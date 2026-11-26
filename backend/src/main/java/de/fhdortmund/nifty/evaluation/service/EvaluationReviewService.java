package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.assessment.service.ImmutableTranscriptView;
import de.fhdortmund.nifty.assessment.service.TranscriptEntryView;
import de.fhdortmund.nifty.audit.service.AuditActorRole;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionRepository;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationEntity;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationRepository;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreEntity;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import de.fhdortmund.nifty.studio.service.view.ScoringQuestion;
import de.fhdortmund.nifty.studio.service.view.ScoringView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-SM-01..03, REQ-SM-06..08, REQ-FE-02 / Decisions 1.18, 2.10, 2.13, 2.15, 2.24:
 * Professor-scoped Skills Matrix, Evidence Drawer drilldown, bulk-approve (skipping flagged),
 * audited score overrides with deterministic Formula B recalculation, explicit attempt release,
 * and assessment-transcript/v1 export.
 */
@Service
@Transactional(readOnly = true)
public class EvaluationReviewService {

    private static final String CONFIG_NOT_FOUND = "Assessment configuration not found.";
    private static final String ATTEMPT_NOT_FOUND = "Evaluation attempt not found.";
    private static final String RESPONSE_NOT_FOUND = "Response evaluation not found.";

    private final StudioQueryService studioQueryService;
    private final AuthContextService authContextService;
    private final AuditService auditService;
    private final AssessmentQueryService assessmentQueryService;
    private final FormulaVersionRepository formulaVersionRepository;
    private final CandidateEvaluationSummaryRepository summaryRepository;
    private final ResponseEvaluationRepository responseEvaluationRepository;
    private final SkillScoreRepository skillScoreRepository;
    private final FormulaRecalculationHelper recalcHelper;

    EvaluationReviewService(
            StudioQueryService studioQueryService,
            AuthContextService authContextService,
            AuditService auditService,
            AssessmentQueryService assessmentQueryService,
            FormulaVersionRepository formulaVersionRepository,
            CandidateEvaluationSummaryRepository summaryRepository,
            ResponseEvaluationRepository responseEvaluationRepository,
            SkillScoreRepository skillScoreRepository,
            FormulaRecalculationHelper recalcHelper) {
        this.studioQueryService = studioQueryService;
        this.authContextService = authContextService;
        this.auditService = auditService;
        this.assessmentQueryService = assessmentQueryService;
        this.formulaVersionRepository = formulaVersionRepository;
        this.summaryRepository = summaryRepository;
        this.responseEvaluationRepository = responseEvaluationRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.recalcHelper = recalcHelper;
    }

    public EvaluationDtos.MatrixResponse getMatrix(UUID configId) {
        requireOwnedConfig(configId);
        EvaluatorView evaluatorView = studioQueryService.evaluatorView(configId);
        ScoringView scoringView = studioQueryService.scoringView(configId);
        FormulaVersionEntity latestFormula = formulaVersionRepository
                .findTopByConfigIdOrderByVersionNumberDesc(configId)
                .orElse(null);

        // Decision 2.13 / REQ-SM-07: newest attempt per invite in any status. A queued, running, or failed retake
        // shows as a waiting row and hides the older completed attempt.
        List<CandidateEvaluationSummaryEntity> latestSummaries = summaryRepository.findLatestPerInvite(configId);
        Map<UUID, List<SkillScoreEntity>> scoresBySummary = loadScoresBySummary(latestSummaries);

        List<EvaluationDtos.SkillColumn> columns = new ArrayList<>(evaluatorView.skills().size());
        for (int i = 0; i < evaluatorView.skills().size(); i++) {
            EvaluatorSkill skill = evaluatorView.skills().get(i);
            columns.add(new EvaluationDtos.SkillColumn(skill.skillId(), skill.name()));
        }

        List<EvaluationDtos.MatrixCandidateRow> rows = new ArrayList<>(latestSummaries.size());
        for (int i = 0; i < latestSummaries.size(); i++) {
            CandidateEvaluationSummaryEntity summary = latestSummaries.get(i);
            List<SkillScoreEntity> scores = scoresBySummary.getOrDefault(summary.getId(), List.of());
            rows.add(new EvaluationDtos.MatrixCandidateRow(
                    summary.getInviteId(),
                    summary.getAttemptId(),
                    summary.getAttemptNo(),
                    summary.getEvaluationStatus(),
                    summary.getFitScore(),
                    summary.getCoverage(),
                    FormulaRecalculationHelper.parseReleaseStatus(summary.getReleaseStatus()),
                    summary.getFlaggedCount(),
                    summary.getReleasedAt(),
                    FormulaRecalculationHelper.toMatrixCells(evaluatorView.skills(), scores)));
        }

        return new EvaluationDtos.MatrixResponse(
                configId,
                latestFormula == null ? null : latestFormula.getId(),
                latestFormula == null ? 1 : latestFormula.getVersionNumber(),
                latestFormula == null ? "WEIGHTED_MEAN" : latestFormula.getFormulaPreset(),
                scoringView.minCoverage(),
                List.copyOf(columns),
                List.copyOf(rows));
    }

    public EvaluationDtos.AttemptEvaluationDrilldownResponse getAttemptDrilldown(UUID attemptId) {
        CandidateEvaluationSummaryEntity summary = requireOwnedSummary(attemptId);
        EvaluatorView evaluatorView = studioQueryService.evaluatorView(summary.getConfigId());
        ScoringView scoringView = studioQueryService.scoringView(summary.getConfigId());

        Map<UUID, String> skillNames = indexSkillNames(evaluatorView.skills());
        Map<UUID, BigDecimal> thresholdByQuestion = indexThresholds(scoringView.questions());

        List<ResponseEvaluationEntity> responses = responseEvaluationRepository.findBySummaryIdOrderByIdAsc(summary.getId());
        List<EvaluationDtos.ResponseEvaluationDetail> details = new ArrayList<>(responses.size());
        for (int i = 0; i < responses.size(); i++) {
            ResponseEvaluationEntity r = responses.get(i);
            details.add(new EvaluationDtos.ResponseEvaluationDetail(
                    r.getId(),
                    r.getEntryId(),
                    r.getCoreQuestionId(),
                    r.getSkillId(),
                    skillNames.getOrDefault(r.getSkillId(), r.getSkillId().toString()),
                    r.getQuestionOrigin(),
                    r.getQuestionWeight(),
                    thresholdByQuestion.get(r.getCoreQuestionId()),
                    r.getRun1Level(),
                    r.getRun2Level(),
                    r.getRun1Quote(),
                    r.getRun2Quote(),
                    r.isQuoteVerified(),
                    r.getVerifiedQuote(),
                    r.getAiRationale(),
                    r.getAiLevel(),
                    r.getCalculatedConfidence(),
                    FormulaRecalculationHelper.splitFlagReasons(r.getFlagReasons()),
                    FormulaRecalculationHelper.parseReleaseStatus(r.getReviewStatus()),
                    r.getEffectiveLevel(),
                    r.getOverriddenBy(),
                    r.getOverrideReason(),
                    r.getReviewedAt()));
        }

        List<SkillScoreEntity> scores = skillScoreRepository.findBySummaryId(summary.getId());
        return new EvaluationDtos.AttemptEvaluationDrilldownResponse(
                summary.getAttemptId(),
                summary.getConfigId(),
                summary.getInviteId(),
                summary.getAttemptNo(),
                summary.getFitScore(),
                summary.getCoverage(),
                scoringView.minCoverage(),
                FormulaRecalculationHelper.parseReleaseStatus(summary.getReleaseStatus()),
                summary.getFlaggedCount(),
                summary.getReleasedAt(),
                List.copyOf(details),
                FormulaRecalculationHelper.toMatrixCells(evaluatorView.skills(), scores));
    }

    @Transactional
    public EvaluationDtos.BulkApproveResponse bulkApprove(EvaluationDtos.BulkApproveRequest request) {
        if (request == null || request.configId() == null) {
            throw new FieldValidationException(List.of(
                    new FieldErrorDetail("configId", "must not be null", "REQUIRED")));
        }
        requireOwnedConfig(request.configId());

        List<CandidateEvaluationSummaryEntity> summaries = summaryRepository.findLatestCompletedPerInvite(request.configId());
        if (summaries.isEmpty()) {
            return new EvaluationDtos.BulkApproveResponse(0, 0);
        }
        List<UUID> summaryIds = new ArrayList<>(summaries.size());
        for (int i = 0; i < summaries.size(); i++) {
            summaryIds.add(summaries.get(i).getId());
        }

        List<ResponseEvaluationEntity> responses = responseEvaluationRepository.findBySummaryIdIn(summaryIds);
        Set<UUID> filterIds = (request.responseEvaluationIds() == null || request.responseEvaluationIds().isEmpty())
                ? null
                : new HashSet<>(request.responseEvaluationIds());

        Instant now = Instant.now();
        int approvedCount = 0;
        int skippedFlaggedCount = 0;
        List<ResponseEvaluationEntity> modified = new ArrayList<>();
        Set<UUID> affectedSummaryIds = new HashSet<>();

        for (int i = 0; i < responses.size(); i++) {
            ResponseEvaluationEntity r = responses.get(i);
            if (filterIds != null && !filterIds.contains(r.getId())) {
                continue;
            }
            // REQ-SM-03: flagged scores MUST NEVER be bulk-approved.
            if (ReleaseStatus.FLAGGED.literal().equals(r.getReviewStatus())) {
                skippedFlaggedCount++;
                continue;
            }
            if (ReleaseStatus.PENDING.literal().equals(r.getReviewStatus())) {
                r.approve(now);
                modified.add(r);
                affectedSummaryIds.add(r.getSummary().getId());
                approvedCount++;
            }
        }
        if (!modified.isEmpty()) {
            responseEvaluationRepository.saveAll(modified);
            EvaluatorView evaluatorView = studioQueryService.evaluatorView(request.configId());
            for (int i = 0; i < summaries.size(); i++) {
                CandidateEvaluationSummaryEntity summary = summaries.get(i);
                if (!affectedSummaryIds.contains(summary.getId())) {
                    continue;
                }
                List<ResponseEvaluationEntity> summaryResponses = responseEvaluationRepository
                        .findBySummaryIdOrderByIdAsc(summary.getId());
                FormulaVersionEntity fv = summary.getFormulaVersion();
                SkillFormulaCalculator.FormulaResult recalculated = recalcHelper.computeForResponses(
                        evaluatorView, fv.getQuestionWeightsJson(), summaryResponses, fv.getMaxEntrySkillWeightShare());
                recalcHelper.replaceSkillScores(summary, fv, recalculated, null);
            }
        }
        return new EvaluationDtos.BulkApproveResponse(approvedCount, skippedFlaggedCount);
    }

    /**
     * REQ-SM-06 / AGENTS.md §2 #2: individual professor approval for a single flagged or pending
     * evaluation without fabricating a 0..4 override (keeps effectiveLevel or N/A intact).
     */
    @Transactional
    public EvaluationDtos.ApproveEvaluationResponse approveEvaluation(UUID responseEvalId) {
        UUID professorId = authContextService.currentProfessorId();
        ResponseEvaluationEntity target = responseEvaluationRepository.findWithSummaryById(responseEvalId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, RESPONSE_NOT_FOUND));
        CandidateEvaluationSummaryEntity summary = target.getSummary();
        if (!studioQueryService.ownedBy(summary.getConfigId(), professorId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, RESPONSE_NOT_FOUND);
        }

        // Decision 2.10: an audited professor override must survive; approve would silently replace it.
        if (target.getOverriddenBy() != null) {
            throw new DomainException(HttpStatus.CONFLICT, "This evaluation was overridden and cannot be approved.");
        }

        String oldStatus = target.getReviewStatus();
        Instant now = Instant.now();
        target.approve(now);
        responseEvaluationRepository.save(target);

        auditService.record(
                professorId.toString(),
                AuditActorRole.PROFESSOR,
                "RESPONSE_EVALUATION",
                responseEvalId,
                "SCORE_APPROVED",
                oldStatus,
                ReleaseStatus.APPROVED.literal(),
                null);

        EvaluatorView evaluatorView = studioQueryService.evaluatorView(summary.getConfigId());
        List<ResponseEvaluationEntity> allResponses = responseEvaluationRepository
                .findBySummaryIdOrderByIdAsc(summary.getId());
        int remainingFlagged = countFlagged(allResponses);

        String nextSummaryStatus = FormulaRecalculationHelper.isReleased(summary.getReleaseStatus())
                ? summary.getReleaseStatus()
                : (remainingFlagged > 0 ? ReleaseStatus.FLAGGED.literal() : ReleaseStatus.PENDING.literal());
        FormulaVersionEntity fv = summary.getFormulaVersion();
        SkillFormulaCalculator.FormulaResult recalculated = recalcHelper.computeForResponses(
                evaluatorView, fv.getQuestionWeightsJson(), allResponses, fv.getMaxEntrySkillWeightShare());

        summary.updateScores(
                fv,
                recalculated.fitScore(),
                recalculated.coverage(),
                remainingFlagged,
                nextSummaryStatus,
                summary.getReleasedAt());
        summaryRepository.save(summary);
        String preserveStatus = FormulaRecalculationHelper.isReleased(summary.getReleaseStatus())
                ? summary.getReleaseStatus()
                : null;
        recalcHelper.replaceSkillScores(summary, fv, recalculated, preserveStatus);

        return new EvaluationDtos.ApproveEvaluationResponse(
                responseEvalId,
                summary.getAttemptId(),
                target.getEffectiveLevel(),
                ReleaseStatus.APPROVED,
                now,
                remainingFlagged);
    }

    @Transactional
    public EvaluationDtos.OverrideEvaluationResponse overrideEvaluation(
            UUID responseEvalId,
            EvaluationDtos.OverrideEvaluationRequest request) {
        validateOverrideRequest(request);

        UUID professorId = authContextService.currentProfessorId();
        ResponseEvaluationEntity target = responseEvaluationRepository.findWithSummaryById(responseEvalId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, RESPONSE_NOT_FOUND));
        CandidateEvaluationSummaryEntity summary = target.getSummary();
        if (!studioQueryService.ownedBy(summary.getConfigId(), professorId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, RESPONSE_NOT_FOUND);
        }

        String oldValue = target.getEffectiveLevel() == null
                ? "N/A"
                : String.valueOf(target.getEffectiveLevel());
        String trimmedReason = request.reason().trim();
        Instant now = Instant.now();

        // REQ-SM-02: keep aiLevel intact; update effectiveLevel and review status.
        target.overrideLevel(request.newLevel(), professorId, trimmedReason, now);
        responseEvaluationRepository.save(target);

        auditService.record(
                professorId.toString(),
                AuditActorRole.PROFESSOR,
                "RESPONSE_EVALUATION",
                responseEvalId,
                "SCORE_OVERRIDDEN",
                oldValue,
                String.valueOf(request.newLevel()),
                trimmedReason);

        // REQ-FE-02: recompute Formula B deterministically in Java with ZERO LLM calls.
        EvaluatorView evaluatorView = studioQueryService.evaluatorView(summary.getConfigId());
        List<ResponseEvaluationEntity> allResponses = responseEvaluationRepository
                .findBySummaryIdOrderByIdAsc(summary.getId());
        FormulaVersionEntity formulaVersion = summary.getFormulaVersion();

        SkillFormulaCalculator.FormulaResult recalculated = recalcHelper.computeForResponses(
                evaluatorView,
                formulaVersion.getQuestionWeightsJson(),
                allResponses,
                formulaVersion.getMaxEntrySkillWeightShare());

        int remainingFlagged = countFlagged(allResponses);
        boolean wasReleased = FormulaRecalculationHelper.isReleased(summary.getReleaseStatus());
        String nextSummaryStatus = wasReleased
                ? ReleaseStatus.OVERRIDDEN.literal()
                : (remainingFlagged > 0 ? ReleaseStatus.FLAGGED.literal() : ReleaseStatus.PENDING.literal());

        summary.updateScores(
                formulaVersion,
                recalculated.fitScore(),
                recalculated.coverage(),
                remainingFlagged,
                nextSummaryStatus,
                summary.getReleasedAt());
        summaryRepository.save(summary);
        recalcHelper.replaceSkillScores(
                summary,
                formulaVersion,
                recalculated,
                wasReleased ? ReleaseStatus.OVERRIDDEN.literal() : null);

        return new EvaluationDtos.OverrideEvaluationResponse(
                responseEvalId,
                summary.getAttemptId(),
                target.getAiLevel(),
                target.getEffectiveLevel(),
                ReleaseStatus.OVERRIDDEN,
                trimmedReason,
                now,
                recalculated.fitScore(),
                recalculated.coverage(),
                remainingFlagged);
    }

    @Transactional
    public EvaluationDtos.ReleaseAttemptResponse releaseAttempt(UUID attemptId) {
        UUID professorId = authContextService.currentProfessorId();
        CandidateEvaluationSummaryEntity summary = requireOwnedSummary(attemptId);
        // Decision 2.28: a queued, running, or failed attempt has no evidence yet and must never be released.
        if (!summary.isCompleted()) {
            throw new DomainException(HttpStatus.CONFLICT, "This attempt has not finished evaluation yet.");
        }
        List<ResponseEvaluationEntity> responses = responseEvaluationRepository
                .findBySummaryIdOrderByIdAsc(summary.getId());

        int remainingFlagged = countFlagged(responses);
        if (remainingFlagged > 0) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "Resolve all flagged evaluations before releasing this attempt.");
        }

        Instant now = Instant.now();
        boolean anyOverridden = false;
        List<ResponseEvaluationEntity> toApprove = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            ResponseEvaluationEntity r = responses.get(i);
            if (ReleaseStatus.OVERRIDDEN.literal().equals(r.getReviewStatus())) {
                anyOverridden = true;
            } else if (ReleaseStatus.PENDING.literal().equals(r.getReviewStatus())) {
                r.approve(now);
                toApprove.add(r);
            }
        }
        if (!toApprove.isEmpty()) {
            responseEvaluationRepository.saveAll(toApprove);
        }

        ReleaseStatus finalStatus = anyOverridden ? ReleaseStatus.OVERRIDDEN : ReleaseStatus.APPROVED;
        String oldStatus = summary.getReleaseStatus();
        summary.markReleased(finalStatus.literal(), now);
        summaryRepository.save(summary);

        EvaluatorView evaluatorView = studioQueryService.evaluatorView(summary.getConfigId());
        FormulaVersionEntity formulaVersion = summary.getFormulaVersion();
        SkillFormulaCalculator.FormulaResult result = recalcHelper.computeForResponses(
                evaluatorView,
                formulaVersion.getQuestionWeightsJson(),
                responses,
                formulaVersion.getMaxEntrySkillWeightShare());
        recalcHelper.replaceSkillScores(summary, summary.getFormulaVersion(), result, finalStatus.literal());

        auditService.record(
                professorId.toString(),
                AuditActorRole.PROFESSOR,
                "ATTEMPT",
                attemptId,
                "ATTEMPT_RELEASED",
                oldStatus,
                finalStatus.literal(),
                null);

        return new EvaluationDtos.ReleaseAttemptResponse(
                attemptId,
                finalStatus,
                now,
                summary.getFitScore(),
                summary.getCoverage());
    }

    public EvaluationDtos.TranscriptExportResponse exportTranscript(UUID attemptId) {
        CandidateEvaluationSummaryEntity summary = requireOwnedSummary(attemptId);
        ImmutableTranscriptView transcript = assessmentQueryService.getTranscriptForAttempt(attemptId);
        EvaluatorView evaluatorView = studioQueryService.evaluatorView(summary.getConfigId());
        ScoringView scoringView = studioQueryService.scoringView(summary.getConfigId());

        Map<UUID, String> skillNames = indexSkillNames(evaluatorView.skills());
        Map<UUID, BigDecimal> weightByQuestion = indexWeights(scoringView.questions());

        List<TranscriptEntryView> rawEntries = transcript.entries() == null ? List.of() : transcript.entries();
        List<EvaluationDtos.TranscriptExportEntry> entries = new ArrayList<>(rawEntries.size());
        for (int i = 0; i < rawEntries.size(); i++) {
            TranscriptEntryView e = rawEntries.get(i);
            BigDecimal weight = weightByQuestion.getOrDefault(e.coreQuestionId(), BigDecimal.ONE);
            entries.add(new EvaluationDtos.TranscriptExportEntry(
                    e.entryId(),
                    e.seq(),
                    new EvaluationDtos.TranscriptQuestionBlock(
                            e.questionText(),
                            e.origin() == null ? "core" : e.origin().literal(),
                            e.coreQuestionId(),
                            weight,
                            e.targetSkillIds() == null ? List.of() : e.targetSkillIds(),
                            e.askedAt(),
                            e.expiresAt()),
                    new EvaluationDtos.TranscriptAnswerBlock(
                            e.answerText(),
                            e.submittedAt(),
                            e.durationSec(),
                            e.timedOut(),
                            e.pasteSuspected(),
                            e.injectionSuspected()),
                    new EvaluationDtos.TranscriptEntryMetadata(List.of(), List.of())));
        }

        List<ResponseEvaluationEntity> responses = responseEvaluationRepository
                .findBySummaryIdOrderByIdAsc(summary.getId());
        List<EvaluationDtos.TranscriptExportEvidence> evidence = new ArrayList<>(responses.size());
        for (int i = 0; i < responses.size(); i++) {
            ResponseEvaluationEntity r = responses.get(i);
            List<Integer> judgeRuns = new ArrayList<>(2);
            if (r.getRun1Level() != null) {
                judgeRuns.add(r.getRun1Level());
            }
            if (r.getRun2Level() != null) {
                judgeRuns.add(r.getRun2Level());
            }
            evidence.add(new EvaluationDtos.TranscriptExportEvidence(
                    r.getId(),
                    r.getEntryId(),
                    r.getSkillId(),
                    skillNames.getOrDefault(r.getSkillId(), r.getSkillId().toString()),
                    // REQ-EP-01: never emit an unverified quote into exported transcript evidence.
                    r.isQuoteVerified() ? r.getVerifiedQuote() : null,
                    r.isQuoteVerified(),
                    r.getEffectiveLevel(),
                    List.copyOf(judgeRuns),
                    r.getAiRationale()));
        }

        return new EvaluationDtos.TranscriptExportResponse(
                "assessment-transcript/v1",
                transcript.attemptId(),
                transcript.inviteId(),
                transcript.configId(),
                transcript.configVersion(),
                transcript.attemptNo(),
                transcript.startedAt(),
                List.copyOf(entries),
                List.copyOf(evidence));
    }

    UUID requireOwnedConfig(UUID configId) {
        UUID professorId = authContextService.currentProfessorId();
        if (configId == null || !studioQueryService.ownedBy(configId, professorId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, CONFIG_NOT_FOUND);
        }
        return professorId;
    }

    private CandidateEvaluationSummaryEntity requireOwnedSummary(UUID attemptId) {
        UUID professorId = authContextService.currentProfessorId();
        CandidateEvaluationSummaryEntity summary = summaryRepository.findByAttemptId(attemptId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, ATTEMPT_NOT_FOUND));
        if (!studioQueryService.ownedBy(summary.getConfigId(), professorId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, ATTEMPT_NOT_FOUND);
        }
        return summary;
    }

    private Map<UUID, List<SkillScoreEntity>> loadScoresBySummary(List<CandidateEvaluationSummaryEntity> summaries) {
        if (summaries.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = new ArrayList<>(summaries.size());
        for (int i = 0; i < summaries.size(); i++) {
            ids.add(summaries.get(i).getId());
        }
        List<SkillScoreEntity> all = skillScoreRepository.findBySummaryIdIn(ids);
        Map<UUID, List<SkillScoreEntity>> map = new HashMap<>(summaries.size());
        for (int i = 0; i < all.size(); i++) {
            SkillScoreEntity sc = all.get(i);
            map.computeIfAbsent(sc.getSummary().getId(), ignored -> new ArrayList<>()).add(sc);
        }
        return map;
    }

    private static void validateOverrideRequest(EvaluationDtos.OverrideEvaluationRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (request == null) {
            errors.add(new FieldErrorDetail("newLevel", "must be between 0 and 4", "REQUIRED"));
            errors.add(new FieldErrorDetail("reason", "must not be blank", "REQUIRED"));
            throw new FieldValidationException(errors);
        }
        if (request.newLevel() == null || request.newLevel() < 0 || request.newLevel() > 4) {
            errors.add(new FieldErrorDetail("newLevel", "must be between 0 and 4", "OUT_OF_RANGE"));
        }
        if (request.reason() == null || request.reason().isBlank()) {
            errors.add(new FieldErrorDetail("reason", "must not be blank", "REQUIRED"));
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private static int countFlagged(List<ResponseEvaluationEntity> responses) {
        int count = 0;
        for (int i = 0; i < responses.size(); i++) {
            if (ReleaseStatus.FLAGGED.literal().equals(responses.get(i).getReviewStatus())) {
                count++;
            }
        }
        return count;
    }

    private static Map<UUID, String> indexSkillNames(List<EvaluatorSkill> skills) {
        Map<UUID, String> map = new HashMap<>(skills.size());
        for (int i = 0; i < skills.size(); i++) {
            EvaluatorSkill s = skills.get(i);
            map.put(s.skillId(), s.name());
        }
        return map;
    }

    private static Map<UUID, BigDecimal> indexThresholds(List<ScoringQuestion> questions) {
        Map<UUID, BigDecimal> map = new HashMap<>(questions.size());
        for (int i = 0; i < questions.size(); i++) {
            ScoringQuestion q = questions.get(i);
            map.put(q.coreQuestionId(), q.confidenceThreshold());
        }
        return map;
    }

    private static Map<UUID, BigDecimal> indexWeights(List<ScoringQuestion> questions) {
        Map<UUID, BigDecimal> map = new HashMap<>(questions.size());
        for (int i = 0; i < questions.size(); i++) {
            ScoringQuestion q = questions.get(i);
            map.put(q.coreQuestionId(), q.weight());
        }
        return map;
    }
}
