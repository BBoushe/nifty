package de.fhdortmund.nifty;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * REQ-BF-01: base class of every Spring test that needs a database ({@code @SpringBootTest},
 * {@code @DataJpaTest}). H2 cannot run JSONB or the immutability triggers.
 */
public abstract class PostgresContainerBase {

    // backend-foundation design §2: one container per JVM, started once. A per-class @Container would restart on a new
    // port and break cached Spring contexts. Testcontainers' Ryuk removes it when the JVM exits.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
