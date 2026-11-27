package de.fhdortmund.nifty.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.auth.repository.ProfessorAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/** REQ-AA-07 (Decision 1.24): the env bootstrap creates one professor account and never changes it. */
@SpringBootTest(properties = {
        "nifty.bootstrap.professor.email=Bootstrap.Prof@Example.test",
        "nifty.bootstrap.professor.password=synthetic-bootstrap-pass"
})
@ActiveProfiles("test")
class ProfessorBootstrapTest extends PostgresContainerBase {

    private static final String EMAIL = "bootstrap.prof@example.test";

    @Autowired
    private ProfessorBootstrap bootstrap;
    @Autowired
    private ProfessorAccountRepository repository;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void startupCreatesOneAccountWithLowerCaseEmailAndOnlyAHash() {
        assertThat(countByEmail(EMAIL)).isEqualTo(1);
        String hash = hashOf(EMAIL);
        assertThat(hash).isNotEqualTo("synthetic-bootstrap-pass").startsWith("{");
        assertThat(encoder.matches("synthetic-bootstrap-pass", hash)).isTrue();
    }

    @Test
    void secondRunChangesNothing() throws Exception {
        String before = hashOf(EMAIL);

        bootstrap.run(null);

        assertThat(countByEmail(EMAIL)).isEqualTo(1);
        assertThat(hashOf(EMAIL)).isEqualTo(before);
    }

    // REQ-AA-15: bcrypt reads 72 bytes only. A longer password would be cut off silently, so startup stops.
    @Test
    void passwordOf80BytesStopsStartupNamesTheLimitAndNeverTheSecret() {
        String password = "synthetic-password-".repeat(5).substring(0, 80);

        assertThatThrownBy(() -> new ProfessorBootstrap(repository, encoder, "long.pw@example.test", password)
                .run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("72")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(password));
        assertThat(countByEmail("long.pw@example.test")).isZero();
    }

    // The limit counts UTF-8 bytes, not characters: 37 times "ä" is 37 characters and 74 bytes.
    @Test
    void theLimitCountsUtf8BytesAndExactly72BytesStillWorks() throws Exception {
        assertThatThrownBy(() -> new ProfessorBootstrap(repository, encoder, "bytes.pw@example.test", "ä".repeat(37))
                .run(null))
                .isInstanceOf(IllegalStateException.class);

        new ProfessorBootstrap(repository, encoder, "edge.pw@example.test", "ä".repeat(36)).run(null);

        assertThat(countByEmail("edge.pw@example.test")).isEqualTo(1);
    }

    @Test
    void missingPropertiesCreateNoAccount() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM professor_account", Long.class);

        new ProfessorBootstrap(repository, encoder, "", "").run(null);
        new ProfessorBootstrap(repository, encoder, "only.email@example.test", "").run(null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM professor_account", Long.class)).isEqualTo(before);
    }

    private long countByEmail(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM professor_account WHERE email = ?", Long.class, email);
    }

    private String hashOf(String email) {
        return jdbc.queryForObject("SELECT password_hash FROM professor_account WHERE email = ?", String.class, email);
    }
}
