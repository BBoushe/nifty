package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import de.fhdortmund.nifty.assessment.service.ImmutableTranscriptView;
import de.fhdortmund.nifty.assessment.service.TranscriptEntryView;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationEntity;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationRepository;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cold review & mutation hardening integration test on real PostgreSQL Testcontainers:
 * 1. Proves the Spring-proxied AsyncEvaluationRunner persists rows cleanly (no read-only TX bug).
 * 2. Proves a hallucinated judge run's level and rationale are discarded even when the other run verifies.
 * 3. Proves a single-run parse failure is flagged (JUDGE_PARSE_FAILED).
 */
@SpringBootTest
@ActiveProfiles("test")
class AsyncEvaluationRunnerIntegrationTest extends PostgresContainerBase {

    private static final List<String> RUBRIC = List.of("None", "Basic", "Developing", "Proficient", "Expert");

    @Autowired
    private AsyncEvaluationRunner runner;
    @Autowired
    private CandidateEvaluationSummaryRepository summaryRepository;
    @Autowired
    private ResponseEvaluationRepository responseEvaluationRepository;
    @Autowired
    private SkillScoreRepository skillScoreRepository;
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
    void springProxiedRunnerPersistsToPostgresAndDiscardsHallucinatedOrParseFailedSingleRun() {
        UUID configId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();

        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "Transactions", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain database transactions.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId,
                new BigDecimal("0.5000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("1.00"), new BigDecimal("0.5000"), List.of(skillId)))));

        TranscriptEntryView entry = new TranscriptEntryView(
                entryId, 1, q1, EntryOrigin.CORE,
                "Explain database transactions.",
                "I wrap writes in an atomic transaction with rollback on error.",
                List.of(skillId),
                Instant.now().minusSeconds(60), Instant.now(), Instant.now(), 40,
                false, false, false);
        when(assessmentQueryService.getTranscriptForAttempt(attemptId)).thenReturn(new ImmutableTranscriptView(
                attemptId, configId, inviteId, "v1", 1,
                Instant.now().minusSeconds(60), Instant.now(), List.of(entry)));

        // Run 1 hallucinates quote with level 0; Run 2 verifies exact quote with level 4.
        // Hallucinated run 1 MUST be discarded (effectiveLevel must be 4, NOT min(0, 4) = 0!),
        // and the evaluation MUST still be flagged because run 1 quote failed verification.
        TwoRunResponseJudge.ParsedRun hallucinatedRun1 = new TwoRunResponseJudge.ParsedRun(
                true, 0, "completely fabricated quote text", "hallucinated rationale", null);
        TwoRunResponseJudge.ParsedRun verifiedRun2 = new TwoRunResponseJudge.ParsedRun(
                true, 4, "atomic transaction with rollback", "verified rationale", null);
        when(judge.judge(any())).thenReturn(
                new TwoRunResponseJudge.TwoRunResult("evaluator-judge-v1", hallucinatedRun1, verifiedRun2));

        try {
            // Call through the Spring proxy (not package-private directly) to exercise @Transactional proxy!
            runner.executeRun(runner.enqueue(new AttemptCompletedEvent(attemptId, configId, inviteId)));

            CandidateEvaluationSummaryEntity summary = summaryRepository.findByAttemptId(attemptId).orElseThrow();
            assertThat(summary.getEvaluationStatus()).isEqualTo("COMPLETED");
            assertThat(summary.getReleaseStatus()).isEqualTo("flagged");
            assertThat(summary.getFlaggedCount()).isEqualTo(1);
            assertThat(summary.getFitScore()).isEqualByComparingTo("4.00");

            List<ResponseEvaluationEntity> responses = responseEvaluationRepository
                    .findBySummaryIdOrderByIdAsc(summary.getId());
            assertThat(responses).hasSize(1);
            ResponseEvaluationEntity resp = responses.getFirst();
            assertThat(resp.isQuoteVerified()).isTrue();
            assertThat(resp.getVerifiedQuote()).isEqualTo("atomic transaction with rollback");
            assertThat(resp.getEffectiveLevel()).isEqualTo(4);
            assertThat(resp.getAiRationale()).isEqualTo("verified rationale");
            assertThat(resp.getFlagReasons()).contains("UNVERIFIED_QUOTE");

            List<SkillScoreEntity> scores = skillScoreRepository.findBySummaryId(summary.getId());
            assertThat(scores).hasSize(1);
            assertThat(scores.getFirst().getScore()).isEqualByComparingTo("4.00");
        } finally {
            transactionTemplate.executeWithoutResult(tx ->
                    eventPublisher.publishEvent(new AssessmentDeletedEvent(configId, "test-cleanup")));
        }
    }

    @Test
    void singleRunParseFailureIsFlaggedWithJudgeParseFailed() {
        UUID configId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();

        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain indexes.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId,
                new BigDecimal("0.5000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("1.00"), new BigDecimal("0.4000"), List.of(skillId)))));

        TranscriptEntryView entry = new TranscriptEntryView(
                UUID.randomUUID(), 1, q1, EntryOrigin.CORE,
                "Explain indexes.",
                "B-tree indexes speed up range and equality lookups.",
                List.of(skillId),
                Instant.now().minusSeconds(60), Instant.now(), Instant.now(), 30,
                false, false, false);
        when(assessmentQueryService.getTranscriptForAttempt(attemptId)).thenReturn(new ImmutableTranscriptView(
                attemptId, configId, inviteId, "v1", 1,
                Instant.now().minusSeconds(60), Instant.now(), List.of(entry)));

        TwoRunResponseJudge.ParsedRun verifiedRun1 = new TwoRunResponseJudge.ParsedRun(
                true, 3, "B-tree indexes speed up", "solid", null);
        when(judge.judge(any())).thenReturn(
                new TwoRunResponseJudge.TwoRunResult(
                        "evaluator-judge-v1", verifiedRun1, TwoRunResponseJudge.ParsedRun.failed()));

        try {
            runner.executeRun(runner.enqueue(new AttemptCompletedEvent(attemptId, configId, inviteId)));

            CandidateEvaluationSummaryEntity summary = summaryRepository.findByAttemptId(attemptId).orElseThrow();
            List<ResponseEvaluationEntity> responses = responseEvaluationRepository
                    .findBySummaryIdOrderByIdAsc(summary.getId());
            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().getReviewStatus()).isEqualTo("flagged");
            assertThat(responses.getFirst().getFlagReasons()).contains("JUDGE_PARSE_FAILED");
        } finally {
            transactionTemplate.executeWithoutResult(tx ->
                    eventPublisher.publishEvent(new AssessmentDeletedEvent(configId, "test-cleanup")));
        }
    }
}
