package de.fhdortmund.nifty.evaluation.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SkillScoreRepository extends JpaRepository<SkillScoreEntity, UUID> {

    List<SkillScoreEntity> findBySummaryId(UUID summaryId);

    List<SkillScoreEntity> findBySummaryIdIn(Collection<UUID> summaryIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SkillScoreEntity sc WHERE sc.summary.id = :summaryId")
    void deleteBySummaryId(UUID summaryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM SkillScoreEntity sc
            WHERE sc.summary.id IN (
                SELECT s.id FROM CandidateEvaluationSummaryEntity s WHERE s.configId = :configId
            )
            """)
    void deleteByConfigId(UUID configId);
}
