package de.fhdortmund.nifty.studio.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Immutable one-shot assessment configuration (V3, Decisions 1.10, 1.19, 2.21).
 * Stores enum columns as strings so {@code repository} never imports {@code service}.
 */
@Entity
@Immutable
@Table(name = "assessment_config")
public class AssessmentConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "professor_id", nullable = false)
    private UUID professorId;

    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "vision", nullable = false, columnDefinition = "text")
    private String vision;

    @Column(name = "min_coverage", nullable = false, precision = 5, scale = 4)
    private BigDecimal minCoverage;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "invented_questions", nullable = false)
    private boolean inventedQuestions;

    @Column(name = "bfi10_enabled", nullable = false)
    private boolean bfi10Enabled;

    @Column(name = "team_size", nullable = false)
    private int teamSize;

    @Column(name = "grouping_mode", nullable = false, length = 16)
    private String groupingMode;

    @Column(name = "soft_preset", nullable = false, length = 32)
    private String softPreset;

    @Column(name = "combined_hard_weight", nullable = false, precision = 5, scale = 4)
    private BigDecimal combinedHardWeight;

    @Column(name = "alpha", nullable = false, precision = 5, scale = 4)
    private BigDecimal alpha;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "constraint_weights", nullable = false, columnDefinition = "jsonb")
    private Map<String, BigDecimal> constraintWeights = new LinkedHashMap<>();

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssessmentConfigEntity() {
    }

    public AssessmentConfigEntity(
            UUID professorId,
            String title,
            String vision,
            BigDecimal minCoverage,
            int maxAttempts,
            boolean inventedQuestions,
            boolean bfi10Enabled,
            int teamSize,
            String groupingMode,
            String softPreset,
            BigDecimal combinedHardWeight,
            BigDecimal alpha,
            Map<String, BigDecimal> constraintWeights,
            UUID idempotencyKey) {
        this.professorId = professorId;
        this.title = title;
        this.vision = vision;
        this.minCoverage = minCoverage;
        this.maxAttempts = maxAttempts;
        this.inventedQuestions = inventedQuestions;
        this.bfi10Enabled = bfi10Enabled;
        this.teamSize = teamSize;
        this.groupingMode = groupingMode;
        this.softPreset = softPreset;
        this.combinedHardWeight = combinedHardWeight;
        this.alpha = alpha;
        this.constraintWeights = constraintWeights == null ? Map.of() : new LinkedHashMap<>(constraintWeights);
        this.status = "PUBLISHED";
        this.version = 1;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public String getTitle() {
        return title;
    }

    public String getVision() {
        return vision;
    }

    public BigDecimal getMinCoverage() {
        return minCoverage;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public boolean isBfi10Enabled() {
        return bfi10Enabled;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public String getGroupingMode() {
        return groupingMode;
    }

    public String getSoftPreset() {
        return softPreset;
    }

    public BigDecimal getCombinedHardWeight() {
        return combinedHardWeight;
    }

    public BigDecimal getAlpha() {
        return alpha;
    }

    public Map<String, BigDecimal> getConstraintWeights() {
        return Collections.unmodifiableMap(constraintWeights);
    }

    public String getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
