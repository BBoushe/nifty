package de.fhdortmund.nifty.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

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
