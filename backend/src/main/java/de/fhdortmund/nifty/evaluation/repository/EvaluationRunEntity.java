package de.fhdortmund.nifty.evaluation.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Async evaluation job lifecycle (REQ-EP-05, REQ-EP-09).
 */
@Entity
@Table(name = "evaluation_run")
public class EvaluationRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "config_id", nullable = false)
    private UUID configId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "formula_version_id", nullable = false)
    private FormulaVersionEntity formulaVersion;

    @Column(name = "context_template_version", length = 64)
    private String contextTemplateVersion;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected EvaluationRunEntity() {
    }

    public EvaluationRunEntity(
            UUID attemptId,
            UUID configId,
            FormulaVersionEntity formulaVersion,
            String contextTemplateVersion,
            String status,
            String idempotencyKey) {
        this.attemptId = attemptId;
        this.configId = configId;
        this.formulaVersion = formulaVersion;
        this.contextTemplateVersion = contextTemplateVersion;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        // attempt_count counts LLM tries: a QUEUED run has made none yet.
        this.attemptCount = "QUEUED".equals(status) ? 0 : 1;
        this.startedAt = Instant.now();
        this.updatedAt = this.startedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public UUID getConfigId() {
        return configId;
    }

    public FormulaVersionEntity getFormulaVersion() {
        return formulaVersion;
    }

    public String getContextTemplateVersion() {
        return contextTemplateVersion;
    }

    public String getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Re-points a not yet evaluated run at the newest formula version (REQ-FE-02). */
    public void useFormulaVersion(FormulaVersionEntity formulaVersion) {
        this.formulaVersion = formulaVersion;
    }

    public void markCompleted(String templateVersion, Instant completedAt) {
        if (templateVersion != null) {
            this.contextTemplateVersion = templateVersion;
        }
        this.status = "COMPLETED";
        this.errorMessage = null;
        this.completedAt = completedAt;
        this.updatedAt = completedAt;
    }

    /** REQ-EP-12: professor re-run. The scheduler claims the run again with a fresh set of tries. */
    public void requeue(Instant now) {
        this.status = "QUEUED";
        this.attemptCount = 0;
        this.errorMessage = null;
        this.completedAt = null;
        this.updatedAt = now;
    }

    public void markFailed(String errorMessage, Instant completedAt) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.completedAt = completedAt;
        this.updatedAt = completedAt;
    }
}
