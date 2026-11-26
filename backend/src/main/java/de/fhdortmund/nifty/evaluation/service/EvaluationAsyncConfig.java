package de.fhdortmund.nifty.evaluation.service;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded async executor for post-commit evaluation runs (00-parallel-waves-and-contracts.md §1, REQ-EP-05).
 * Decision 2.28: a full queue rejects the submission (default AbortPolicy). The run is already persisted as QUEUED,
 * so the {@link EvaluationRetryScheduler} pass picks it up later and no work is lost.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class EvaluationAsyncConfig {

    public static final String EVALUATION_EXECUTOR = "evaluationTaskExecutor";

    @Bean(name = EVALUATION_EXECUTOR)
    public Executor evaluationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("nifty-eval-");
        executor.initialize();
        return executor;
    }
}
