package de.fhdortmund.nifty.studio.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Immutable skill definition with 5 hidden rubric levels (0..4). No weight column (Decision 2.22).
 */
@Entity
@Immutable
@Table(name = "skill")
public class SkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "config_id", nullable = false)
    private AssessmentConfigEntity config;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rubric_levels", nullable = false, columnDefinition = "jsonb")
    private List<String> rubricLevels = new ArrayList<>();

    protected SkillEntity() {
    }

    public SkillEntity(AssessmentConfigEntity config, String name, List<String> rubricLevels) {
        this.config = config;
        this.name = name;
        this.rubricLevels = List.copyOf(rubricLevels);
    }

    public UUID getId() {
        return id;
    }

    public AssessmentConfigEntity getConfig() {
        return config;
    }

    public String getName() {
        return name;
    }

    public List<String> getRubricLevels() {
        return Collections.unmodifiableList(rubricLevels);
    }
}
