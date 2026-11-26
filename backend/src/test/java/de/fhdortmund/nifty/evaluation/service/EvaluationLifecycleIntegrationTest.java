package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import de.fhdortmund.nifty.assessment.service.ImmutableTranscriptView;
import de.fhdortmund.nifty.assessment.service.TranscriptEntryView;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.auth.service.CandidateSessionPrincipal;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunEntity;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunRepository;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionRepository;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreEntity;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import de.fhdortmund.nifty.shared.config.AttemptCompletedEvent;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorQuestion;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import de.fhdortmund.nifty.studio.service.view.ScoringQuestion;
import de.fhdortmund.nifty.studio.service.view.ScoringView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Review fixes for PR 29 on real PostgreSQL: no lost evaluations (REQ-EP-05, REQ-EP-09, Decision 2.24),
 * bounded retry, atomic claims, waiting attempts in matrix and query service (REQ-EV-01, REQ-SM-07),
 * and the retake rule (Decision 2.13).
 */
@SpringBootTest
@ActiveProfiles("test")
class EvaluationLifecycleIntegrationTest extends PostgresContainerBase {

    private static final List<String> RUBRIC = List.of("None", "Basic", "Developing", "Proficient", "Expert");
    private static final String ANSWER = "I wrap writes in an atomic transaction with rollback on error.";

    @Autowired
    private AsyncEvaluationRunner runner;
    @Autowired
    private EvaluationQueryService evaluationQueryService;
    @Autowired
    private CohortGapQueryService cohortGapQueryService;
    @Autowired
    private EvaluationReviewService reviewService;
    @Autowired
    private CandidateResultsService candidateResultsService;
    @Autowired
    private CandidateEvaluationSummaryRepository summaryRepository;
    @Autowired
    private EvaluationRunRepository runRepository;
    @Autowired
    private FormulaVersionRepository formulaVersionRepository;
    @Autowired
    private SkillScoreRepository skillScoreRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private StudioQueryService studioQueryService;
    @MockitoBean
    private AssessmentQueryService assessmentQueryService;
    @MockitoBean
    private AuthContextService authContextService;
    @MockitoBean
    private TwoRunResponseJudge judge;

    private record Ids(UUID configId, UUID inviteId, UUID attemptId, UUID skillId, UUID questionId) {
        static Ids fresh() {
            return new Ids(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        }

        AttemptCompletedEvent event() {
            return new AttemptCompletedEvent(attemptId, configId, inviteId);
        }
    }

    private void stubStudioAndTranscript(Ids ids, int attemptNo) {
        when(studioQueryService.evaluatorView(ids.configId())).thenReturn(new EvaluatorView(
                ids.configId(),
                List.of(new EvaluatorSkill(ids.skillId(), "Transactions", RUBRIC)),
                List.of(new EvaluatorQuestion(ids.questionId(), "Explain database transactions.", List.of(ids.skillId())))));
        when(studioQueryService.scoringView(ids.configId())).thenReturn(new ScoringView(
                ids.configId(),
                new BigDecimal("0.5000"),
                List.of(new ScoringQuestion(
                        ids.questionId(), new BigDecimal("1.00"), new BigDecimal("0.5000"), List.of(ids.skillId())))));
        TranscriptEntryView entry = new TranscriptEntryView(
                UUID.randomUUID(), 1, ids.questionId(), EntryOrigin.CORE, "Explain database transactions.", ANSWER,
                List.of(ids.skillId()), Instant.now().minusSeconds(60), Instant.now(), Instant.now(), 40,
                false, false, false);
        when(assessmentQueryService.getTranscriptForAttempt(ids.attemptId())).thenReturn(new ImmutableTranscriptView(
                ids.attemptId(), ids.configId(), ids.inviteId(), "v1", attemptNo,
                Instant.now().minusSeconds(60), Instant.now(), List.of(entry)));
    }

    private static TwoRunResponseJudge.TwoRunResult goodJudgement() {
        TwoRunResponseJudge.ParsedRun run = new TwoRunResponseJudge.ParsedRun(
                true, 3, "atomic transaction with rollback", "clear", null);
        return new TwoRunResponseJudge.TwoRunResult("evaluator-judge-v1", run, run);
    }

    private String runStatus(UUID attemptId) {
        return jdbc.queryForObject("SELECT status FROM evaluation_run WHERE attempt_id = ?", String.class, attemptId);
    }

    private int runTries(UUID attemptId) {
        return jdbc.queryForObject("SELECT attempt_count FROM evaluation_run WHERE attempt_id = ?", Integer.class, attemptId);
    }

    private long runCount(UUID attemptId) {
        return jdbc.queryForObject("SELECT count(*) FROM evaluation_run WHERE attempt_id = ?", Long.class, attemptId);
    }

    private void cleanup(UUID configId) {
        transactionTemplate.executeWithoutResult(tx ->
                eventPublisher.publishEvent(new AssessmentDeletedEvent(configId, "test-cleanup")));
    }

    @Test
    void enqueuePersistsQueuedRunBeforeAnyLlmWorkAndIgnoresDuplicateEventAfterNewFormulaVersion() {
        Ids ids = Ids.fresh();
        stubStudioAndTranscript(ids, 1);
        try {
            UUID runId = runner.enqueue(ids.event());

            assertThat(runId).isNotNull();
            assertThat(runStatus(ids.attemptId())).isEqualTo("QUEUED");
            assertThat(runTries(ids.attemptId())).isZero();
            CandidateEvaluationSummaryEntity summary = summaryRepository.findByAttemptId(ids.attemptId()).orElseThrow();
            assertThat(summary.getEvaluationStatus()).isEqualTo("QUEUED");
            assertThat(summary.getAttemptNo()).isEqualTo(1);
            assertThat(runner.retryableRunIds()).contains(runId);
            verify(judge, never()).judge(any());

            // A new formula version appears, then the same event arrives again: no second run, no LLM work.
            formulaVersionRepository.save(new FormulaVersionEntity(
                    ids.configId(), 2, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null));
            assertThat(runner.enqueue(ids.event())).isNull();
            assertThat(runCount(ids.attemptId())).isEqualTo(1);
            verify(judge, never()).judge(any());

            // The queued run is evaluated under the newest formula version.
            when(judge.judge(any())).thenReturn(goodJudgement());
            runner.executeRun(runId);
            assertThat(runStatus(ids.attemptId())).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject(
                    "SELECT fv.version_number FROM candidate_evaluation_summary s "
                            + "JOIN formula_version fv ON fv.id = s.formula_version_id WHERE s.attempt_id = ?",
                    Integer.class, ids.attemptId())).isEqualTo(2);
        } finally {
            cleanup(ids.configId());
        }
    }

    @Test
    void transientJudgeFailureIsRetriedAndTriesAreBounded() {
        Ids ids = Ids.fresh();
        stubStudioAndTranscript(ids, 1);
        try {
            UUID runId = runner.enqueue(ids.event());
            when(judge.judge(any()))
                    .thenThrow(new IllegalStateException("ki:connect 503"))
                    .thenReturn(goodJudgement());

            runner.executeRun(runId);
            assertThat(runStatus(ids.attemptId())).isEqualTo("FAILED");
            assertThat(runTries(ids.attemptId())).isEqualTo(1);
            assertThat(summaryRepository.findByAttemptId(ids.attemptId()).orElseThrow().getEvaluationStatus())
                    .isEqualTo("FAILED");
            assertThat(runner.retryableRunIds()).contains(runId);

            runner.executeRun(runId);
            assertThat(runStatus(ids.attemptId())).isEqualTo("COMPLETED");
            assertThat(runTries(ids.attemptId())).isEqualTo(2);
            assertThat(summaryRepository.findByAttemptId(ids.attemptId()).orElseThrow().isCompleted()).isTrue();
            assertThat(runner.retryableRunIds()).doesNotContain(runId);
        } finally {
            cleanup(ids.configId());
        }
    }

    @Test
    void alwaysFailingRunStopsAfterMaxAttemptsAndStaysVisibleAsFailed() {
        Ids ids = Ids.fresh();
        stubStudioAndTranscript(ids, 1);
        try {
            UUID runId = runner.enqueue(ids.event());
            when(judge.judge(any())).thenThrow(new IllegalStateException("down"));

            for (int i = 0; i < 5; i++) {
                runner.executeRun(runId);
            }

            assertThat(runStatus(ids.attemptId())).isEqualTo("FAILED");
            assertThat(runTries(ids.attemptId())).isEqualTo(3);
            verify(judge, times(3)).judge(any());
            assertThat(runner.retryableRunIds()).doesNotContain(runId);
        } finally {
            cleanup(ids.configId());
        }
    }

    @Test
    void twoWorkersNeverEvaluateTheSameAttemptConcurrently() throws Exception {
        Ids ids = Ids.fresh();
        stubStudioAndTranscript(ids, 1);
        try {
            UUID runId = runner.enqueue(ids.event());
            when(judge.judge(any())).thenAnswer(inv -> {
                Thread.sleep(400);
                return goodJudgement();
            });

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    runner.executeRun(runId);
                    return null;
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
            pool.shutdown();

            verify(judge, times(1)).judge(any());
            assertThat(runStatus(ids.attemptId())).isEqualTo("COMPLETED");
            assertThat(runTries(ids.attemptId())).isEqualTo(1);
        } finally {
            cleanup(ids.configId());
        }
    }

    @Test
    void freshRunningRunIsLeftAloneButStaleRunningRunIsReclaimed() {
        Ids ids = Ids.fresh();
        stubStudioAndTranscript(ids, 1);
        try {
            UUID runId = runner.enqueue(ids.event());
            when(judge.judge(any())).thenReturn(goodJudgement());

            jdbc.update("UPDATE evaluation_run SET status = 'RUNNING', attempt_count = 1, updated_at = now() WHERE id = ?", runId);
            assertThat(runner.retryableRunIds()).doesNotContain(runId);
            runner.executeRun(runId);
            verify(judge, never()).judge(any());

            jdbc.update("UPDATE evaluation_run SET updated_at = now() - interval '2 hours' WHERE id = ?", runId);
            assertThat(runner.retryableRunIds()).contains(runId);
            runner.executeRun(runId);
            assertThat(runStatus(ids.attemptId())).isEqualTo("COMPLETED");
            assertThat(runTries(ids.attemptId())).isEqualTo(2);
        } finally {
            cleanup(ids.configId());
        }
    }

    @Test
    void waitingRetakeHidesTheOlderReleasedAttemptEverywhere() {
        Ids ids = Ids.fresh();
        UUID professorId = UUID.randomUUID();
        UUID attempt2 = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(authContextService.currentCandidate())
                .thenReturn(new CandidateSessionPrincipal(ids.inviteId(), ids.configId(), "ref"));
        when(studioQueryService.ownedBy(ids.configId(), professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(ids.configId())).thenReturn(new EvaluatorView(
                ids.configId(), List.of(new EvaluatorSkill(ids.skillId(), "Java", RUBRIC)), List.of()));
        when(studioQueryService.scoringView(ids.configId())).thenReturn(new ScoringView(
                ids.configId(), new BigDecimal("0.5000"), List.of()));
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                FormulaVersionEntity formula = formulaVersionRepository.save(new FormulaVersionEntity(
                        ids.configId(), 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), professorId));
                EvaluationRunEntity run1 = runRepository.save(new EvaluationRunEntity(
                        ids.attemptId(), ids.configId(), formula, "v1", "COMPLETED", ids.attemptId().toString()));
                CandidateEvaluationSummaryEntity first = summaryRepository.save(new CandidateEvaluationSummaryEntity(
                        ids.attemptId(), ids.configId(), ids.inviteId(), 1, formula, run1, "COMPLETED",
                        new BigDecimal("3.00"), BigDecimal.ONE, 0, "approved", Instant.now()));
                skillScoreRepository.save(new SkillScoreEntity(
                        first, formula, ids.skillId(), "SCORED", new BigDecimal("3.00"), new BigDecimal("0.9000"), 1,
                        "approved"));
                EvaluationRunEntity run2 = runRepository.save(new EvaluationRunEntity(
                        attempt2, ids.configId(), formula, null, "QUEUED", attempt2.toString()));
                summaryRepository.save(CandidateEvaluationSummaryEntity.queued(
                        attempt2, ids.configId(), ids.inviteId(), 2, formula, run2));
            });

            for (String status : List.of("QUEUED", "RUNNING", "FAILED")) {
                jdbc.update("UPDATE candidate_evaluation_summary SET evaluation_status = ? WHERE attempt_id = ?",
                        status, attempt2);

                // Team Builder: the invite is waiting, the old released scores are gone.
                CandidateProfilesView profiles = evaluationQueryService.getCandidateProfilesForConfig(ids.configId());
                assertThat(profiles.released()).as(status).isEmpty();
                assertThat(profiles.pendingInviteIds()).as(status).containsExactly(ids.inviteId());

                // Matrix: one waiting row of attempt 2, no scores.
                EvaluationDtos.MatrixResponse matrix = reviewService.getMatrix(ids.configId());
                assertThat(matrix.rows()).as(status).hasSize(1);
                EvaluationDtos.MatrixCandidateRow row = matrix.rows().getFirst();
                assertThat(row.attemptId()).isEqualTo(attempt2);
                assertThat(row.attemptNo()).isEqualTo(2);
                assertThat(row.evaluationStatus()).isEqualTo(status);
                assertThat(row.fitScore()).isNull();
                assertThat(row.cells().getFirst().status()).isEqualTo(ScoreStatus.NOT_ASSESSED);

                // Cohort gaps count nobody and report N/A (null), never 0.0000.
                EvaluationDtos.CohortGapsResponse gaps = cohortGapQueryService.getCohortGaps(ids.configId());
                assertThat(gaps.releasedCandidateCount()).as(status).isZero();
                assertThat(gaps.skills().getFirst().naShare()).as(status).isNull();
                assertThat(gaps.skills().getFirst().meanScore()).isNull();

                // Candidate results: nothing is released for the newest attempt.
                EvaluationDtos.CandidateResultsResponse mine =
                        candidateResultsService.getResultsForCurrentCandidate(ids.configId());
                assertThat(mine.released()).as(status).isFalse();
                assertThat(mine.attemptId()).isEqualTo(attempt2);
                assertThat(mine.fitScore()).isNull();
            }
            verify(authContextService, atLeastOnce()).currentCandidate();
        } finally {
            cleanup(ids.configId());
        }
    }
}
