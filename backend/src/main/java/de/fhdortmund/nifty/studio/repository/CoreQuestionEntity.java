package de.fhdortmund.nifty.studio.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Immutable ordered core question targeting one or more skills (V3, Decisions 1.19, 2.21, 2.22). */
@Entity
@Immutable
@Table(name = "core_question")
public class CoreQuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "config_id", nullable = false)
    private AssessmentConfigEntity config;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "text", nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "weight", nullable = false, precision = 6, scale = 2)
    private BigDecimal weight;

    @Column(name = "confidence_threshold", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceThreshold;

    @Column(name = "min_time_sec", nullable = false)
    private int minTimeSec;

    @Column(name = "max_time_sec", nullable = false)
    private int maxTimeSec;

    @Column(name = "follow_up_limit", nullable = false)
    private int followUpLimit;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "core_question_skill",
            joinColumns = @JoinColumn(name = "core_question_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    @OrderBy("name ASC")
    private List<SkillEntity> skills = new ArrayList<>();

    protected CoreQuestionEntity() {
    }

    public CoreQuestionEntity(
            AssessmentConfigEntity config,
            int position,
            String text,
            BigDecimal weight,
            BigDecimal confidenceThreshold,
            int minTimeSec,
            int maxTimeSec,
            int followUpLimit,
            List<SkillEntity> skills) {
        this.config = config;
        this.position = position;
        this.text = text;
        this.weight = weight;
        this.confidenceThreshold = confidenceThreshold;
        this.minTimeSec = minTimeSec;
        this.maxTimeSec = maxTimeSec;
        this.followUpLimit = followUpLimit;
        this.skills = new ArrayList<>(skills);
    }

    public UUID getId() {
        return id;
    }

    public AssessmentConfigEntity getConfig() {
        return config;
    }

    public int getPosition() {
        return position;
    }

    public String getText() {
        return text;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public BigDecimal getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public int getMinTimeSec() {
        return minTimeSec;
    }

    public int getMaxTimeSec() {
        return maxTimeSec;
    }

    public int getFollowUpLimit() {
        return followUpLimit;
    }

    public List<SkillEntity> getSkills() {
        return Collections.unmodifiableList(skills);
    }
}
