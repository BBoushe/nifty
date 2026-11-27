package de.fhdortmund.nifty.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.fhdortmund.nifty.PostgresContainerBase;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/** REQ-AL-02: no code path can update or delete an audit_log row. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AuditLogImmutabilityTest extends PostgresContainerBase {

    @Autowired
    private AuditLogRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void changedEntityIsNotWritten() {
        Long id = repository.save(new AuditLogEntity("prof-1", "PROFESSOR", "RESPONSE_EVALUATION",
                "00000000-0000-0000-0000-000000000001", "SCORE_OVERRIDDEN", "2", "3", "Clear explanation")).getId();
        entityManager.flush();
        entityManager.clear();

        AuditLogEntity loaded = repository.findById(id).orElseThrow();
        ReflectionTestUtils.setField(loaded, "reason", "Tampered");
        entityManager.flush();

        String stored = jdbc.queryForObject("SELECT reason FROM audit_log WHERE id = ?", String.class, id);
        assertThat(stored).isEqualTo("Clear explanation");
    }

    @Test
    void repositoryOffersNoDeleteOrUpdateMethod() {
        for (Method method : AuditLogRepository.class.getMethods()) {
            assertThat(method.getName()).as(method.toString()).doesNotStartWith("delete").doesNotStartWith("update");
            assertThat(method.isAnnotationPresent(Modifying.class)).as(method.toString()).isFalse();
        }
    }
}
