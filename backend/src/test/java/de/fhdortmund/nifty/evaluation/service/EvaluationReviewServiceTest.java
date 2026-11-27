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
import de.fhdortmund.nifty.auth.service.InviteService;
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
 * US-EV-04 / REQ-SM-02, REQ-SM-03, REQ-SM-06, REQ-FE-02 / Decision 2.30:
 * individual approval (no bulk approval), override requires non-blank reason + 0..4 bounds
 * + calls AuditService + preserves aiLevel + recalculates Formula B without LLM, explicit release
 * blocks on flagged items, and unowned config returns 404.
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
    private InviteService inviteService;
    private EvaluationReviewService service;

    @BeforeEach
    void setUp() {
        inviteService = mock(InviteService.class);
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
                recalc,
                inviteService);
    }

    // REQ-SM-11, REQ-SM-12: the professor UI needs the candidate name and the evaluation status.
    @Test
    void matrixRowAndDrilldownCarryCandidateRefAndEvaluationStatus() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId, List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)), List.of()));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.7000"), List.of()));
        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        when(formulaVersionRepository.findTopByConfigIdOrderByVersionNumberDesc(configId)).thenReturn(Optional.of(formula));
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, null, "QUEUED", "k-ref");
        CandidateEvaluationSummaryEntity summary = CandidateEvaluationSummaryEntity.queued(
                attemptId, configId, inviteId, 1, formula, run);
        summary.markEvaluationStatus("RUNNING");
        when(summaryRepository.findLatestPerInvite(configId)).thenReturn(List.of(summary));
        when(summaryRepository.findByAttemptId(attemptId)).thenReturn(Optional.of(summary));
        when(skillScoreRepository.findBySummaryIdIn(any())).thenReturn(List.of());
        when(skillScoreRepository.findBySummaryId(any())).thenReturn(List.of());
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(any())).thenReturn(List.of());
        when(inviteService.candidateRefs(configId)).thenReturn(Map.of(inviteId, "anna.schmidt"));

        EvaluationDtos.MatrixResponse matrix = service.getMatrix(configId);
        EvaluationDtos.AttemptEvaluationDrilldownResponse drilldown = service.getAttemptDrilldown(attemptId);

        assertThat(matrix.rows().getFirst().candidateRef()).isEqualTo("anna.schmidt");
        assertThat(drilldown.candidateRef()).isEqualTo("anna.schmidt");
        assertThat(drilldown.evaluationStatus()).isEqualTo("RUNNING");
    }

    @Test
    void overrideValidatesZeroToFourBoundariesAndPreservesReleasedStatusAndRecalculatesSkillScores() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();

        // Boundary checks: -1 and 5 must fail validation; 0 and 4 must succeed (REQ-SM-02).
        assertThatThrownBy(() -> service.overrideEvaluation(
                responseId, new EvaluationDtos.OverrideEvaluationRequest(-1, "Too low")))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> service.overrideEvaluation(
                responseId, new EvaluationDtos.OverrideEvaluationRequest(5, "Too high")))
                .isInstanceOf(FieldValidationException.class);

        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "Spring Boot", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain transactions.", List.of(skillId)))));

        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"),
                Map.of(q1.toString(), BigDecimal.ONE, q2.toString(), BigDecimal.ONE), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k-rel");
        CandidateEvaluationSummaryEntity releasedSummary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                new BigDecimal("3.00"), new BigDecimal("0.5000"), 0, "auto_released", java.time.Instant.now());

        // Unverified quote overridden to 4 stays SCORED via overriddenBy != null; INVENTED entry stays out of coverage.
        ResponseEvaluationEntity unverified = new ResponseEvaluationEntity(
                run, releasedSummary, UUID.randomUUID(), q1, skillId, "core", BigDecimal.ONE,
                null, null, "bad quote", null, false, null, null, null,
                new BigDecimal("0.2000"), "UNVERIFIED_QUOTE", "auto_released", null);
        ResponseEvaluationEntity invented = new ResponseEvaluationEntity(
                run, releasedSummary, UUID.randomUUID(), q2, skillId, "invented", BigDecimal.ONE,
                4, 4, "invented verified quote", "invented verified quote", true, "invented verified quote", "ok",
                4, new BigDecimal("0.9000"), null, "auto_released", 4);

        stubResponse(responseId, unverified);
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(any())).thenReturn(List.of(unverified, invented));

        EvaluationDtos.OverrideEvaluationResponse res = service.overrideEvaluation(
                responseId, new EvaluationDtos.OverrideEvaluationRequest(4, "Verified during oral check"));

        assertThat(res.effectiveLevel()).isEqualTo(4);
        assertThat(releasedSummary.getReleaseStatus()).isEqualTo("overridden");
        // q1 (core, overridden without verified quote) counts -> 0.5000; q2 (invented) does NOT count toward coverage!
        assertThat(res.updatedCoverage()).isEqualByComparingTo("0.5000");
        assertThat(res.updatedFitScore()).isEqualByComparingTo("4.00");
        verify(skillScoreRepository).deleteBySummaryId(releasedSummary.getId());
        verify(skillScoreRepository).saveAll(any());

        // Entity-level immutability guard: approve() must reject an already-overridden response (Decision 2.10).
        assertThatThrownBy(() -> unverified.approve(java.time.Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approvingOneOfTwoFlaggedResponsesKeepsSummaryFlaggedAndRefreshesSkillScores() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID resp1Id = UUID.randomUUID();

        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId, List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)), List.of()));

        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(q1.toString(), BigDecimal.ONE), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k-two");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                new BigDecimal("2.00"), BigDecimal.ONE, 2, "flagged", null);
        ResponseEvaluationEntity f1 = verifiedResponse(run, summary, q1, skillId, 2, "flagged");
        ResponseEvaluationEntity f2 = verifiedResponse(run, summary, q1, skillId, 2, "flagged");

        stubResponse(resp1Id, f1);
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(any())).thenReturn(List.of(f1, f2));

        EvaluationDtos.ApproveEvaluationResponse approved = service.approveEvaluation(resp1Id);

        assertThat(approved.remainingFlaggedCount()).isEqualTo(1);
        assertThat(summary.getReleaseStatus()).isEqualTo("flagged");
        verify(skillScoreRepository).deleteBySummaryId(summary.getId());
        verify(skillScoreRepository).saveAll(any());
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

        stubResponse(responseId, response);
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

        when(summaryRepository.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.of(summary));
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

    @Test
    void approveIndividualFlaggedNaResponseUnblocksReleaseWithoutFabricatingScore() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();

        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "Docker", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain multi-stage builds.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.0000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("1.00"), new BigDecimal("0.6000"), List.of(skillId)))));

        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(q1.toString(), new BigDecimal("1.00")), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k-na");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                null, BigDecimal.ZERO, 1, "flagged", null);
        ResponseEvaluationEntity flaggedNa = new ResponseEvaluationEntity(
                run, summary, UUID.randomUUID(), q1, skillId, "core", new BigDecimal("1.00"),
                null, null, "hallucinated quote", null, false, null, null,
                null, new BigDecimal("0.2000"), "UNVERIFIED_QUOTE", "flagged", null);

        stubResponse(responseId, flaggedNa);
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(any())).thenReturn(List.of(flaggedNa));

        EvaluationDtos.ApproveEvaluationResponse approved = service.approveEvaluation(responseId);

        assertThat(approved.reviewStatus()).isEqualTo(ReleaseStatus.APPROVED);
        assertThat(approved.effectiveLevel()).isNull();
        assertThat(approved.remainingFlaggedCount()).isZero();
        assertThat(flaggedNa.getEffectiveLevel()).isNull();
        assertThat(summary.getFlaggedCount()).isZero();
        verify(auditService).record(
                eq(professorId.toString()),
                eq(AuditActorRole.PROFESSOR),
                eq("RESPONSE_EVALUATION"),
                eq(responseId),
                eq("SCORE_APPROVED"),
                eq("flagged"),
                eq("approved"),
                eq(null));
    }

    @Test
    void exportTranscriptOmitsUnverifiedQuoteAndNeverLeaksRun1Quote() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();

        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain indexes.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.5000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("1.00"), new BigDecimal("0.6000"), List.of(skillId)))));

        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k-tr");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                null, BigDecimal.ZERO, 1, "flagged", null);
        ResponseEvaluationEntity unverified = new ResponseEvaluationEntity(
                run, summary, entryId, q1, skillId, "core", new BigDecimal("1.00"),
                2, 2, "FABRICATED_HALLUCINATED_QUOTE", "FABRICATED_HALLUCINATED_QUOTE",
                false, null, "bad", null, new BigDecimal("0.3000"), "UNVERIFIED_QUOTE", "flagged", null);
        ResponseEvaluationEntity verified = new ResponseEvaluationEntity(
                run, summary, entryId, q1, skillId, "core", new BigDecimal("1.00"),
                3, 3, "exact verified quote", "exact verified quote",
                true, "exact verified quote", "good", 3, new BigDecimal("0.9200"), null, "approved", 3);

        when(summaryRepository.findByAttemptId(attemptId)).thenReturn(Optional.of(summary));
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(any())).thenReturn(List.of(unverified, verified));
        when(assessmentQueryService.getTranscriptForAttempt(attemptId)).thenReturn(
                new de.fhdortmund.nifty.assessment.service.ImmutableTranscriptView(
                        attemptId, configId, summary.getInviteId(), "v1", 1,
                        java.time.Instant.now().minusSeconds(60), java.time.Instant.now(),
                        List.of(new de.fhdortmund.nifty.assessment.service.TranscriptEntryView(
                                entryId, 1, q1, de.fhdortmund.nifty.assessment.service.EntryOrigin.CORE,
                                "Explain indexes.", "No idea really.", List.of(skillId),
                                java.time.Instant.now().minusSeconds(60), java.time.Instant.now(),
                                java.time.Instant.now(), 20, false, true, false))));

        EvaluationDtos.TranscriptExportResponse export = service.exportTranscript(attemptId);

        assertThat(export.format()).isEqualTo("assessment-transcript/v1");
        assertThat(export.entries()).hasSize(1);
        assertThat(export.entries().getFirst().answer().pasteSuspected()).isTrue();
        assertThat(export.evidence()).hasSize(2);
        assertThat(export.evidence().get(0).quoteVerified()).isFalse();
        assertThat(export.evidence().get(0).quote()).isNull();
        assertThat(export.evidence().get(1).quoteVerified()).isTrue();
        assertThat(export.evidence().get(1).quote()).isEqualTo("exact verified quote");
    }

    @Test
    void getMatrixDrilldownAndReleaseHappyPathsWorkForOwnedConfig() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();

        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain indexes.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.7000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("1.00"), new BigDecimal("0.6000"), List.of(skillId)))));

        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k-ok");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                new BigDecimal("3.00"), BigDecimal.ONE, 0, "pending", null);
        ResponseEvaluationEntity pending = new ResponseEvaluationEntity(
                run, summary, UUID.randomUUID(), q1, skillId, "core", new BigDecimal("1.00"),
                3, 3, "index quote", "index quote", true, "index quote", "solid",
                3, new BigDecimal("0.9200"), null, "pending", 3);

        when(formulaVersionRepository.findTopByConfigIdOrderByVersionNumberDesc(configId)).thenReturn(Optional.of(formula));
        when(summaryRepository.findLatestPerInvite(configId)).thenReturn(List.of(summary));
        when(summaryRepository.findByAttemptId(attemptId)).thenReturn(Optional.of(summary));
        when(summaryRepository.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.of(summary));
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(any())).thenReturn(List.of(pending));
        when(skillScoreRepository.findBySummaryIdIn(any())).thenReturn(List.of());
        when(skillScoreRepository.findBySummaryId(any())).thenReturn(List.of());

        EvaluationDtos.MatrixResponse matrix = service.getMatrix(configId);
        assertThat(matrix.skills()).hasSize(1);
        assertThat(matrix.rows()).hasSize(1);

        EvaluationDtos.AttemptEvaluationDrilldownResponse drilldown = service.getAttemptDrilldown(attemptId);
        assertThat(drilldown.responses()).hasSize(1);
        assertThat(drilldown.responses().getFirst().skillName()).isEqualTo("SQL");

        EvaluationDtos.ReleaseAttemptResponse released = service.releaseAttempt(attemptId);
        assertThat(released.releaseStatus()).isEqualTo(ReleaseStatus.APPROVED);
        assertThat(summary.getReleaseStatus()).isEqualTo("approved");
        assertThat(pending.getReviewStatus()).isEqualTo("approved");
        verify(skillScoreRepository).deleteBySummaryId(summary.getId());
        verify(skillScoreRepository).saveAll(any());
        verify(auditService).record(
                eq(professorId.toString()),
                eq(AuditActorRole.PROFESSOR),
                eq("ATTEMPT"),
                eq(attemptId),
                eq("ATTEMPT_RELEASED"),
                eq("pending"),
                eq("approved"),
                eq(null));
    }

    @Test
    void queuedRunningOrFailedLatestAttemptShowsAsWaitingRowWithoutScores() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId, List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)), List.of()));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.7000"), List.of()));
        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        when(formulaVersionRepository.findTopByConfigIdOrderByVersionNumberDesc(configId)).thenReturn(Optional.of(formula));

        List<CandidateEvaluationSummaryEntity> waiting = new java.util.ArrayList<>();
        for (String status : List.of("QUEUED", "RUNNING", "FAILED")) {
            UUID attemptId = UUID.randomUUID();
            EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, null, "QUEUED", attemptId.toString());
            CandidateEvaluationSummaryEntity summary = CandidateEvaluationSummaryEntity.queued(
                    attemptId, configId, UUID.randomUUID(), 2, formula, run);
            summary.markEvaluationStatus(status);
            waiting.add(summary);
        }
        when(summaryRepository.findLatestPerInvite(configId)).thenReturn(waiting);
        when(skillScoreRepository.findBySummaryIdIn(any())).thenReturn(List.of());

        EvaluationDtos.MatrixResponse matrix = service.getMatrix(configId);

        assertThat(matrix.rows()).hasSize(3);
        assertThat(matrix.rows()).extracting(EvaluationDtos.MatrixCandidateRow::evaluationStatus)
                .containsExactly("QUEUED", "RUNNING", "FAILED");
        for (EvaluationDtos.MatrixCandidateRow row : matrix.rows()) {
            assertThat(row.releaseStatus()).isEqualTo(ReleaseStatus.PENDING);
            assertThat(row.fitScore()).isNull();
            assertThat(row.cells()).hasSize(1);
            assertThat(row.cells().getFirst().status()).isEqualTo(ScoreStatus.NOT_ASSESSED);
            assertThat(row.cells().getFirst().value()).isNull();
        }
    }

    // REQ-EP-12: the professor re-runs a FAILED evaluation from the Evidence dialog.
    @Test
    void retryResetsAFailedRunToQueuedWithZeroTriesAndAuditsIt() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, null, "RUNNING", "k-retry");
        org.springframework.test.util.ReflectionTestUtils.setField(run, "attemptCount", 3);
        run.markFailed("boom", java.time.Instant.now());
        CandidateEvaluationSummaryEntity summary = CandidateEvaluationSummaryEntity.queued(
                attemptId, configId, UUID.randomUUID(), 1, formula, run);
        summary.markEvaluationStatus("FAILED");
        when(summaryRepository.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.of(summary));

        EvaluationDtos.RetryEvaluationResponse response = service.retryEvaluation(attemptId);

        assertThat(response.evaluationStatus()).isEqualTo("QUEUED");
        assertThat(run.getStatus()).isEqualTo("QUEUED");
        assertThat(run.getAttemptCount()).isZero();
        assertThat(run.getErrorMessage()).isNull();
        assertThat(summary.getEvaluationStatus()).isEqualTo("QUEUED");
        verify(auditService).record(
                professorId.toString(), AuditActorRole.PROFESSOR, "ATTEMPT", attemptId,
                "EVALUATION_RETRIED", "FAILED", "QUEUED", null);
    }

    @Test
    void retryOfANonFailedRunIsRefusedWithConflictAndChangesNothing() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k-done");
        CandidateEvaluationSummaryEntity summary = CandidateEvaluationSummaryEntity.queued(
                attemptId, configId, UUID.randomUUID(), 1, formula, run);
        summary.markEvaluationStatus("COMPLETED");
        when(summaryRepository.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.of(summary));

        assertThatThrownBy(() -> service.retryEvaluation(attemptId))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(summary.getEvaluationStatus()).isEqualTo("COMPLETED");
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void retryOfAnotherProfessorsAttemptIsNotFound() {
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(UUID.randomUUID());
        when(studioQueryService.ownedBy(any(), any())).thenReturn(false);
        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, null, "QUEUED", "k-foreign");
        when(summaryRepository.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.of(
                CandidateEvaluationSummaryEntity.queued(attemptId, configId, UUID.randomUUID(), 1, formula, run)));

        assertThatThrownBy(() -> service.retryEvaluation(attemptId))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void releaseRefusesAnAttemptThatIsStillQueuedRunningOrFailed() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, null, "QUEUED", "k-q");
        CandidateEvaluationSummaryEntity queued = CandidateEvaluationSummaryEntity.queued(
                attemptId, configId, UUID.randomUUID(), 1, formula, run);
        when(summaryRepository.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.of(queued));

        assertThatThrownBy(() -> service.releaseAttempt(attemptId))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(queued.getReleaseStatus()).isEqualTo("pending");
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void releaseReadsTheSummaryThroughTheRowLock() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, null, "QUEUED", "k-lock");
        CandidateEvaluationSummaryEntity queued = CandidateEvaluationSummaryEntity.queued(
                attemptId, configId, UUID.randomUUID(), 1, formula, run);
        when(summaryRepository.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.of(queued));

        assertThatThrownBy(() -> service.releaseAttempt(attemptId)).isInstanceOf(DomainException.class);

        // Decision 2.10: the row lock makes two concurrent releases write one audit row (see EvaluationReleaseRaceTest).
        verify(summaryRepository).findByAttemptIdForUpdate(attemptId);
        verify(summaryRepository, never()).findByAttemptId(attemptId);
    }

    @Test
    void releaseRefusesAlreadyReleasedAttemptWithConflict() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);

        for (String releasedStatus : List.of("auto_released", "approved", "overridden")) {
            UUID attemptId = UUID.randomUUID();
            FormulaVersionEntity formula = new FormulaVersionEntity(
                    configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
            EvaluationRunEntity run = new EvaluationRunEntity(
                    attemptId, configId, formula, "v1", "COMPLETED", "k-" + releasedStatus);
            java.time.Instant originalReleasedAt = java.time.Instant.parse("2026-11-26T10:00:00Z");
            CandidateEvaluationSummaryEntity released = new CandidateEvaluationSummaryEntity(
                    attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                    new BigDecimal("3.00"), BigDecimal.ONE, 0, releasedStatus, originalReleasedAt);
            when(summaryRepository.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.of(released));

            assertThatThrownBy(() -> service.releaseAttempt(attemptId))
                    .isInstanceOf(DomainException.class)
                    .extracting(ex -> ((DomainException) ex).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
            assertThat(released.getReleaseStatus()).isEqualTo(releasedStatus);
            assertThat(released.getReleasedAt()).isEqualTo(originalReleasedAt);
        }
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void approveRefusesAnOverriddenResponseSoTheOverrideSurvives() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(q1.toString(), BigDecimal.ONE), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k-ov");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                new BigDecimal("3.00"), BigDecimal.ONE, 0, "overridden", java.time.Instant.now());
        ResponseEvaluationEntity overridden = new ResponseEvaluationEntity(
                run, summary, UUID.randomUUID(), q1, UUID.randomUUID(), "core", BigDecimal.ONE,
                null, null, "x", null, false, null, null, null, new BigDecimal("0.2000"), "UNVERIFIED_QUOTE", "flagged", null);
        overridden.overrideLevel(3, professorId, "Follow-up showed mastery", java.time.Instant.now());
        stubResponse(responseId, overridden);

        assertThatThrownBy(() -> service.approveEvaluation(responseId))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(overridden.getReviewStatus()).isEqualTo("overridden");
        assertThat(overridden.getEffectiveLevel()).isEqualTo(3);
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void recalculationUsesTheFormulaVersionWeightsNotTheLiveStudioWeights() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID q3 = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId, List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)), List.of()));
        // The live studio weight of q3 is 10 now; the snapshot says 1. The snapshot must win (REQ-FE-02).
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.5000"),
                List.of(new ScoringQuestion(q3, new BigDecimal("10.00"), new BigDecimal("0.6000"), List.of(skillId)))));

        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"),
                Map.of(q1.toString(), BigDecimal.ONE, q2.toString(), BigDecimal.ONE, q3.toString(), BigDecimal.ONE), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k-w");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                new BigDecimal("3.33"), BigDecimal.ONE, 1, "flagged", null);
        ResponseEvaluationEntity r1 = verifiedResponse(run, summary, q1, skillId, 4, "approved");
        ResponseEvaluationEntity r2 = verifiedResponse(run, summary, q2, skillId, 4, "approved");
        ResponseEvaluationEntity r3 = verifiedResponse(run, summary, q3, skillId, 2, "flagged");
        stubResponse(responseId, r3);
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(any())).thenReturn(List.of(r1, r2, r3));

        EvaluationDtos.OverrideEvaluationResponse result = service.overrideEvaluation(
                responseId, new EvaluationDtos.OverrideEvaluationRequest(0, "Answer was wrong on review"));

        // Snapshot weights 1,1,1 -> (4 + 4 + 0) / 3 = 2.67. Live weight 10 would cap q3 and give 2.00.
        assertThat(result.updatedFitScore()).isEqualByComparingTo("2.67");
    }

    // REQ-SM-14: approve and override resolve the attempt first, lock its summary row, and only then load the response.
    private void stubResponse(UUID responseId, ResponseEvaluationEntity response) {
        CandidateEvaluationSummaryEntity summary = response.getSummary();
        when(responseEvaluationRepository.findAttemptIdById(responseId)).thenReturn(Optional.of(summary.getAttemptId()));
        when(summaryRepository.findByAttemptIdForUpdate(summary.getAttemptId())).thenReturn(Optional.of(summary));
        when(responseEvaluationRepository.findWithSummaryById(responseId)).thenReturn(Optional.of(response));
    }

    @Test
    void approveAndOverrideLockTheSummaryRowBeforeAnyResponseWrite() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId, List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)), List.of()));
        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(q1.toString(), BigDecimal.ONE), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k-order");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                new BigDecimal("2.00"), BigDecimal.ONE, 2, "flagged", null);
        ResponseEvaluationEntity toApprove = verifiedResponse(run, summary, q1, skillId, 2, "flagged");
        ResponseEvaluationEntity toOverride = verifiedResponse(run, summary, q1, skillId, 2, "flagged");
        UUID approveId = UUID.randomUUID();
        UUID overrideId = UUID.randomUUID();
        stubResponse(approveId, toApprove);
        stubResponse(overrideId, toOverride);
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(any())).thenReturn(List.of(toApprove, toOverride));

        service.approveEvaluation(approveId);
        service.overrideEvaluation(overrideId, new EvaluationDtos.OverrideEvaluationRequest(3, "Checked by hand"));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(summaryRepository, responseEvaluationRepository);
        order.verify(summaryRepository).findByAttemptIdForUpdate(attemptId);
        order.verify(responseEvaluationRepository).save(toApprove);
        order.verify(summaryRepository).findByAttemptIdForUpdate(attemptId);
        order.verify(responseEvaluationRepository).save(toOverride);
    }

    @Test
    void approveOfAnotherProfessorsResponseIsNotFound() {
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(UUID.randomUUID());
        when(studioQueryService.ownedBy(any(), any())).thenReturn(false);
        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(q1.toString(), BigDecimal.ONE), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k-foreign-r");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                new BigDecimal("2.00"), BigDecimal.ONE, 1, "flagged", null);
        ResponseEvaluationEntity response = verifiedResponse(run, summary, q1, skillId, 2, "flagged");
        UUID responseId = UUID.randomUUID();
        stubResponse(responseId, response);

        assertThatThrownBy(() -> service.approveEvaluation(responseId))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(responseEvaluationRepository, never()).save(any());
    }

    private static ResponseEvaluationEntity verifiedResponse(
            EvaluationRunEntity run, CandidateEvaluationSummaryEntity summary,
            UUID questionId, UUID skillId, int level, String status) {
        return new ResponseEvaluationEntity(
                run, summary, UUID.randomUUID(), questionId, skillId, "core", BigDecimal.ONE,
                level, level, "verified quote text", "verified quote text", true, "verified quote text", "ok",
                level, new BigDecimal("0.9000"), null, status, level);
    }
}
