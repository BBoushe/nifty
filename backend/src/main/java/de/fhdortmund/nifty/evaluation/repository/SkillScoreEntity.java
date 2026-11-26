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
import java.util.UUID;

/**
 * Aggregated per-skill score for an attempt summary. Missing verified evidence is stored as
 * {@code status = 'N/A'} and {@code score = null} (enforced by {@code chk_na_score_null}).
 */
@Entity
@Table(name = "skill_score")
public class SkillScoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "summary_id", nullable = false)
    private CandidateEvaluationSummaryEntity summary;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "formula_version_id", nullable = false)
    private FormulaVersionEntity formulaVersion;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "score", precision = 4, scale = 2)
    private BigDecimal score;

    @Column(name = "avg_confidence", precision = 5, scale = 4)
    private BigDecimal avgConfidence;

    @Column(name = "verified_evidence_count", nullable = false)
    private int verifiedEvidenceCount;

    @Column(name = "review_status", nullable = false, length = 16)
    private String reviewStatus;

    protected SkillScoreEntity() {
    }

    public SkillScoreEntity(
            CandidateEvaluationSummaryEntity summary,
            FormulaVersionEntity formulaVersion,
            UUID skillId,
            String status,
            BigDecimal score,
            BigDecimal avgConfidence,
            int verifiedEvidenceCount,
            String reviewStatus) {
        this.summary = summary;
        this.formulaVersion = formulaVersion;
        this.skillId = skillId;
        this.status = status;
        this.score = score;
        this.avgConfidence = avgConfidence;
        this.verifiedEvidenceCount = verifiedEvidenceCount;
        this.reviewStatus = reviewStatus;
    }

    public UUID getId() {
        return id;
    }

    public CandidateEvaluationSummaryEntity getSummary() {
        return summary;
    }

    public FormulaVersionEntity getFormulaVersion() {
        return formulaVersion;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getScore() {
        return score;
    }

    public BigDecimal getAvgConfidence() {
        return avgConfidence;
    }

    public int getVerifiedEvidenceCount() {
        return verifiedEvidenceCount;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }
}
