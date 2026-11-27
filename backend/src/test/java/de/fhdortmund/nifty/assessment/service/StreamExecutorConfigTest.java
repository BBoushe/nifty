package de.fhdortmund.nifty.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.fhdortmund.nifty.PostgresContainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/** REQ-AC-24: interviewer streams run on a bounded pool, and Spring MVC really uses it. */
@SpringBootTest
@ActiveProfiles("test")
class StreamExecutorConfigTest extends PostgresContainerBase {

    @Autowired
    @Qualifier("streamExecutor")
    private ThreadPoolTaskExecutor streams;
    @Autowired
    private RequestMappingHandlerAdapter mvc;

    @Test
    void streamExecutorIsABoundedThreadPoolTaskExecutor() {
        assertThat(streams.getCorePoolSize()).isEqualTo(8);
        assertThat(streams.getMaxPoolSize()).isEqualTo(32);
        assertThat(streams.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(100);
    }

    @Test
    void mvcAsyncRequestsRunOnTheStreamExecutor() {
        assertThat(ReflectionTestUtils.getField(mvc, "taskExecutor")).isSameAs(streams);
    }
}
