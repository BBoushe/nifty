package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.audit.service.AuditActorRole;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionRepository;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationEntity;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationRepository;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import de.fhdortmund.nifty.studio.service.view.ScoringQuestion;
import de.fhdortmund.nifty.studio.service.view.ScoringView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-FE-02, REQ-FE-05 / Decisions 2.19, 2.22:
 * Named formula presets (MVP: {@code WEIGHTED_MEAN}), immutable {@code formula_version} creation
 * with zero-LLM deterministic cohort recomputation, and non-persisting what-if preview.
 */
@Service
@Transactional(readOnly = true)
public class FormulaPresetService {

    public static final String PRESET_WEIGHTED_MEAN = "WEIGHTED_MEAN";
    private static final String CONFIG_NOT_FOUND = "Assessment configuration not found.";

    private static final List<EvaluationDtos.FormulaPresetInfo> PRESETS = List.of(
            new EvaluationDtos.FormulaPresetInfo(
                    PRESET_WEIGHTED_MEAN,
                    "Question-Weighted Mean (Formula B)",
                    "Question-weighted mean with 1.0 follow-up inheritance, 0.50 per-entry weight cap, explicit N/A, and unweighted fit.",
                    SkillFormulaCalculator.DEFAULT_MAX_ENTRY_SHARE));

    public record FormulaPresetRequest(String preset, String expression) {
    }

    private final StudioQueryService studioQueryService;
    private final AuthContextService authContextService;
    private final AuditService auditService;
    private final FormulaVersionRepository formulaVersionRepository;
    private final CandidateEvaluationSummaryRepository summaryRepository;
    private final ResponseEvaluationRepository responseEvaluationRepository;
    private final FormulaRecalculationHelper recalcHelper;

    FormulaPresetService(
            StudioQueryService studioQueryService,
            AuthContextService authContextService,
            AuditService auditService,
            FormulaVersionRepository formulaVersionRepository,
            CandidateEvaluationSummaryRepository summaryRepository,
            ResponseEvaluationRepository responseEvaluationRepository,
            FormulaRecalculationHelper recalcHelper) {
        this.studioQueryService = studioQueryService;
        this.authContextService = authContextService;
        this.auditService = auditService;
        this.formulaVersionRepository = formulaVersionRepository;
        this.summaryRepository = summaryRepository;
        this.responseEvaluationRepository = responseEvaluationRepository;
        this.recalcHelper = recalcHelper;
    }

    public List<EvaluationDtos.FormulaPresetInfo> listPresets() {
        return PRESETS;
    }

    @Transactional
    public EvaluationDtos.FormulaVersionResponse applyPreset(UUID configId, FormulaPresetRequest request) {
        String preset = validatePresetRequest(request);
        UUID professorId = requireOwnedConfig(configId);

        EvaluatorView evaluatorView = studioQueryService.evaluatorView(configId);
        ScoringView scoringView = studioQueryService.scoringView(configId);

        int nextVersion = formulaVersionRepository.findTopByConfigIdOrderByVersionNumberDesc(configId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        Map<String, BigDecimal> questionWeights = buildQuestionWeightsMap(scoringView.questions());
        FormulaVersionEntity savedVersion = formulaVersionRepository.save(new FormulaVersionEntity(
                configId,
                nextVersion,
                preset,
                SkillFormulaCalculator.DEFAULT_MAX_ENTRY_SHARE,
                questionWeights,
                professorId));

        List<CandidateEvaluationSummaryEntity> summaries = summaryRepository.findLatestCompletedPerInvite(configId);
        for (int i = 0; i < summaries.size(); i++) {
            CandidateEvaluationSummaryEntity summary = summaries.get(i);
            List<ResponseEvaluationEntity> responses = responseEvaluationRepository
                    .findBySummaryIdOrderByIdAsc(summary.getId());
            SkillFormulaCalculator.FormulaResult recomputed = recalcHelper.computeForResponses(
                    evaluatorView, scoringView, responses, savedVersion.getMaxEntrySkillWeightShare());

            summary.updateScores(
                    savedVersion,
                    recomputed.fitScore(),
                    recomputed.coverage(),
                    summary.getFlaggedCount(),
                    summary.getReleaseStatus(),
                    summary.getReleasedAt());
            summaryRepository.save(summary);
            String preservedReleaseStatus = FormulaRecalculationHelper.isReleased(summary.getReleaseStatus())
                    ? summary.getReleaseStatus()
                    : null;
            recalcHelper.replaceSkillScores(summary, savedVersion, recomputed, preservedReleaseStatus);
        }

        auditService.record(
                professorId.toString(),
                AuditActorRole.PROFESSOR,
                "FORMULA_VERSION",
                savedVersion.getId() != null ? savedVersion.getId() : configId,
                "FORMULA_VERSION_CREATED",
                null,
                preset + ":v" + nextVersion,
                null);

        return new EvaluationDtos.FormulaVersionResponse(
                savedVersion.getId(),
                configId,
                savedVersion.getVersionNumber(),
                savedVersion.getFormulaPreset(),
                savedVersion.getMaxEntrySkillWeightShare(),
                savedVersion.getQuestionWeightsJson(),
                savedVersion.getCreatedAt(),
                summaries.size());
    }

    public EvaluationDtos.FormulaPreviewResponse previewPreset(UUID configId, FormulaPresetRequest request) {
        String preset = validatePresetRequest(request);
        requireOwnedConfig(configId);

        EvaluatorView evaluatorView = studioQueryService.evaluatorView(configId);
        ScoringView scoringView = studioQueryService.scoringView(configId);
        List<CandidateEvaluationSummaryEntity> summaries = summaryRepository.findLatestCompletedPerInvite(configId);

        List<EvaluationDtos.FormulaPreviewCandidate> candidates = new ArrayList<>(summaries.size());
        for (int i = 0; i < summaries.size(); i++) {
            CandidateEvaluationSummaryEntity summary = summaries.get(i);
            List<ResponseEvaluationEntity> responses = responseEvaluationRepository
                    .findBySummaryIdOrderByIdAsc(summary.getId());
            SkillFormulaCalculator.FormulaResult result = recalcHelper.computeForResponses(
                    evaluatorView, scoringView, responses, SkillFormulaCalculator.DEFAULT_MAX_ENTRY_SHARE);
            candidates.add(new EvaluationDtos.FormulaPreviewCandidate(
                    summary.getInviteId(),
                    summary.getAttemptId(),
                    result.fitScore(),
                    result.coverage(),
                    FormulaRecalculationHelper.toMatrixCellsFromCalculated(
                            evaluatorView.skills(), result.skillScores())));
        }

        return new EvaluationDtos.FormulaPreviewResponse(
                configId,
                preset,
                SkillFormulaCalculator.DEFAULT_MAX_ENTRY_SHARE,
                List.copyOf(candidates));
    }

    private UUID requireOwnedConfig(UUID configId) {
        UUID professorId = authContextService.currentProfessorId();
        if (configId == null || !studioQueryService.ownedBy(configId, professorId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, CONFIG_NOT_FOUND);
        }
        return professorId;
    }

    private static String validatePresetRequest(FormulaPresetRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (request == null) {
            errors.add(new FieldErrorDetail("preset", "must be one of: WEIGHTED_MEAN", "REQUIRED"));
            throw new FieldValidationException(errors);
        }
        // Decision 2.19 / REQ-FE-05: reject any free-form formula expression field in MVP.
        if (request.expression() != null) {
            errors.add(new FieldErrorDetail(
                    "expression", "free-form formula expressions are not allowed in MVP", "FREE_FORM_FORBIDDEN"));
        }
        String preset = request.preset() == null ? "" : request.preset().trim();
        if (!PRESET_WEIGHTED_MEAN.equals(preset)) {
            errors.add(new FieldErrorDetail("preset", "must be one of: WEIGHTED_MEAN", "INVALID_PRESET"));
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return preset;
    }

    private static Map<String, BigDecimal> buildQuestionWeightsMap(List<ScoringQuestion> questions) {
        Map<String, BigDecimal> map = new LinkedHashMap<>(questions.size());
        for (int i = 0; i < questions.size(); i++) {
            ScoringQuestion q = questions.get(i);
            map.put(q.coreQuestionId().toString(), q.weight());
        }
        return map;
    }
}
