package de.fhdortmund.nifty.audit.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * One row of the V1 {@code audit_log} table. REQ-AL-02: {@code @Immutable}, so Hibernate never writes an UPDATE.
 * V1 keeps {@code id BIGSERIAL} and stores {@code entity_id} as UUID text (contract pack §1 rule 4).
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "actor_role", nullable = false, length = 32)
    private String actorRole;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 128)
    private String entityId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    // The database sets created_at (V1 DEFAULT CURRENT_TIMESTAMP).
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AuditLogEntity() {
    }

    public AuditLogEntity(String actorId, String actorRole, String entityType, String entityId, String action,
            String oldValue, String newValue, String reason) {
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public String getReason() {
        return reason;
    }
}
