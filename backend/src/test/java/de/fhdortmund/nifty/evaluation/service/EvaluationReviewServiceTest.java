package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.audit.service.AuditActorRole;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionRepository;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationEntity;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationRepository;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorQuestion;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import de.fhdortmund.nifty.studio.service.view.ScoringQuestion;
import de.fhdortmund.nifty.studio.service.view.ScoringView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * US-EV-04 / REQ-SM-02, REQ-SM-03, REQ-SM-06, REQ-FE-02:
 * bulk-approve skips flagged items, override requires non-blank reason + calls AuditService
 * + preserves aiLevel + recalculates Formula B without LLM, explicit release blocks on flagged items,
 * and unowned config returns 404.
 */
class EvaluationReviewServiceTest {

    private static final List<String> RUBRIC = List.of("None", "Basic", "Developing", "Proficient", "Expert");

    private StudioQueryService studioQueryService;
    private AuthContextService authContextService;
    private AuditService auditService;
    private AssessmentQueryService assessmentQueryService;
    private FormulaVersionRepository formulaVersionRepository;
    private CandidateEvaluationSummaryRepository summaryRepository;
    private ResponseEvaluationRepository responseEvaluationRepository;
    private SkillScoreRepository skillScoreRepository;
    private EvaluationReviewService service;

    @BeforeEach
    void setUp() {
        studioQueryService = mock(StudioQueryService.class);
        authContextService = mock(AuthContextService.class);
        auditService = mock(AuditService.class);
        assessmentQueryService = mock(AssessmentQueryService.class);
        formulaVersionRepository = mock(FormulaVersionRepository.class);
        summaryRepository = mock(CandidateEvaluationSummaryRepository.class);
        responseEvaluationRepository = mock(ResponseEvaluationRepository.class);
        skillScoreRepository = mock(SkillScoreRepository.class);

        FormulaRecalculationHelper recalc = new FormulaRecalculationHelper(
                new SkillFormulaCalculator(), skillScoreRepository);

        service = new EvaluationReviewService(
                studioQueryService,
                authContextService,
                auditService,
                assessmentQueryService,
                formulaVersionRepository,
                summaryRepository,
                responseEvaluationRepository,
                skillScoreRepository,
                recalc);
    }

    @Test
    void bulkApproveSkipsFlaggedItemsAndApprovesPendingOnly() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();

        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);

        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k1");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                new BigDecimal("2.50"), BigDecimal.ONE, 1, "flagged", null);
        ResponseEvaluationEntity pending = new ResponseEvaluationEntity(
                run, summary, UUID.randomUUID(), q1, skillId, "core", new BigDecimal("1.00"),
                3, 3, "good quote text", "good quote text", true, "good quote text", "clear",
                3, new BigDecimal("0.9200"), null, "pending", 3);
        ResponseEvaluationEntity flagged = new ResponseEvaluationEntity(
                run, summary, UUID.randomUUID(), q1, skillId, "core", new BigDecimal("1.00"),
                1, 3, "some quote text", "some quote text", true, "some quote text", "disagreement",
                1, new BigDecimal("0.5000"), "JUDGE_DISAGREEMENT", "flagged", 1);

        when(summaryRepository.findLatestCompletedPerInvite(configId)).thenReturn(List.of(summary));
        when(responseEvaluationRepository.findBySummaryIdIn(any())).thenReturn(List.of(pending, flagged));

        EvaluationDtos.BulkApproveResponse res = service.bulkApprove(
                new EvaluationDtos.BulkApproveRequest(configId, null));

        assertThat(res.approvedCount()).isEqualTo(1);
        assertThat(res.skippedFlaggedCount()).isEqualTo(1);
        assertThat(pending.getReviewStatus()).isEqualTo("approved");
        assertThat(flagged.getReviewStatus()).isEqualTo("flagged");
    }

    @Test
    void overridePreservesAiLevelCallsAuditServiceAndRecalculatesFormulaBWithoutLlm() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();

        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);

        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"),
                Map.of(q1.toString(), new BigDecimal("1.00")), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k2");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                new BigDecimal("2.00"), BigDecimal.ONE, 1, "flagged", null);
        ResponseEvaluationEntity response = new ResponseEvaluationEntity(
                run, summary, UUID.randomUUID(), q1, skillId, "core", new BigDecimal("1.00"),
                2, 3, "exact verified quote", "exact verified quote", true, "exact verified quote", "borderline",
                2, new BigDecimal("0.5500"), "JUDGE_DISAGREEMENT", "flagged", 2);

        when(responseEvaluationRepository.findWithSummaryById(responseId)).thenReturn(Optional.of(response));
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(any())).thenReturn(List.of(response));
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "Spring Boot", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain transactions.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.7000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("1.00"), new BigDecimal("0.6000"), List.of(skillId)))));

        EvaluationDtos.OverrideEvaluationResponse result = service.overrideEvaluation(
                responseId, new EvaluationDtos.OverrideEvaluationRequest(3, "Follow-up showed practical mastery"));

        assertThat(response.getAiLevel()).isEqualTo(2);
        assertThat(response.getEffectiveLevel()).isEqualTo(3);
        assertThat(response.getReviewStatus()).isEqualTo("overridden");
        assertThat(summary.getFitScore()).isEqualByComparingTo("3.00");
        assertThat(summary.getFlaggedCount()).isZero();
        assertThat(result.effectiveLevel()).isEqualTo(3);
        assertThat(result.aiLevel()).isEqualTo(2);
        assertThat(result.updatedFitScore()).isEqualByComparingTo("3.00");

        verify(auditService).record(
                eq(professorId.toString()),
                eq(AuditActorRole.PROFESSOR),
                eq("RESPONSE_EVALUATION"),
                eq(responseId),
                eq("SCORE_OVERRIDDEN"),
                eq("2"),
                eq("3"),
                eq("Follow-up showed practical mastery"));
    }

    @Test
    void overrideRejectsBlankReasonAndReleaseBlocksWhileFlaggedRemain() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();

        assertThatThrownBy(() -> service.overrideEvaluation(
                UUID.randomUUID(), new EvaluationDtos.OverrideEvaluationRequest(3, "   ")))
                .isInstanceOf(FieldValidationException.class);

        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);

        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k3");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                new BigDecimal("2.00"), BigDecimal.ONE, 1, "flagged", null);
        ResponseEvaluationEntity stillFlagged = new ResponseEvaluationEntity(
                run, summary, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "core",
                new BigDecimal("1.00"), 1, 3, "q", "q", true, "q", "r",
                1, new BigDecimal("0.4000"), "JUDGE_DISAGREEMENT", "flagged", 1);

        when(summaryRepository.findByAttemptId(attemptId)).thenReturn(Optional.of(summary));
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(any())).thenReturn(List.of(stillFlagged));

        assertThatThrownBy(() -> service.releaseAttempt(attemptId))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void nonOwnerProfessorReceives404OnMatrix() {
        UUID professorId = UUID.randomUUID();
        UUID otherConfigId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(otherConfigId, professorId)).thenReturn(false);

        assertThatThrownBy(() -> service.getMatrix(otherConfigId))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
