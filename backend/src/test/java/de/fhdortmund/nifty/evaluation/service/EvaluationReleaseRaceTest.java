package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunEntity;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunRepository;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionRepository;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQ-SM-03 / Decision 2.10: two concurrent releases of one attempt write exactly one `ATTEMPT_RELEASED` audit
 * row and one gets 409. Real committed transactions on PostgreSQL; the audit mock holds each caller for up to a
 * second so that, without a row lock, both callers would read an unreleased attempt before either commits.
 */
@SpringBootTest
@ActiveProfiles("test")
class EvaluationReleaseRaceTest extends PostgresContainerBase {

    private final UUID professor = UUID.randomUUID();
    private final UUID configId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();

    @Autowired
    private EvaluationReviewService service;
    @Autowired
    private FormulaVersionRepository formulaVersionRepository;
    @Autowired
    private EvaluationRunRepository evaluationRunRepository;
    @Autowired
    private CandidateEvaluationSummaryRepository summaryRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private StudioQueryService studio;
    @MockitoBean
    private AuthContextService authContext;
    @MockitoBean
    private AuditService audit;

    @Test
    void concurrentReleasesWriteOneAuditRowAndOneGets409() throws Exception {
        when(authContext.currentProfessorId()).thenReturn(professor);
        when(studio.ownedBy(configId, professor)).thenReturn(true);
        when(studio.evaluatorView(configId)).thenReturn(new EvaluatorView(configId, List.of(), List.of()));
        transactionTemplate.executeWithoutResult(tx -> {
            FormulaVersionEntity formula = formulaVersionRepository.save(new FormulaVersionEntity(
                    configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), professor));
            EvaluationRunEntity run = evaluationRunRepository.save(new EvaluationRunEntity(
                    attemptId, configId, formula, "v1", "COMPLETED", attemptId + ":v1"));
            summaryRepository.save(new CandidateEvaluationSummaryEntity(
                    attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                    new BigDecimal("3.00"), BigDecimal.ONE, 0, "pending", null));
        });

        CountDownLatch bothInAudit = new CountDownLatch(2);
        doAnswer(invocation -> {
            bothInAudit.countDown();
            bothInAudit.await(1, TimeUnit.SECONDS);
            return null;
        }).when(audit).record(any(), any(), any(), any(), any(), any(), any(), any());

        Callable<Object> release = () -> {
            try {
                return service.releaseAttempt(attemptId).releaseStatus();
            } catch (DomainException ex) {
                return ex.getStatus();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(release);
            Future<Object> second = pool.submit(release);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(ReleaseStatus.APPROVED, HttpStatus.CONFLICT);
        } finally {
            pool.shutdownNow();
            transactionTemplate.executeWithoutResult(tx ->
                    eventPublisher.publishEvent(new AssessmentDeletedEvent(configId, "test-cleanup")));
        }
        verify(audit, times(1)).record(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
