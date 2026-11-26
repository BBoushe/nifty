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
 * Per-(entry, skill) evaluation result storing both judge runs, verified quote, deterministic
 * confidence, flag reasons, review state, and immutable original AI level (REQ-EP-01..08, REQ-SM-01..03).
 */
@Entity
@Table(name = "response_evaluation")
public class ResponseEvaluationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_run_id", nullable = false)
    private EvaluationRunEntity evaluationRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "summary_id", nullable = false)
    private CandidateEvaluationSummaryEntity summary;

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "core_question_id", nullable = false)
    private UUID coreQuestionId;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @Column(name = "question_origin", nullable = false, length = 16)
    private String questionOrigin;

    @Column(name = "question_weight", nullable = false, precision = 6, scale = 2)
    private BigDecimal questionWeight;

    @Column(name = "run1_level")
    private Integer run1Level;

    @Column(name = "run2_level")
    private Integer run2Level;

    @Column(name = "run1_quote", columnDefinition = "text")
    private String run1Quote;

    @Column(name = "run2_quote", columnDefinition = "text")
    private String run2Quote;

    @Column(name = "quote_verified", nullable = false)
    private boolean quoteVerified;

    @Column(name = "verified_quote", columnDefinition = "text")
    private String verifiedQuote;

    @Column(name = "ai_rationale", columnDefinition = "text")
    private String aiRationale;

    @Column(name = "ai_level")
    private Integer aiLevel;

    @Column(name = "calculated_confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal calculatedConfidence;

    @Column(name = "flag_reasons", columnDefinition = "text")
    private String flagReasons;

    @Column(name = "review_status", nullable = false, length = 16)
    private String reviewStatus;

    @Column(name = "effective_level")
    private Integer effectiveLevel;

    @Column(name = "overridden_by")
    private UUID overriddenBy;

    @Column(name = "override_reason", columnDefinition = "text")
    private String overrideReason;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected ResponseEvaluationEntity() {
    }

    public ResponseEvaluationEntity(
            EvaluationRunEntity evaluationRun,
            CandidateEvaluationSummaryEntity summary,
            UUID entryId,
            UUID coreQuestionId,
            UUID skillId,
            String questionOrigin,
            BigDecimal questionWeight,
            Integer run1Level,
            Integer run2Level,
            String run1Quote,
            String run2Quote,
            boolean quoteVerified,
            String verifiedQuote,
            String aiRationale,
            Integer aiLevel,
            BigDecimal calculatedConfidence,
            String flagReasons,
            String reviewStatus,
            Integer effectiveLevel) {
        this.evaluationRun = evaluationRun;
        this.summary = summary;
        this.entryId = entryId;
        this.coreQuestionId = coreQuestionId;
        this.skillId = skillId;
        this.questionOrigin = questionOrigin;
        this.questionWeight = questionWeight;
        this.run1Level = run1Level;
        this.run2Level = run2Level;
        this.run1Quote = run1Quote;
        this.run2Quote = run2Quote;
        this.quoteVerified = quoteVerified;
        this.verifiedQuote = verifiedQuote;
        this.aiRationale = aiRationale;
        this.aiLevel = aiLevel;
        this.calculatedConfidence = calculatedConfidence;
        this.flagReasons = flagReasons;
        this.reviewStatus = reviewStatus;
        this.effectiveLevel = effectiveLevel;
    }

    public UUID getId() {
        return id;
    }

    public EvaluationRunEntity getEvaluationRun() {
        return evaluationRun;
    }

    public CandidateEvaluationSummaryEntity getSummary() {
        return summary;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getCoreQuestionId() {
        return coreQuestionId;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public String getQuestionOrigin() {
        return questionOrigin;
    }

    public BigDecimal getQuestionWeight() {
        return questionWeight;
    }

    public Integer getRun1Level() {
        return run1Level;
    }

    public Integer getRun2Level() {
        return run2Level;
    }

    public String getRun1Quote() {
        return run1Quote;
    }

    public String getRun2Quote() {
        return run2Quote;
    }

    public boolean isQuoteVerified() {
        return quoteVerified;
    }

    public String getVerifiedQuote() {
        return verifiedQuote;
    }

    public String getAiRationale() {
        return aiRationale;
    }

    public Integer getAiLevel() {
        return aiLevel;
    }

    public BigDecimal getCalculatedConfidence() {
        return calculatedConfidence;
    }

    public String getFlagReasons() {
        return flagReasons;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public Integer getEffectiveLevel() {
        return effectiveLevel;
    }

    public UUID getOverriddenBy() {
        return overriddenBy;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void approve(Instant reviewedAt) {
        this.reviewStatus = "approved";
        this.reviewedAt = reviewedAt;
    }

    public void overrideLevel(int newLevel, UUID professorId, String reason, Instant reviewedAt) {
        this.effectiveLevel = newLevel;
        this.overriddenBy = professorId;
        this.overrideReason = reason;
        this.reviewStatus = "overridden";
        this.reviewedAt = reviewedAt;
    }
}
