package de.fhdortmund.nifty.assessment.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * REQ-AC-24: interviewer streams run on a bounded pool. A full pool and queue rejects the task (default AbortPolicy,
 * {@code TaskRejectedException}), and the error boundary answers 503. Boot's own MVC executor is off because
 * {@code EvaluationAsyncConfig} already defines an {@code Executor}, so without this MVC would start one unbounded
 * thread per stream.
 */
@Configuration
class StreamExecutorConfig {

    static final String STREAM_EXECUTOR = "streamExecutor";

    @Bean(STREAM_EXECUTOR)
    ThreadPoolTaskExecutor streamExecutor(@Value("${nifty.assessment.stream.core-size}") int coreSize,
            @Value("${nifty.assessment.stream.max-size}") int maxSize,
            @Value("${nifty.assessment.stream.queue-capacity}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("nifty-stream-");
        return executor;
    }

    @Bean
    WebMvcConfigurer streamAsyncSupport(@Qualifier(STREAM_EXECUTOR) ThreadPoolTaskExecutor streamExecutor) {
        return new WebMvcConfigurer() {
            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                configurer.setTaskExecutor(streamExecutor);
            }
        };
    }
}
