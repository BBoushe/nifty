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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-attempt evaluation summary keyed by {@code UNIQUE (attempt_id)} (Decisions 2.10, 2.13).
 * Stores enum literals as strings so {@code repository} never depends on {@code service}.
 */
@Entity
@Table(name = "candidate_evaluation_summary")
public class CandidateEvaluationSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "attempt_id", nullable = false, unique = true)
    private UUID attemptId;

    @Column(name = "config_id", nullable = false)
    private UUID configId;

    @Column(name = "invite_id", nullable = false)
    private UUID inviteId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "formula_version_id", nullable = false)
    private FormulaVersionEntity formulaVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "latest_run_id", nullable = false)
    private EvaluationRunEntity latestRun;

    @Column(name = "evaluation_status", nullable = false, length = 16)
    private String evaluationStatus;

    @Column(name = "fit_score", precision = 4, scale = 2)
    private BigDecimal fitScore;

    @Column(name = "coverage", nullable = false, precision = 5, scale = 4)
    private BigDecimal coverage;

    @Column(name = "flagged_count", nullable = false)
    private int flaggedCount;

    @Column(name = "release_status", nullable = false, length = 16)
    private String releaseStatus;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CandidateEvaluationSummaryEntity() {
    }

    public CandidateEvaluationSummaryEntity(
            UUID attemptId,
            UUID configId,
            UUID inviteId,
            int attemptNo,
            FormulaVersionEntity formulaVersion,
            EvaluationRunEntity latestRun,
            String evaluationStatus,
            BigDecimal fitScore,
            BigDecimal coverage,
            int flaggedCount,
            String releaseStatus,
            Instant releasedAt) {
        this.attemptId = attemptId;
        this.configId = configId;
        this.inviteId = inviteId;
        this.attemptNo = attemptNo;
        this.formulaVersion = formulaVersion;
        this.latestRun = latestRun;
        this.evaluationStatus = evaluationStatus;
        this.fitScore = fitScore;
        this.coverage = coverage;
        this.flaggedCount = flaggedCount;
        this.releaseStatus = releaseStatus;
        this.releasedAt = releasedAt;
        this.createdAt = Instant.now();
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

    public UUID getInviteId() {
        return inviteId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public FormulaVersionEntity getFormulaVersion() {
        return formulaVersion;
    }

    public EvaluationRunEntity getLatestRun() {
        return latestRun;
    }

    public String getEvaluationStatus() {
        return evaluationStatus;
    }

    public BigDecimal getFitScore() {
        return fitScore;
    }

    public BigDecimal getCoverage() {
        return coverage;
    }

    public int getFlaggedCount() {
        return flaggedCount;
    }

    public String getReleaseStatus() {
        return releaseStatus;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updateScores(
            FormulaVersionEntity formulaVersion,
            BigDecimal fitScore,
            BigDecimal coverage,
            int flaggedCount,
            String releaseStatus,
            Instant releasedAt) {
        this.formulaVersion = formulaVersion;
        this.fitScore = fitScore;
        this.coverage = coverage;
        this.flaggedCount = flaggedCount;
        this.releaseStatus = releaseStatus;
        this.releasedAt = releasedAt;
    }

    public void markReleased(String releaseStatus, Instant releasedAt) {
        this.releaseStatus = releaseStatus;
        this.releasedAt = releasedAt;
        this.flaggedCount = 0;
    }

    /** Decision 2.13: a summary exists from enqueue on, so a newer attempt supersedes older ones at once. */
    public static CandidateEvaluationSummaryEntity queued(
            UUID attemptId,
            UUID configId,
            UUID inviteId,
            int attemptNo,
            FormulaVersionEntity formulaVersion,
            EvaluationRunEntity run) {
        return new CandidateEvaluationSummaryEntity(
                attemptId, configId, inviteId, attemptNo, formulaVersion, run, "QUEUED",
                null, BigDecimal.ZERO.setScale(4), 0, "pending", null);
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(evaluationStatus);
    }

    /** QUEUED, RUNNING, or FAILED: mirrors the run so the matrix can show the attempt as waiting. */
    public void markEvaluationStatus(String evaluationStatus) {
        this.evaluationStatus = evaluationStatus;
    }

    public void markCompleted(
            FormulaVersionEntity formulaVersion,
            BigDecimal fitScore,
            BigDecimal coverage,
            int flaggedCount,
            String releaseStatus,
            Instant releasedAt) {
        this.evaluationStatus = "COMPLETED";
        updateScores(formulaVersion, fitScore, coverage, flaggedCount, releaseStatus, releasedAt);
    }
}
