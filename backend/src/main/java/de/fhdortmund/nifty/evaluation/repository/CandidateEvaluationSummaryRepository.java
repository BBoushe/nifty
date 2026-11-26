package de.fhdortmund.nifty.evaluation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CandidateEvaluationSummaryRepository extends JpaRepository<CandidateEvaluationSummaryEntity, UUID> {

    Optional<CandidateEvaluationSummaryEntity> findByAttemptId(UUID attemptId);

    Optional<CandidateEvaluationSummaryEntity> findTopByConfigIdAndInviteIdOrderByAttemptNoDescCreatedAtDesc(
            UUID configId, UUID inviteId);

    /**
     * Decision 2.13: the newest attempt per invite in ANY status (queued, running, failed, completed).
     * A summary exists from enqueue on, so a retake supersedes the older attempt immediately.
     */
    @Query(value = """
            SELECT DISTINCT ON (invite_id) *
            FROM candidate_evaluation_summary
            WHERE config_id = :configId
            ORDER BY invite_id, attempt_no DESC, created_at DESC
            """, nativeQuery = true)
    List<CandidateEvaluationSummaryEntity> findLatestPerInvite(UUID configId);

    /**
     * Decision 2.13: the newest attempt per invite, kept only when it is COMPLETED. An older completed attempt never
     * shows through while the retake is queued, running, or failed.
     */
    @Query(value = """
            SELECT latest.*
            FROM (
                SELECT DISTINCT ON (invite_id) *
                FROM candidate_evaluation_summary
                WHERE config_id = :configId
                ORDER BY invite_id, attempt_no DESC, created_at DESC
            ) latest
            WHERE latest.evaluation_status = 'COMPLETED'
            """, nativeQuery = true)
    List<CandidateEvaluationSummaryEntity> findLatestCompletedPerInvite(UUID configId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CandidateEvaluationSummaryEntity s WHERE s.configId = :configId")
    void deleteByConfigId(UUID configId);
}
