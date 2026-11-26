package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import de.fhdortmund.nifty.assessment.service.ImmutableTranscriptView;
import de.fhdortmund.nifty.assessment.service.TranscriptEntryView;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Decision 2.24: the real chain on PostgreSQL. A committed attempt is enqueued and evaluated asynchronously,
 * and the scheduled retry pass (switched on here with a short delay) finishes a run whose first try failed.
 */
@SpringBootTest(properties = {
        "nifty.evaluation.retry.enabled=true",
        "nifty.evaluation.retry.fixed-delay=PT0.2S"
})
@ActiveProfiles("test")
// The cached context would keep its scheduler running and touch the data of later test classes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EvaluationRetrySchedulerIntegrationTest extends PostgresContainerBase {

    private static final List<String> RUBRIC = List.of("None", "Basic", "Developing", "Proficient", "Expert");

    @Autowired
    private CandidateEvaluationSummaryRepository summaryRepository;
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
    private TwoRunResponseJudge judge;

    @Test
    void committedAttemptIsEvaluatedAndTransientFailureIsFinishedByTheRetryPass() {
        UUID configId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "Transactions", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain database transactions.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.5000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("1.00"), new BigDecimal("0.5000"), List.of(skillId)))));
        TranscriptEntryView entry = new TranscriptEntryView(
                UUID.randomUUID(), 1, q1, EntryOrigin.CORE, "Explain database transactions.",
                "I wrap writes in an atomic transaction with rollback on error.", List.of(skillId),
                Instant.now().minusSeconds(60), Instant.now(), Instant.now(), 40, false, false, false);
        when(assessmentQueryService.getTranscriptForAttempt(attemptId)).thenReturn(new ImmutableTranscriptView(
                attemptId, configId, inviteId, "v1", 1, Instant.now().minusSeconds(60), Instant.now(), List.of(entry)));
        TwoRunResponseJudge.ParsedRun run = new TwoRunResponseJudge.ParsedRun(
                true, 3, "atomic transaction with rollback", "clear", null);
        // First try fails like a transient ki:connect error, the retry pass then succeeds.
        when(judge.judge(any()))
                .thenThrow(new IllegalStateException("ki:connect 503"))
                .thenReturn(new TwoRunResponseJudge.TwoRunResult("evaluator-judge-v1", run, run));

        try {
            transactionTemplate.executeWithoutResult(tx ->
                    eventPublisher.publishEvent(new AttemptCompletedEvent(attemptId, configId, inviteId)));

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(summaryRepository.findByAttemptId(attemptId))
                            .hasValueSatisfying(s -> assertThat(s.isCompleted()).isTrue()));
            assertThat(jdbc.queryForObject(
                    "SELECT attempt_count FROM evaluation_run WHERE attempt_id = ?", Integer.class, attemptId))
                    .isEqualTo(2);
        } finally {
            transactionTemplate.executeWithoutResult(tx ->
                    eventPublisher.publishEvent(new AssessmentDeletedEvent(configId, "test-cleanup")));
        }
    }
}
