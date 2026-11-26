package de.fhdortmund.nifty.auth.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** One professor account (V2). Holds only a password hash, never the password. */
@Entity
@Table(name = "professor_account")
public class ProfessorAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // The database sets created_at (V2 DEFAULT CURRENT_TIMESTAMP).
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ProfessorAccountEntity() {
    }

    public ProfessorAccountEntity(String email, String passwordHash) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
    }

    /** The one email form that the database stores and every lookup uses. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
