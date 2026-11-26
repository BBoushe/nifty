package de.fhdortmund.nifty.evaluation.repository;

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
 * Immutable formula version snapshot (REQ-FE-02, REQ-FE-05, Decision 2.19, 2.22).
 */
@Entity
@Immutable
@Table(name = "formula_version")
public class FormulaVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "config_id", nullable = false)
    private UUID configId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "formula_preset", nullable = false, length = 32)
    private String formulaPreset;

    @Column(name = "max_entry_skill_weight_share", nullable = false, precision = 5, scale = 4)
    private BigDecimal maxEntrySkillWeightShare;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_weights_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, BigDecimal> questionWeightsJson = new LinkedHashMap<>();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FormulaVersionEntity() {
    }

    public FormulaVersionEntity(
            UUID configId,
            int versionNumber,
            String formulaPreset,
            BigDecimal maxEntrySkillWeightShare,
            Map<String, BigDecimal> questionWeightsJson,
            UUID createdBy) {
        this.configId = configId;
        this.versionNumber = versionNumber;
        this.formulaPreset = formulaPreset;
        this.maxEntrySkillWeightShare = maxEntrySkillWeightShare;
        this.questionWeightsJson = questionWeightsJson == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(questionWeightsJson);
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getConfigId() {
        return configId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getFormulaPreset() {
        return formulaPreset;
    }

    public BigDecimal getMaxEntrySkillWeightShare() {
        return maxEntrySkillWeightShare;
    }

    public Map<String, BigDecimal> getQuestionWeightsJson() {
        return Collections.unmodifiableMap(questionWeightsJson);
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
