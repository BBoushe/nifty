package de.fhdortmund.nifty.auth.service;

import de.fhdortmund.nifty.auth.repository.ProfessorAccountEntity;
import de.fhdortmund.nifty.auth.repository.ProfessorAccountRepository;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Decision 1.24 / REQ-AA-07: creates one professor account from the environment when no account has that email.
 * Never changes an existing account and never logs the password.
 */
@Component
public class ProfessorBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ProfessorBootstrap.class);
    private static final int BCRYPT_MAX_BYTES = 72;

    private final ProfessorAccountRepository repository;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    ProfessorBootstrap(ProfessorAccountRepository repository, PasswordEncoder encoder,
            @Value("${nifty.bootstrap.professor.email:}") String email,
            @Value("${nifty.bootstrap.professor.password:}") String password) {
        this.repository = repository;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        // REQ-AA-15: bcrypt reads at most 72 bytes. The message names the limit and never the password.
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new IllegalStateException("nifty.bootstrap.professor.password is longer than the bcrypt limit of "
                    + BCRYPT_MAX_BYTES + " bytes (UTF-8). Use a shorter password.");
        }
        String normalized = ProfessorAccountEntity.normalizeEmail(email);
        if (repository.existsByEmail(normalized)) {
            return;
        }
        repository.save(new ProfessorAccountEntity(normalized, encoder.encode(password)));
        LOG.info("Created the bootstrap professor account");
    }
}
