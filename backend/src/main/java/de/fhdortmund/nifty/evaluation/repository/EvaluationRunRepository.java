package de.fhdortmund.nifty.evaluation.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRunEntity, UUID> {

    /**
     * Decision 2.28 / REQ-EP-09: atomically claims one run for one worker. Returns 1 only for the single winner,
     * so two workers never evaluate the same attempt. QUEUED, FAILED, or a RUNNING run whose heartbeat is older
     * than {@code staleBefore} (crashed worker) can be claimed while it still has tries left.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EvaluationRunEntity r
            SET r.status = 'RUNNING', r.attemptCount = r.attemptCount + 1, r.updatedAt = :now,
                r.completedAt = NULL, r.errorMessage = NULL
            WHERE r.id = :id
              AND r.attemptCount < :maxAttempts
              AND (r.status IN ('QUEUED', 'FAILED') OR (r.status = 'RUNNING' AND r.updatedAt < :staleBefore))
            """)
    int claim(UUID id, Instant now, Instant staleBefore, int maxAttempts);

    @Query("""
            SELECT r.id FROM EvaluationRunEntity r
            WHERE r.attemptCount < :maxAttempts
              AND (r.status IN ('QUEUED', 'FAILED') OR (r.status = 'RUNNING' AND r.updatedAt < :staleBefore))
            ORDER BY r.startedAt ASC
            """)
    List<UUID> findRetryableIds(int maxAttempts, Instant staleBefore, Pageable page);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM EvaluationRunEntity r WHERE r.configId = :configId")
    void deleteByConfigId(UUID configId);
}
