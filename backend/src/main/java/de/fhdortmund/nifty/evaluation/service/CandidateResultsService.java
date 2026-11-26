package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.assessment.service.ImmutableTranscriptView;
import de.fhdortmund.nifty.assessment.service.StudentSoftSkillProfile;
import de.fhdortmund.nifty.assessment.service.TranscriptEntryView;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.auth.service.CandidateSessionPrincipal;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionEntity;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationEntity;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationRepository;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreEntity;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-CR-01..06 / Decisions 2.10, 2.15, 2.16, 4.3:
 * Strictly isolated candidate results service. Takes invite identity only from the signed-in
 * candidate session, withholds all scores/weights/formulas/traits prior to release, and reveals
 * full evidence transparency, conversation log, deterministic advice, and own BFI-10 traits after release.
 */
@Service
@Transactional(readOnly = true)
public class CandidateResultsService {

    private static final List<String> TRAIT_ORDER = List.of(
            "conscientiousness", "agreeableness", "neuroticism", "extraversion", "openness");

    private static final Map<String, String> NEUTRAL_TRAIT_DESCRIPTIONS = Map.of(
            "conscientiousness", "Reflects how you organize tasks, plan ahead, and follow structured workflows.",
            "agreeableness", "Reflects how you collaborate, build consensus, and approach group interactions.",
            "neuroticism", "Reflects how strongly you notice pressure, uncertainty, and emotional responses.",
            "extraversion", "Reflects how much energy you draw from active discussion and group communication.",
            "openness", "Reflects your curiosity toward new ideas, methods, and exploratory problem solving.");

    private final AuthContextService authContextService;
    private final StudioQueryService studioQueryService;
    private final AssessmentQueryService assessmentQueryService;
    private final CandidateEvaluationSummaryRepository summaryRepository;
    private final ResponseEvaluationRepository responseEvaluationRepository;
    private final SkillScoreRepository skillScoreRepository;
    private final DeterministicAdviceBuilder adviceBuilder;

    CandidateResultsService(
            AuthContextService authContextService,
            StudioQueryService studioQueryService,
            AssessmentQueryService assessmentQueryService,
            CandidateEvaluationSummaryRepository summaryRepository,
            ResponseEvaluationRepository responseEvaluationRepository,
            SkillScoreRepository skillScoreRepository,
            DeterministicAdviceBuilder adviceBuilder) {
        this.authContextService = authContextService;
        this.studioQueryService = studioQueryService;
        this.assessmentQueryService = assessmentQueryService;
        this.summaryRepository = summaryRepository;
        this.responseEvaluationRepository = responseEvaluationRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.adviceBuilder = adviceBuilder;
    }

    public EvaluationDtos.CandidateResultsResponse getResultsForCurrentCandidate(UUID configId) {
        CandidateSessionPrincipal principal = authContextService.currentCandidate();
        // REQ-CR-03: candidate can ONLY access the assessment of their signed-in invite.
        if (configId == null || !principal.configId().equals(configId)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "You may only view results for your own assessment.");
        }

        Optional<CandidateEvaluationSummaryEntity> latestOpt = summaryRepository
                .findTopByConfigIdAndInviteIdOrderByAttemptNoDescCreatedAtDesc(configId, principal.inviteId());

        // REQ-CR-02: withhold all scores, weights, formulas, conversation log, and traits before release.
        if (latestOpt.isEmpty()
                || !latestOpt.get().isCompleted()
                || !FormulaRecalculationHelper.isReleased(latestOpt.get().getReleaseStatus())) {
            UUID attemptId = latestOpt.map(CandidateEvaluationSummaryEntity::getAttemptId).orElse(null);
            return new EvaluationDtos.CandidateResultsResponse(
                    configId, attemptId, ReleaseStatus.PENDING, false, null,
                    null, null, null, null, null, null, null, null, null, null);
        }

        CandidateEvaluationSummaryEntity summary = latestOpt.get();
        FormulaVersionEntity formula = summary.getFormulaVersion();
        EvaluatorView evaluatorView = studioQueryService.evaluatorView(configId);
        Map<UUID, String> skillNames = indexSkillNames(evaluatorView.skills());

        List<SkillScoreEntity> persistedScores = skillScoreRepository.findBySummaryId(summary.getId());
        List<EvaluationDtos.MatrixCell> skillCells = FormulaRecalculationHelper
                .toMatrixCells(evaluatorView.skills(), persistedScores);

        List<ResponseEvaluationEntity> responses = responseEvaluationRepository
                .findBySummaryIdOrderByIdAsc(summary.getId());
        List<EvaluationDtos.CandidateEvidenceItem> evidence = buildVerifiedEvidence(responses, skillNames);

        ImmutableTranscriptView transcript = assessmentQueryService.getTranscriptForAttempt(summary.getAttemptId());
        List<EvaluationDtos.CandidateConversationTurn> conversationLog = buildConversationLog(transcript);

        EvaluationDtos.CandidateAdvice advice = adviceBuilder.build(skillCells);

        Optional<StudentSoftSkillProfile> softProfile = assessmentQueryService
                .getSoftSkillProfileForAttempt(summary.getAttemptId());
        List<EvaluationDtos.CandidateTraitScore> traits = buildNeutralTraits(softProfile.orElse(null));

        return new EvaluationDtos.CandidateResultsResponse(
                configId,
                summary.getAttemptId(),
                FormulaRecalculationHelper.parseReleaseStatus(summary.getReleaseStatus()),
                true,
                summary.getReleasedAt(),
                formula.getFormulaPreset(),
                formula.getVersionNumber(),
                summary.getFitScore(),
                summary.getCoverage(),
                skillCells,
                evidence,
                formula.getQuestionWeightsJson(),
                conversationLog,
                advice,
                traits);
    }

    private static List<EvaluationDtos.CandidateEvidenceItem> buildVerifiedEvidence(
            List<ResponseEvaluationEntity> responses,
            Map<UUID, String> skillNames) {
        List<EvaluationDtos.CandidateEvidenceItem> items = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            ResponseEvaluationEntity r = responses.get(i);
            boolean overridden = r.getOverriddenBy() != null
                    || ReleaseStatus.OVERRIDDEN.literal().equals(r.getReviewStatus());
            if ((r.isQuoteVerified() || overridden) && r.getEffectiveLevel() != null) {
                items.add(new EvaluationDtos.CandidateEvidenceItem(
                        r.getEntryId(),
                        r.getCoreQuestionId(),
                        r.getSkillId(),
                        skillNames.getOrDefault(r.getSkillId(), r.getSkillId().toString()),
                        r.getVerifiedQuote(),
                        r.getEffectiveLevel(),
                        r.getCalculatedConfidence(),
                        r.getQuestionWeight()));
            }
        }
        return List.copyOf(items);
    }

    private static List<EvaluationDtos.CandidateConversationTurn> buildConversationLog(
            ImmutableTranscriptView transcript) {
        if (transcript == null || transcript.entries() == null) {
            return List.of();
        }
        List<TranscriptEntryView> entries = transcript.entries();
        List<EvaluationDtos.CandidateConversationTurn> turns = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            TranscriptEntryView e = entries.get(i);
            turns.add(new EvaluationDtos.CandidateConversationTurn(
                    e.entryId(),
                    e.seq(),
                    e.origin() == null ? "core" : e.origin().literal(),
                    e.questionText(),
                    e.answerText(),
                    e.askedAt(),
                    e.submittedAt(),
                    e.timedOut()));
        }
        return List.copyOf(turns);
    }

    private static List<EvaluationDtos.CandidateTraitScore> buildNeutralTraits(StudentSoftSkillProfile profile) {
        if (profile == null || profile.softSkillScores() == null || profile.softSkillScores().isEmpty()) {
            return null;
        }
        Map<String, Double> scores = profile.softSkillScores();
        List<EvaluationDtos.CandidateTraitScore> traits = new ArrayList<>(TRAIT_ORDER.size());
        for (int i = 0; i < TRAIT_ORDER.size(); i++) {
            String key = TRAIT_ORDER.get(i);
            Double val = scores.get(key);
            if (val != null) {
                traits.add(new EvaluationDtos.CandidateTraitScore(
                        key,
                        val,
                        NEUTRAL_TRAIT_DESCRIPTIONS.getOrDefault(key, "")));
            }
        }
        return traits.isEmpty() ? null : List.copyOf(traits);
    }

    private static Map<UUID, String> indexSkillNames(List<EvaluatorSkill> skills) {
        Map<UUID, String> map = new HashMap<>(skills.size());
        for (int i = 0; i < skills.size(); i++) {
            EvaluatorSkill s = skills.get(i);
            map.put(s.skillId(), s.name());
        }
        return map;
    }
}
