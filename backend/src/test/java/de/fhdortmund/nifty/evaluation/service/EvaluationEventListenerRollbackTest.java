package de.fhdortmund.nifty.evaluation.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.shared.config.AttemptCompletedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQ-EP-05 / Decision 1.12: verifies EvaluationEventListener triggers ONLY AFTER_COMMIT
 * and never starts when the publishing transaction rolls back.
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

        verify(asyncEvaluationRunner, never()).runAsync(event);
    }

    @Test
    void committedTransactionTriggersAsyncEvaluator() {
        AttemptCompletedEvent event = new AttemptCompletedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        verify(asyncEvaluationRunner, times(1)).runAsync(event);
    }
}
