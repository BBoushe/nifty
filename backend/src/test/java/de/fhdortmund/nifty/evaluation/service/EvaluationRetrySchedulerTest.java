package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.assessment.service.ImmutableTranscriptView;
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
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQ-EP-11, REQ-EP-13 / Decision 2.28: the retry pass fails a run that crashed on its last try and creates the run
 * of a completed attempt whose completion event was lost.
 */
class EvaluationRetrySchedulerTest {

    private AssessmentQueryService assessmentQueryService;
    private FormulaVersionRepository formulaVersionRepository;
    private EvaluationRunRepository evaluationRunRepository;
    private CandidateEvaluationSummaryRepository summaryRepository;
    private EvaluationRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        assessmentQueryService = mock(AssessmentQueryService.class);
        formulaVersionRepository = mock(FormulaVersionRepository.class);
        evaluationRunRepository = mock(EvaluationRunRepository.class);
        summaryRepository = mock(CandidateEvaluationSummaryRepository.class);

        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());

        AsyncEvaluationRunner runner = new AsyncEvaluationRunner(
                mock(StudioQueryService.class),
                assessmentQueryService,
                mock(TwoRunResponseJudge.class),
                new VerbatimQuoteVerifier(),
                new ConfidenceCalculator(),
                new SkillFormulaCalculator(),
                new ReleaseGateEvaluator(),
                formulaVersionRepository,
                evaluationRunRepository,
                summaryRepository,
                mock(ResponseEvaluationRepository.class),
                mock(SkillScoreRepository.class),
                new TransactionTemplate(txManager),
                3,
                Duration.ofMinutes(30));
        scheduler = new EvaluationRetryScheduler(runner);
    }

    @Test
    void staleRunningRunOnItsFinalTryBecomesFailedWithStaleFinalTry() {
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        FormulaVersionEntity formula = formula(configId);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, null, "RUNNING", "k1");
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(run, "attemptCount", 3);
        CandidateEvaluationSummaryEntity summary =
                CandidateEvaluationSummaryEntity.queued(attemptId, configId, UUID.randomUUID(), 1, formula, run);
        summary.markEvaluationStatus("RUNNING");
        when(evaluationRunRepository.findStaleFinalTryIds(eq(3), any())).thenReturn(List.of(run.getId()));
        when(evaluationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(summaryRepository.findByAttemptId(attemptId)).thenReturn(Optional.of(summary));

        scheduler.retryPass();

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getErrorMessage()).isEqualTo("STALE_FINAL_TRY");
        assertThat(summary.getEvaluationStatus()).isEqualTo("FAILED");
    }

    @Test
    void completedAttemptWithoutRunGetsExactlyOneRunEvenWhenThePassRunsTwice() {
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        FormulaVersionEntity formula = formula(configId);
        AttemptCompletedEvent event = new AttemptCompletedEvent(attemptId, configId, inviteId);
        when(assessmentQueryService.completedAttemptsSince(any())).thenReturn(List.of(event));
        when(assessmentQueryService.getTranscriptForAttempt(attemptId)).thenReturn(new ImmutableTranscriptView(
                attemptId, configId, inviteId, "v1", 1, Instant.now(), Instant.now(), List.of()));
        when(formulaVersionRepository.findTopByConfigIdOrderByVersionNumberDesc(configId))
                .thenReturn(Optional.of(formula));
        when(evaluationRunRepository.save(any())).thenAnswer(inv -> {
            EvaluationRunEntity saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        // The first pass finds no summary; once it is saved, the second pass sees it (REQ-EP-09 idempotent create).
        when(summaryRepository.save(any())).thenAnswer(inv -> {
            CandidateEvaluationSummaryEntity saved = inv.getArgument(0);
            when(summaryRepository.findByAttemptId(attemptId)).thenReturn(Optional.of(saved));
            return saved;
        });
        when(summaryRepository.findByAttemptId(attemptId)).thenReturn(Optional.empty());

        scheduler.retryPass();
        scheduler.retryPass();

        verify(evaluationRunRepository, times(1)).save(any());
        verify(summaryRepository, times(1)).save(any());
    }

    private static FormulaVersionEntity formula(UUID configId) {
        return new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(UUID.randomUUID().toString(), BigDecimal.ONE), null);
    }
}
