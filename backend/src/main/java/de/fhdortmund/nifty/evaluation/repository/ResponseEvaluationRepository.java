package de.fhdortmund.nifty.evaluation.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ResponseEvaluationRepository extends JpaRepository<ResponseEvaluationEntity, UUID> {

    List<ResponseEvaluationEntity> findBySummaryIdOrderByIdAsc(UUID summaryId);

    List<ResponseEvaluationEntity> findBySummaryIdIn(Collection<UUID> summaryIds);

    /** Scalar read: resolves the attempt without loading the response, so the summary lock comes first (REQ-SM-14). */
    @Query("SELECT r.summary.attemptId FROM ResponseEvaluationEntity r WHERE r.id = :id")
    Optional<UUID> findAttemptIdById(UUID id);

    @Query("SELECT r FROM ResponseEvaluationEntity r JOIN FETCH r.summary WHERE r.id = :id")
    Optional<ResponseEvaluationEntity> findWithSummaryById(UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM ResponseEvaluationEntity r
            WHERE r.summary.id IN (
                SELECT s.id FROM CandidateEvaluationSummaryEntity s WHERE s.configId = :configId
            )
            """)
    void deleteByConfigId(UUID configId);
}
