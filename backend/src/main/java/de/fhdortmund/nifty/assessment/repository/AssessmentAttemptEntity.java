package de.fhdortmund.nifty.assessment.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One candidate attempt. Only {@code status} and {@code completedAt} change (IN_PROGRESS -> BFI10 -> COMPLETED). */
@Entity
@Table(name = "assessment_attempt")
public class AssessmentAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invite_id", nullable = false, updatable = false)
    private UUID inviteId;

    @Column(name = "config_id", nullable = false, updatable = false)
    private UUID configId;

    @Column(name = "config_version", nullable = false, updatable = false)
    private int configVersion;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private int attemptNo;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "started_at", updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AssessmentAttemptEntity() {
    }

    public AssessmentAttemptEntity(UUID inviteId, UUID configId, int configVersion, int attemptNo, String status,
            Instant startedAt) {
        this.inviteId = inviteId;
        this.configId = configId;
        this.configVersion = configVersion;
        this.attemptNo = attemptNo;
        this.status = status;
        this.startedAt = startedAt;
    }

    public void moveTo(String newStatus, Instant completedAt) {
        this.status = newStatus;
        this.completedAt = completedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInviteId() {
        return inviteId;
    }

    public UUID getConfigId() {
        return configId;
    }

    public int getConfigVersion() {
        return configVersion;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
