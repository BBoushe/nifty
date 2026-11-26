package de.fhdortmund.nifty.evaluation.service;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Decision 2.24 / REQ-EP-09: periodic pass that dispatches QUEUED, FAILED, and stale RUNNING runs again.
 * The runner bounds the tries and claims each run atomically, so this pass is safe to overlap.
 * shortcut: single-node fixed delay; upgrade to a shared scheduler lock if the backend runs on many nodes.
 */
@Component
@ConditionalOnProperty(name = "nifty.evaluation.retry.enabled", havingValue = "true", matchIfMissing = true)
public class EvaluationRetryScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(EvaluationRetryScheduler.class);

    private final AsyncEvaluationRunner runner;

    EvaluationRetryScheduler(AsyncEvaluationRunner runner) {
        this.runner = runner;
    }

    @Scheduled(
            fixedDelayString = "${nifty.evaluation.retry.fixed-delay:PT1M}",
            initialDelayString = "${nifty.evaluation.retry.fixed-delay:PT1M}")
    public void retryPass() {
        List<UUID> runIds = runner.retryableRunIds();
        for (int i = 0; i < runIds.size(); i++) {
            try {
                runner.runAsync(runIds.get(i));
            } catch (TaskRejectedException ex) {
                LOG.warn("Evaluation executor is full; {} runs wait for the next pass", runIds.size() - i);
                return;
            }
        }
    }
}
