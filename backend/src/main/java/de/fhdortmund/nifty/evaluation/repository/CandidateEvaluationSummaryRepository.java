package de.fhdortmund.nifty.evaluation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CandidateEvaluationSummaryRepository extends JpaRepository<CandidateEvaluationSummaryEntity, UUID> {

    Optional<CandidateEvaluationSummaryEntity> findByAttemptId(UUID attemptId);

    List<CandidateEvaluationSummaryEntity> findByConfigIdOrderByCreatedAtDesc(UUID configId);

    Optional<CandidateEvaluationSummaryEntity> findTopByConfigIdAndInviteIdOrderByAttemptNoDescCreatedAtDesc(
            UUID configId, UUID inviteId);

    /** Decision 2.13: latest completed attempt per invite for a configuration. */
    @Query(value = """
            SELECT DISTINCT ON (invite_id) *
            FROM candidate_evaluation_summary
            WHERE config_id = :configId
              AND evaluation_status = 'COMPLETED'
            ORDER BY invite_id, attempt_no DESC, created_at DESC
            """, nativeQuery = true)
    List<CandidateEvaluationSummaryEntity> findLatestCompletedPerInvite(UUID configId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CandidateEvaluationSummaryEntity s WHERE s.configId = :configId")
    void deleteByConfigId(UUID configId);
}
