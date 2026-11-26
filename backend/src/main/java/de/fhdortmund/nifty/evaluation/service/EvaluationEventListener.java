package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.shared.config.AttemptCompletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * REQ-EP-05 / Decision 1.12: starts the asynchronous evaluator strictly AFTER_COMMIT
 * so rolled-back attempt completions never trigger LLM judge calls.
 */
@Component
public class EvaluationEventListener {

    private final AsyncEvaluationRunner asyncEvaluationRunner;

    EvaluationEventListener(AsyncEvaluationRunner asyncEvaluationRunner) {
        this.asyncEvaluationRunner = asyncEvaluationRunner;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttemptCompleted(AttemptCompletedEvent event) {
        asyncEvaluationRunner.runAsync(event);
    }
}
