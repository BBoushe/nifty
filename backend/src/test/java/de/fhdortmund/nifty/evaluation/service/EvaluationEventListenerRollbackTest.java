package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.shared.config.AttemptCompletedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQ-EP-05 / Decisions 1.12, 2.24: verifies EvaluationEventListener triggers ONLY AFTER_COMMIT,
 * never starts when the publishing transaction rolls back, and survives a full executor queue.
 */
@SpringBootTest
@ActiveProfiles("test")
class EvaluationEventListenerRollbackTest extends PostgresContainerBase {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @MockitoBean
    private AsyncEvaluationRunner asyncEvaluationRunner;

    @Test
    void rolledBackTransactionDoesNotTriggerEvaluator() {
        AttemptCompletedEvent event = new AttemptCompletedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        verify(asyncEvaluationRunner, never()).enqueue(event);
    }

    @Test
    void committedTransactionEnqueuesThenDispatchesAsyncEvaluator() {
        AttemptCompletedEvent event = new AttemptCompletedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID runId = UUID.randomUUID();
        when(asyncEvaluationRunner.enqueue(event)).thenReturn(runId);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        verify(asyncEvaluationRunner, times(1)).enqueue(event);
        verify(asyncEvaluationRunner, times(1)).runAsync(runId);
    }

    @Test
    void duplicateEventIsNotDispatched() {
        AttemptCompletedEvent event = new AttemptCompletedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(asyncEvaluationRunner.enqueue(event)).thenReturn(null);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        verify(asyncEvaluationRunner, never()).runAsync(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectedExecutorSubmissionDoesNotLoseOrThrow() {
        AttemptCompletedEvent event = new AttemptCompletedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID runId = UUID.randomUUID();
        when(asyncEvaluationRunner.enqueue(event)).thenReturn(runId);
        doThrow(new TaskRejectedException("queue full")).when(asyncEvaluationRunner).runAsync(runId);

        // The run is already persisted as QUEUED; the retry pass dispatches it again.
        assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event)))
                .doesNotThrowAnyException();
        verify(asyncEvaluationRunner, times(1)).enqueue(event);
    }
}
