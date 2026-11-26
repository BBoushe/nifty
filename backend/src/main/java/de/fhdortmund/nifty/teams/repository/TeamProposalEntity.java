package de.fhdortmund.nifty.teams.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A DRAFT or PUBLISHED team proposal (V8). Enum columns are strings so {@code repository} never imports {@code service}. */
@Entity
@Table(name = "team_proposal")
public class TeamProposalEntity {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "config_id", nullable = false, updatable = false)
    private UUID configId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "num_teams", nullable = false, updatable = false)
    private int numTeams;

    @Column(name = "grouping_mode", nullable = false, updatable = false, length = 16)
    private String groupingMode;

    @Column(name = "alpha", nullable = false, updatable = false, precision = 5, scale = 4)
    private BigDecimal alpha;

    @Column(name = "combined_hard_weight", nullable = false, updatable = false, precision = 5, scale = 4)
    private BigDecimal combinedHardWeight;

    @Column(name = "seed", nullable = false, updatable = false)
    private long seed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cost_metrics", nullable = false, columnDefinition = "jsonb")
    private Map<String, Double> costMetrics;

    @Column(name = "created_by", nullable = false, updatable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected TeamProposalEntity() {
    }

    public TeamProposalEntity(UUID configId, int numTeams, String groupingMode, BigDecimal alpha,
            BigDecimal combinedHardWeight, long seed, Map<String, Double> costMetrics, String createdBy) {
        this.configId = configId;
        this.status = DRAFT;
        this.numTeams = numTeams;
        this.groupingMode = groupingMode;
        this.alpha = alpha;
        this.combinedHardWeight = combinedHardWeight;
        this.seed = seed;
        this.costMetrics = costMetrics;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void updateCostMetrics(Map<String, Double> metrics) {
        this.costMetrics = metrics;
    }

    public void publish() {
        this.status = PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getConfigId() {
        return configId;
    }

    public String getStatus() {
        return status;
    }

    public int getNumTeams() {
        return numTeams;
    }

    public String getGroupingMode() {
        return groupingMode;
    }

    public BigDecimal getAlpha() {
        return alpha;
    }

    public BigDecimal getCombinedHardWeight() {
        return combinedHardWeight;
    }

    public long getSeed() {
        return seed;
    }

    public Map<String, Double> getCostMetrics() {
        return costMetrics;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
