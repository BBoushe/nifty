package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.shared.config.AttemptCompletedEvent;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * REQ-EP-05 / Decisions 1.12, 2.24: starts the evaluator strictly AFTER_COMMIT so rolled-back attempt
 * completions never trigger LLM judge calls. The QUEUED run is persisted before dispatch, so a rejected
 * executor submission only delays the work: the retry pass dispatches the run again.
 * shortcut: a crash between the assessment commit and {@code enqueue} still loses the event. Upgrade: insert the
 * QUEUED rows in BEFORE_COMMIT, or reconcile completed attempts against the assessment slice at start-up.
 */
@Component
public class EvaluationEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(EvaluationEventListener.class);

    private final AsyncEvaluationRunner asyncEvaluationRunner;

    EvaluationEventListener(AsyncEvaluationRunner asyncEvaluationRunner) {
        this.asyncEvaluationRunner = asyncEvaluationRunner;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttemptCompleted(AttemptCompletedEvent event) {
        UUID runId = asyncEvaluationRunner.enqueue(event);
        if (runId == null) {
            return;
        }
        try {
            asyncEvaluationRunner.runAsync(runId);
        } catch (TaskRejectedException ex) {
            LOG.warn("Evaluation executor is full; run {} stays QUEUED for the retry pass", runId);
        }
    }
}
