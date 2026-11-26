package de.fhdortmund.nifty.assessment.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Private to the {@code assessment} slice (ArchUnit Decision 1.10). No generic delete method. */
public interface AssessmentAttemptRepository extends Repository<AssessmentAttemptEntity, UUID> {

    AssessmentAttemptEntity save(AssessmentAttemptEntity attempt);

    Optional<AssessmentAttemptEntity> findById(UUID id);

    /**
     * GDPR purge only (Decision 4.4). The FK cascade removes ledger and BFI-10 rows, so the caller must have set
     * {@code SET LOCAL nifty.allow_purge = 'on'}.
     */
    @Modifying
    @Query("DELETE FROM AssessmentAttemptEntity a WHERE a.configId = :configId")
    void purgeByConfigId(@Param("configId") UUID configId);
}
