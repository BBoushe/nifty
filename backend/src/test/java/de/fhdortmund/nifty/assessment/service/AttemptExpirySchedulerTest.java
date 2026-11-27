package de.fhdortmund.nifty.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.context.service.ContextCreationService;
import de.fhdortmund.nifty.shared.config.AttemptCompletedEvent;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.event.TransactionalEventListener;

/** REQ-AC-21, Decision 2.33: the sweep completes an attempt that nobody finished in the BFI-10 step. */
@SpringBootTest
@ActiveProfiles("test")
class AttemptExpirySchedulerTest extends PostgresContainerBase {

    @Autowired
    private AttemptExpiryScheduler scheduler;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CompletedEventProbe completedEvents;

    // Mocked like AssessmentSessionServiceIntegrationTest: the evaluator listener reads the studio after commit.
    @MockitoBean
    private StudioQueryService studio;
    @MockitoBean
    private ContextCreationService context;
    @MockitoBean
    private AuthContextService auth;
    @MockitoBean
    private ChatModel chatModel;

    private UUID configId;

    @BeforeEach
    void setUp() {
        configId = UUID.randomUUID();
        completedEvents.events.clear();
    }

    @Test
    void attemptPastItsBfi10WindowCompletesWithoutAProfileAndPublishesTheEvent() {
        UUID abandoned = insertAttempt("BFI10", Instant.now().minusSeconds(7200));
        insertAnswer(abandoned, Instant.now().minusSeconds(7100));
        UUID fresh = insertAttempt("BFI10", Instant.now().minusSeconds(600));
        insertAnswer(fresh, Instant.now().minusSeconds(60));

        scheduler.sweep();

        assertThat(status(abandoned)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM assessment_attempt WHERE id = ?",
                Boolean.class, abandoned)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bfi_response WHERE attempt_id = ?", Long.class,
                abandoned)).isZero();
        assertThat(completedEvents.events).extracting(AttemptCompletedEvent::attemptId).containsExactly(abandoned);
        assertThat(status(fresh)).isEqualTo("BFI10");

        scheduler.sweep();

        assertThat(completedEvents.events).hasSize(1); // a second sweep never publishes the event again
    }

    @Test
    void softSkillsOnlyAttemptThatExpiresBeforeTheBfi10FormAlsoCompletes() {
        UUID attemptId = insertAttempt("BFI10", Instant.now().minusSeconds(7200)); // no ledger rows at all

        scheduler.sweep();

        assertThat(status(attemptId)).isEqualTo("COMPLETED");
        assertThat(completedEvents.events).extracting(AttemptCompletedEvent::attemptId).containsExactly(attemptId);
    }

    private String status(UUID attemptId) {
        return jdbc.queryForObject("SELECT status FROM assessment_attempt WHERE id = ?", String.class, attemptId);
    }

    private UUID insertAttempt(String status, Instant startedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assessment_attempt (id, invite_id, config_id, attempt_no, status, started_at) "
                + "VALUES (?, ?, ?, 1, ?, ?)", id, UUID.randomUUID(), configId, status, Timestamp.from(startedAt));
        return id;
    }

    /** The last core answer: the moment the BFI-10 step opened. */
    private void insertAnswer(UUID attemptId, Instant createdAt) {
        jdbc.update("INSERT INTO conversation_entry (id, attempt_id, seq, author, core_question_id, origin, content, "
                + "target_skills, created_at) VALUES (?, ?, 1, 'candidate', ?, 'core', 'My answer', '[]'::jsonb, ?)",
                UUID.randomUUID(), attemptId, UUID.randomUUID(), Timestamp.from(createdAt));
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        CompletedEventProbe completedEventProbe() {
            return new CompletedEventProbe();
        }
    }

    static class CompletedEventProbe {
        final List<AttemptCompletedEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener
        void on(AttemptCompletedEvent event) {
            events.add(event);
        }
    }
}
