package de.fhdortmund.nifty;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** REQ-BF-01: Flyway V1 runs on real PostgreSQL, not on an embedded database. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class FlywayBaselineTest extends PostgresContainerBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayV1CreatesAuditLogOnPostgres() {
        Boolean exists = jdbc.queryForObject("SELECT to_regclass('public.audit_log') IS NOT NULL", Boolean.class);
        assertThat(exists).isTrue();
    }
}
