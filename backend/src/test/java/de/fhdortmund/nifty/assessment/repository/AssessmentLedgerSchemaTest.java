package de.fhdortmund.nifty.assessment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.assessment.service.AssessmentCleanupListener;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQ-AC-04 / REQ-AC-19 / Decision 4.4: V10__assessment.sql persists attempts, the ledger and BFI-10 rows, the
 * trigger blocks UPDATE and unflagged DELETE on conversation_entry, and AssessmentCleanupListener purges a config's
 * rows inside the deleting transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AssessmentCleanupListener.class)
class AssessmentLedgerSchemaTest extends PostgresContainerBase {

    @Autowired
    private AssessmentAttemptRepository attempts;
    @Autowired
    private ConversationEntryRepository entries;
    @Autowired
    private BfiResponseRepository bfiResponses;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private ApplicationEventPublisher events;

    @Test
    void persistsAttemptLedgerEntryWithJsonbSkillsAndBfiResponse() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID skillId = UUID.randomUUID();
        AssessmentAttemptEntity attempt = attempts.save(new AssessmentAttemptEntity(
                UUID.randomUUID(), UUID.randomUUID(), 1, 1, "IN_PROGRESS", now));
        ConversationEntryEntity entry = entries.save(new ConversationEntryEntity(
                attempt.getId(), 1, "interviewer", UUID.randomUUID(), "core", "Explain transactions.",
                List.of(skillId), now, now.plusSeconds(180), false, false, false));
        bfiResponses.save(new BfiResponseEntity(attempt.getId(), new int[] {1, 2, 3, 4, 5, 1, 2, 3, 4, 5},
                new BigDecimal("3.00"), new BigDecimal("2.50"), new BigDecimal("2.00"), new BigDecimal("3.50"),
                new BigDecimal("4.00"), now));
        entityManager.flush();
        entityManager.clear();

        List<ConversationEntryEntity> ledger = entries.findByAttemptIdOrderBySeqAsc(attempt.getId());
        assertThat(ledger).hasSize(1);
        assertThat(ledger.getFirst().getId()).isEqualTo(entry.getId());
        assertThat(ledger.getFirst().getTargetSkills()).containsExactly(skillId);
        assertThat(ledger.getFirst().getExpiresAt()).isEqualTo(now.plusSeconds(180));
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus()).isEqualTo("IN_PROGRESS");
        BfiResponseEntity bfi = bfiResponses.findById(attempt.getId()).orElseThrow();
        assertThat(bfi.getItems()).containsExactly(1, 2, 3, 4, 5, 1, 2, 3, 4, 5);
        assertThat(bfi.getOpenness()).isEqualByComparingTo("4.00");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void triggerBlocksLedgerUpdateAndUnflaggedDelete() {
        UUID configId = UUID.randomUUID();
        UUID attemptId = insertAttempt(configId);
        UUID entryId = insertEntry(attemptId, 1);
        try {
            assertThatThrownBy(() -> jdbc.update("UPDATE conversation_entry SET content = 'tampered' WHERE id = ?", entryId))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM conversation_entry WHERE id = ?", entryId))
                    .isInstanceOf(DataAccessException.class);
            // The FK cascade fires the row trigger too, so deleting the attempt cannot bypass the lock.
            assertThatThrownBy(() -> jdbc.update("DELETE FROM assessment_attempt WHERE id = ?", attemptId))
                    .isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT content FROM conversation_entry WHERE id = ?", String.class, entryId))
                    .isEqualTo("Original answer");
        } finally {
            purge(configId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanupListenerPurgesOnlyTheDeletedConfigInsideTheDeletingTransaction() {
        UUID deletedConfig = UUID.randomUUID();
        UUID keptConfig = UUID.randomUUID();
        UUID deletedAttempt = insertAttempt(deletedConfig);
        insertEntry(deletedAttempt, 1);
        insertBfi(deletedAttempt);
        UUID keptAttempt = insertAttempt(keptConfig);
        insertEntry(keptAttempt, 1);
        try {
            transactions.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL nifty.allow_purge = 'on'");
                events.publishEvent(new AssessmentDeletedEvent(deletedConfig, "professor"));
            });

            assertThat(count("assessment_attempt", "id", deletedAttempt)).isZero();
            assertThat(count("conversation_entry", "attempt_id", deletedAttempt)).isZero();
            assertThat(count("bfi_response", "attempt_id", deletedAttempt)).isZero();
            assertThat(count("conversation_entry", "attempt_id", keptAttempt)).isOne();
        } finally {
            purge(keptConfig);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanupListenerRefusesToRunOutsideTheDeletingTransaction() {
        assertThatThrownBy(() -> events.publishEvent(new AssessmentDeletedEvent(UUID.randomUUID(), "professor")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private long count(String table, String column, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Long.class, id);
    }

    private void purge(UUID configId) {
        transactions.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL nifty.allow_purge = 'on'");
            jdbc.update("DELETE FROM assessment_attempt WHERE config_id = ?", configId);
        });
    }

    private UUID insertAttempt(UUID configId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assessment_attempt (id, invite_id, config_id, attempt_no, status, started_at) "
                + "VALUES (?, ?, ?, 1, 'IN_PROGRESS', now())", id, UUID.randomUUID(), configId);
        return id;
    }

    private UUID insertEntry(UUID attemptId, int seq) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO conversation_entry (id, attempt_id, seq, author, origin, content, target_skills, "
                + "created_at) VALUES (?, ?, ?, 'candidate', 'core', 'Original answer', '[]'::jsonb, now())",
                id, attemptId, seq);
        return id;
    }

    private void insertBfi(UUID attemptId) {
        jdbc.update("INSERT INTO bfi_response (attempt_id, item_1, item_2, item_3, item_4, item_5, item_6, item_7, "
                + "item_8, item_9, item_10, conscientiousness, agreeableness, neuroticism, extraversion, openness, "
                + "submitted_at) VALUES (?, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3.00, 3.00, 3.00, 3.00, 3.00, now())",
                attemptId);
    }
}
