package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQ-EP-05, REQ-EP-06, REQ-EP-09 / Decisions 2.10, 2.20, 2.24:
 * Evaluates non-blank timed-out answers, skips blank entries, enforces idempotent runs,
 * and isolates failures without leaving partial skill_score rows.
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
                txTemplate);
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
        when(formulaVersionRepository.findTopByConfigIdOrderByVersionNumberDesc(configId))
                .thenReturn(Optional.of(formula));
        when(evaluationRunRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(evaluationRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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

        runner.executeEvaluation(new AttemptCompletedEvent(attemptId, configId, inviteId));

        ArgumentCaptor<JudgeContextInput> judgeCaptor = ArgumentCaptor.forClass(JudgeContextInput.class);
        verify(judge, times(1)).judge(judgeCaptor.capture());
        assertThat(judgeCaptor.getValue().entryId()).isEqualTo(timedOutWithText.entryId());
        assertThat(judgeCaptor.getValue().timedOut()).isTrue();

        ArgumentCaptor<CandidateEvaluationSummaryEntity> summaryCaptor =
                ArgumentCaptor.forClass(CandidateEvaluationSummaryEntity.class);
        verify(summaryRepository).save(summaryCaptor.capture());
        assertThat(summaryCaptor.getValue().getCoverage()).isEqualByComparingTo("0.5000");
        assertThat(summaryCaptor.getValue().getReleaseStatus()).isEqualTo("auto_released");
    }

    @Test
    void duplicateAttemptCompletedEventIsIdempotentWithZeroLlmCalls() {
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        FormulaVersionEntity formula = formulaWithId(configId, UUID.randomUUID());

        when(formulaVersionRepository.findTopByConfigIdOrderByVersionNumberDesc(configId))
                .thenReturn(Optional.of(formula));
        when(evaluationRunRepository.findByIdempotencyKey(any()))
                .thenReturn(Optional.of(new EvaluationRunEntity(
                        attemptId, configId, formula, "evaluator-judge-v1", "COMPLETED", "dup")));

        runner.executeEvaluation(new AttemptCompletedEvent(attemptId, configId, inviteId));

        verify(judge, never()).judge(any());
        verify(summaryRepository, never()).save(any());
        verify(skillScoreRepository, never()).saveAll(any());
    }

    @Test
    void failureIsolatesRunAsFailedWithoutPersistingPartialSkillScores() {
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        FormulaVersionEntity formula = formulaWithId(configId, q1);

        when(formulaVersionRepository.findTopByConfigIdOrderByVersionNumberDesc(configId))
                .thenReturn(Optional.of(formula));
        when(evaluationRunRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(evaluationRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain indexes.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.5000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("1.00"), new BigDecimal("0.6000"), List.of(skillId)))));
        when(assessmentQueryService.getTranscriptForAttempt(attemptId)).thenThrow(new RuntimeException("upstream down"));

        runner.executeEvaluation(new AttemptCompletedEvent(attemptId, configId, inviteId));

        ArgumentCaptor<EvaluationRunEntity> runCaptor = ArgumentCaptor.forClass(EvaluationRunEntity.class);
        verify(evaluationRunRepository, times(2)).save(runCaptor.capture());
        assertThat(runCaptor.getAllValues().getLast().getStatus()).isEqualTo("FAILED");
        verify(skillScoreRepository, never()).saveAll(any());
        verify(summaryRepository, never()).save(any());
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
