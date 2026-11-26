package de.fhdortmund.nifty.assessment.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Private to the {@code assessment} slice (ArchUnit Decision 1.10). No generic delete method. */
public interface AssessmentAttemptRepository extends Repository<AssessmentAttemptEntity, UUID> {

    AssessmentAttemptEntity save(AssessmentAttemptEntity attempt);

    Optional<AssessmentAttemptEntity> findById(UUID id);

    /** Serialises answers, interviewer replies and the expiry sweep of one attempt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AssessmentAttemptEntity a WHERE a.id = :id")
    Optional<AssessmentAttemptEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<AssessmentAttemptEntity> findFirstByInviteIdAndStatusNotOrderByAttemptNoDesc(UUID inviteId, String status);

    long countByInviteId(UUID inviteId);

    Optional<AssessmentAttemptEntity> findFirstByConfigIdAndInviteIdAndStatusOrderByAttemptNoDesc(UUID configId,
            UUID inviteId, String status);

    /** Newest attempt first within each invite, so the first row per invite is the latest one (Decision 2.13). */
    List<AssessmentAttemptEntity> findByConfigIdAndStatusOrderByInviteIdAscAttemptNoDesc(UUID configId, String status);

    List<AssessmentAttemptEntity> findByStatusAndCompletedAtGreaterThanEqual(String status, Instant since);

    /** IN_PROGRESS attempts whose last ledger row is an interviewer question that expired before {@code cutoff}. */
    @Query(value = "SELECT a.id FROM assessment_attempt a JOIN conversation_entry e ON e.attempt_id = a.id "
            + "WHERE a.status = 'IN_PROGRESS' AND e.author = 'interviewer' AND e.expires_at < :cutoff "
            + "AND e.seq = (SELECT max(e2.seq) FROM conversation_entry e2 WHERE e2.attempt_id = a.id)",
            nativeQuery = true)
    List<UUID> findIdsWithQuestionExpiredBefore(@Param("cutoff") Instant cutoff);

    /** IN_PROGRESS attempts with no open question whose last activity (last ledger row, or the start) is before cutoff. */
    @Query(value = "SELECT a.id FROM assessment_attempt a LEFT JOIN conversation_entry e ON e.attempt_id = a.id "
            + "AND e.seq = (SELECT max(e2.seq) FROM conversation_entry e2 WHERE e2.attempt_id = a.id) "
            + "WHERE a.status = 'IN_PROGRESS' AND ((e.id IS NULL AND a.started_at < :cutoff) "
            + "OR (e.author = 'candidate' AND e.created_at < :cutoff))",
            nativeQuery = true)
    List<UUID> findIdsAwaitingInterviewerSince(@Param("cutoff") Instant cutoff);

    /**
     * GDPR purge only (Decision 4.4). The FK cascade removes ledger and BFI-10 rows, so the caller must have set
     * {@code SET LOCAL nifty.allow_purge = 'on'}.
     */
    @Modifying
    @Query("DELETE FROM AssessmentAttemptEntity a WHERE a.configId = :configId")
    void purgeByConfigId(@Param("configId") UUID configId);
}
