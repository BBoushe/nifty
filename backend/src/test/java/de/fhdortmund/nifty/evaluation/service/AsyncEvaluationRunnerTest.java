package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import de.fhdortmund.nifty.assessment.service.ImmutableTranscriptView;
import de.fhdortmund.nifty.assessment.service.TranscriptEntryView;
import de.fhdortmund.nifty.context.service.dto.JudgeContextInput;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunEntity;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunRepository;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionRepository;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationRepository;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.shared.config.AttemptCompletedEvent;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorQuestion;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import de.fhdortmund.nifty.studio.service.view.ScoringQuestion;
import de.fhdortmund.nifty.studio.service.view.ScoringView;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQ-EP-05, REQ-EP-06, REQ-EP-09 / Decisions 2.10, 2.20, 2.24:
 * Evaluates non-blank timed-out answers, skips blank entries, enqueues before dispatch, enforces idempotent
 * claims, and isolates failures without leaving partial skill_score rows.
 */
class AsyncEvaluationRunnerTest {

    private static final List<String> RUBRIC = List.of("None", "Basic", "Developing", "Proficient", "Expert");

    private StudioQueryService studioQueryService;
    private AssessmentQueryService assessmentQueryService;
    private TwoRunResponseJudge judge;
    private FormulaVersionRepository formulaVersionRepository;
    private EvaluationRunRepository evaluationRunRepository;
    private CandidateEvaluationSummaryRepository summaryRepository;
    private ResponseEvaluationRepository responseEvaluationRepository;
    private SkillScoreRepository skillScoreRepository;
    private AsyncEvaluationRunner runner;

    @BeforeEach
    void setUp() {
        studioQueryService = mock(StudioQueryService.class);
        assessmentQueryService = mock(AssessmentQueryService.class);
        judge = mock(TwoRunResponseJudge.class);
        formulaVersionRepository = mock(FormulaVersionRepository.class);
        evaluationRunRepository = mock(EvaluationRunRepository.class);
        summaryRepository = mock(CandidateEvaluationSummaryRepository.class);
        responseEvaluationRepository = mock(ResponseEvaluationRepository.class);
        skillScoreRepository = mock(SkillScoreRepository.class);

        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);

        runner = new AsyncEvaluationRunner(
                studioQueryService,
                assessmentQueryService,
                judge,
                new VerbatimQuoteVerifier(),
                new ConfidenceCalculator(),
                new SkillFormulaCalculator(),
                new ReleaseGateEvaluator(),
                formulaVersionRepository,
                evaluationRunRepository,
                summaryRepository,
                responseEvaluationRepository,
                skillScoreRepository,
                txTemplate,
                3,
                Duration.ofMinutes(30));
    }

    @Test
    void evaluatesNonBlankTimedOutEntryAndSkipsBlankEntry() {
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();

        FormulaVersionEntity formula = formulaWithId(configId, q1, q2);
        Queued queued = givenQueuedRun(configId, attemptId, inviteId, formula);

        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "Transactions", RUBRIC)),
                List.of(
                        new EvaluatorQuestion(q1, "Explain transactions.", List.of(skillId)),
                        new EvaluatorQuestion(q2, "Explain isolation.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId,
                new BigDecimal("0.5000"),
                List.of(
                        new ScoringQuestion(q1, new BigDecimal("1.00"), new BigDecimal("0.6000"), List.of(skillId)),
                        new ScoringQuestion(q2, new BigDecimal("1.00"), new BigDecimal("0.6000"), List.of(skillId)))));

        // q1 timed out WITH partial text (must be evaluated, Decision 2.20); q2 timed out BLANK (must be skipped)
        TranscriptEntryView timedOutWithText = new TranscriptEntryView(
                UUID.randomUUID(), 1, q1, EntryOrigin.CORE, "Explain transactions.",
                "I would wrap it in a transaction for atomicity", List.of(skillId),
                Instant.now().minusSeconds(60), Instant.now(), Instant.now(), 60, true, false, false);
        TranscriptEntryView blankTimedOut = new TranscriptEntryView(
                UUID.randomUUID(), 2, q2, EntryOrigin.CORE, "Explain isolation.",
                "   ", List.of(skillId),
                Instant.now().minusSeconds(60), Instant.now(), Instant.now(), 60, true, false, false);

        when(assessmentQueryService.getTranscriptForAttempt(attemptId)).thenReturn(new ImmutableTranscriptView(
                attemptId, configId, inviteId, "v1", 1,
                Instant.now().minusSeconds(120), Instant.now(), List.of(timedOutWithText, blankTimedOut)));

        TwoRunResponseJudge.ParsedRun validRun = new TwoRunResponseJudge.ParsedRun(
                true, 3, "wrap it in a transaction", "Understands atomicity", null);
        when(judge.judge(any())).thenReturn(new TwoRunResponseJudge.TwoRunResult("evaluator-judge-v1", validRun, validRun));

        runner.executeRun(queued.runId());

        ArgumentCaptor<JudgeContextInput> judgeCaptor = ArgumentCaptor.forClass(JudgeContextInput.class);
        verify(judge, times(1)).judge(judgeCaptor.capture());
        assertThat(judgeCaptor.getValue().entryId()).isEqualTo(timedOutWithText.entryId());
        assertThat(judgeCaptor.getValue().timedOut()).isTrue();

        assertThat(queued.summary().isCompleted()).isTrue();
        assertThat(queued.summary().getCoverage()).isEqualByComparingTo("0.5000");
        assertThat(queued.summary().getReleaseStatus()).isEqualTo("auto_released");
        assertThat(queued.run().getStatus()).isEqualTo("COMPLETED");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationEntity>> respCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(responseEvaluationRepository).saveAll(respCaptor.capture());
        assertThat(respCaptor.getValue()).hasSize(1);
        assertThat(respCaptor.getValue().getFirst().getReviewStatus()).isEqualTo("auto_released");
        assertThat(respCaptor.getValue().getFirst().getEffectiveLevel()).isEqualTo(3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<de.fhdortmund.nifty.evaluation.repository.SkillScoreEntity>> scoreCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(skillScoreRepository).saveAll(scoreCaptor.capture());
        assertThat(scoreCaptor.getValue()).hasSize(1);
        assertThat(scoreCaptor.getValue().getFirst().getReviewStatus()).isEqualTo("auto_released");
        assertThat(scoreCaptor.getValue().getFirst().getScore()).isEqualByComparingTo("3.00");
    }

    @Test
    void disagreeingVerifiedRunsUseMinLevelFlagAttemptAndDeduplicateTargetSkills() {
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();

        FormulaVersionEntity formula = formulaWithId(configId, q1);
        Queued queued = givenQueuedRun(configId, attemptId, inviteId, formula);

        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain indexes.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId,
                new BigDecimal("0.5000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("1.00"), new BigDecimal("0.5000"), List.of(skillId)))));

        // Duplicate skillId in targetSkillIds MUST be deduplicated so judge runs once per (entry, skill).
        TranscriptEntryView entry = new TranscriptEntryView(
                UUID.randomUUID(), 1, q1, EntryOrigin.CORE, "Explain indexes.",
                "B-tree indexes speed up equality and range lookups on email.", List.of(skillId, skillId),
                Instant.now().minusSeconds(60), Instant.now(), Instant.now(), 30, false, false, false);
        when(assessmentQueryService.getTranscriptForAttempt(attemptId)).thenReturn(new ImmutableTranscriptView(
                attemptId, configId, inviteId, "v1", 1,
                Instant.now().minusSeconds(60), Instant.now(), List.of(entry)));

        TwoRunResponseJudge.ParsedRun run1 = new TwoRunResponseJudge.ParsedRun(
                true, 1, "B-tree indexes speed up", "conservative", null);
        TwoRunResponseJudge.ParsedRun run2 = new TwoRunResponseJudge.ParsedRun(
                true, 4, "equality and range lookups", "optimistic", null);
        when(judge.judge(any())).thenReturn(new TwoRunResponseJudge.TwoRunResult("evaluator-judge-v1", run1, run2));

        runner.executeRun(queued.runId());

        verify(judge, times(1)).judge(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationEntity>> respCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(responseEvaluationRepository).saveAll(respCaptor.capture());
        assertThat(respCaptor.getValue()).hasSize(1);
        assertThat(respCaptor.getValue().getFirst().getEffectiveLevel()).isEqualTo(1);
        assertThat(respCaptor.getValue().getFirst().getReviewStatus()).isEqualTo("flagged");
        assertThat(queued.summary().getReleaseStatus()).isEqualTo("flagged");
    }

    @Test
    void countsPromptedAndUnpromptedContentTermsDeterministically() {
        int[] counts = AsyncEvaluationRunner.countPromptedAndUnpromptedTerms(
                "How does PostgreSQL handle index scans?",
                "PostgreSQL uses B-tree index structures with MVCC snapshots.");
        // Prompted (>=4 chars in both): postgresql, index -> 2; Unprompted: uses, tree, structures, with, mvcc, snapshots -> 6
        assertThat(counts[0]).isEqualTo(2);
        assertThat(counts[1]).isEqualTo(6);

        int[] empty = AsyncEvaluationRunner.countPromptedAndUnpromptedTerms(null, "a b c");
        assertThat(empty).containsExactly(0, 0);
    }

    @Test
    void enqueuePersistsQueuedRunAndSummaryBeforeAnyLlmWork() {
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        FormulaVersionEntity formula = formulaWithId(configId, UUID.randomUUID());
        when(formulaVersionRepository.findTopByConfigIdOrderByVersionNumberDesc(configId))
                .thenReturn(Optional.of(formula));
        when(assessmentQueryService.getTranscriptForAttempt(attemptId)).thenReturn(new ImmutableTranscriptView(
                attemptId, configId, inviteId, "v1", 2, Instant.now(), Instant.now(), List.of()));
        when(evaluationRunRepository.save(any())).thenAnswer(inv -> {
            EvaluationRunEntity saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        UUID runId = runner.enqueue(new AttemptCompletedEvent(attemptId, configId, inviteId));

        assertThat(runId).isNotNull();
        ArgumentCaptor<EvaluationRunEntity> runCaptor = ArgumentCaptor.forClass(EvaluationRunEntity.class);
        verify(evaluationRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getStatus()).isEqualTo("QUEUED");
        assertThat(runCaptor.getValue().getAttemptCount()).isZero();
        ArgumentCaptor<CandidateEvaluationSummaryEntity> summaryCaptor =
                ArgumentCaptor.forClass(CandidateEvaluationSummaryEntity.class);
        verify(summaryRepository).save(summaryCaptor.capture());
        assertThat(summaryCaptor.getValue().getEvaluationStatus()).isEqualTo("QUEUED");
        assertThat(summaryCaptor.getValue().getAttemptNo()).isEqualTo(2);
        assertThat(summaryCaptor.getValue().getReleaseStatus()).isEqualTo("pending");
        verify(judge, never()).judge(any());
    }

    @Test
    void duplicateAttemptCompletedEventEnqueuesNothingEvenAfterANewFormulaVersion() {
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        FormulaVersionEntity formula = formulaWithId(configId, UUID.randomUUID());
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, null, "COMPLETED", "dup");
        when(summaryRepository.findByAttemptId(attemptId))
                .thenReturn(Optional.of(CandidateEvaluationSummaryEntity.queued(
                        attemptId, configId, inviteId, 1, formula, run)));

        UUID runId = runner.enqueue(new AttemptCompletedEvent(attemptId, configId, inviteId));

        assertThat(runId).isNull();
        verify(evaluationRunRepository, never()).save(any());
        verify(summaryRepository, never()).save(any());
        verify(judge, never()).judge(any());
    }

    @Test
    void runThatAnotherWorkerOwnsOrThatIsExhaustedMakesZeroLlmCalls() {
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.claim(any(), any(), any(), anyInt())).thenReturn(0);

        runner.executeRun(runId);

        verify(judge, never()).judge(any());
        verify(summaryRepository, never()).save(any());
        verify(skillScoreRepository, never()).saveAll(any());
    }

    @Test
    void failureKeepsRunRetryableAsFailedWithoutPersistingPartialSkillScores() {
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        FormulaVersionEntity formula = formulaWithId(configId, q1);
        Queued queued = givenQueuedRun(configId, attemptId, inviteId, formula);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain indexes.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.5000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("1.00"), new BigDecimal("0.6000"), List.of(skillId)))));
        when(assessmentQueryService.getTranscriptForAttempt(attemptId)).thenThrow(new RuntimeException("upstream down"));

        runner.executeRun(queued.runId());

        assertThat(queued.run().getStatus()).isEqualTo("FAILED");
        assertThat(queued.run().getErrorMessage()).contains("upstream down");
        assertThat(queued.summary().getEvaluationStatus()).isEqualTo("FAILED");
        verify(skillScoreRepository, never()).saveAll(any());
    }

    private Queued givenQueuedRun(UUID configId, UUID attemptId, UUID inviteId, FormulaVersionEntity formula) {
        UUID runId = UUID.randomUUID();
        UUID summaryId = UUID.randomUUID();
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, null, "QUEUED", attemptId.toString());
        ReflectionTestUtils.setField(run, "id", runId);
        CandidateEvaluationSummaryEntity summary =
                CandidateEvaluationSummaryEntity.queued(attemptId, configId, inviteId, 1, formula, run);
        ReflectionTestUtils.setField(summary, "id", summaryId);

        // The real claim UPDATE flips the row to RUNNING; the failure path only fails a RUNNING run.
        when(evaluationRunRepository.claim(any(), any(), any(), anyInt())).thenAnswer(inv -> {
            ReflectionTestUtils.setField(run, "status", "RUNNING");
            return 1;
        });
        when(evaluationRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(formulaVersionRepository.findTopByConfigIdOrderByVersionNumberDesc(configId))
                .thenReturn(Optional.of(formula));
        when(summaryRepository.findByAttemptId(attemptId)).thenReturn(Optional.of(summary));
        when(summaryRepository.findById(summaryId)).thenReturn(Optional.of(summary));
        return new Queued(runId, run, summary);
    }

    private record Queued(UUID runId, EvaluationRunEntity run, CandidateEvaluationSummaryEntity summary) {
    }

    private static FormulaVersionEntity formulaWithId(UUID configId, UUID... questionIds) {
        Map<String, BigDecimal> weights = new java.util.LinkedHashMap<>();
        for (UUID q : questionIds) {
            weights.put(q.toString(), new BigDecimal("1.00"));
        }
        return new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), weights, null);
    }
}
