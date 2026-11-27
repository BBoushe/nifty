package de.fhdortmund.nifty.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.PostgresContainerBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** REQ-AL-01: AuditService.record(...) is the single writer of audit_log rows. */
@SpringBootTest
@ActiveProfiles("test")
class AuditServiceImplTest extends PostgresContainerBase {

    @Autowired
    private AuditService audit;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void recordStoresOneRowWithEveryFieldAndDatabaseTimestamp() {
        UUID entityId = UUID.randomUUID();

        audit.record("prof-1", AuditActorRole.PROFESSOR, "RESPONSE_EVALUATION", entityId, "SCORE_OVERRIDDEN",
                "2", "3", "Clear explanation");

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM audit_log WHERE entity_id = ?", entityId.toString());
        assertThat(row)
                .containsEntry("actor_id", "prof-1")
                .containsEntry("actor_role", "PROFESSOR")
                .containsEntry("entity_type", "RESPONSE_EVALUATION")
                .containsEntry("entity_id", entityId.toString())
                .containsEntry("action", "SCORE_OVERRIDDEN")
                .containsEntry("old_value", "2")
                .containsEntry("new_value", "3")
                .containsEntry("reason", "Clear explanation");
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("id")).isNotNull();
    }

    @Test
    void recordAcceptsNullOptionalValues() {
        UUID entityId = UUID.randomUUID();

        audit.record("system", AuditActorRole.SYSTEM, "ATTEMPT", entityId, "PROMPT_INJECTION_DETECTED",
                null, null, null);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM audit_log WHERE entity_id = ?", entityId.toString());
        assertThat(row.get("old_value")).isNull();
        assertThat(row.get("new_value")).isNull();
        assertThat(row.get("reason")).isNull();
    }

    @Test
    void recordRejectsNullAction() {
        UUID entityId = UUID.randomUUID();

        assertThatThrownBy(() -> audit.record("prof-1", AuditActorRole.PROFESSOR, "ASSESSMENT_CONFIG", entityId,
                null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ?", Long.class,
                entityId.toString())).isZero();
    }

    @Test
    void recordRejectsEveryOtherMissingRequiredField() {
        UUID id = UUID.randomUUID();
        AuditActorRole role = AuditActorRole.PROFESSOR;

        assertThatThrownBy(() -> audit.record(null, role, "T", id, "A", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> audit.record("p", null, "T", id, "A", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> audit.record("p", role, null, id, "A", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> audit.record("p", role, "T", null, "A", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
