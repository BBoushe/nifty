package de.fhdortmund.nifty.auth.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Personal candidate invite link bound to one assessment config (V4, REQ-AA-03, Decision 1.17). */
@Entity
@Table(name = "invite")
public class InviteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "config_id", nullable = false, updatable = false)
    private UUID configId;

    @Column(name = "candidate_ref", nullable = false, updatable = false, length = 256)
    private String candidateRef;

    @JdbcTypeCode(SqlTypes.CHAR)
    // Decision 2.34: updatable, because a re-invite rotates the token on the same row.
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InviteEntity() {
    }

    public InviteEntity(UUID configId, String candidateRef, String tokenHash, UUID createdBy) {
        this.configId = configId;
        this.candidateRef = candidateRef;
        this.tokenHash = tokenHash;
        this.revoked = false;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getConfigId() {
        return configId;
    }

    public String getCandidateRef() {
        return candidateRef;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void revoke() {
        this.revoked = true;
    }

    /** Decision 2.34: a new token for a revoked invite. Old links and sessions stop working. */
    public void reissue(String newTokenHash) {
        this.tokenHash = newTokenHash;
        this.revoked = false;
    }
}
