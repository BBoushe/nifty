package de.fhdortmund.nifty.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Private to the {@code auth} slice (ArchUnit Decision 1.10). */
public interface InviteRepository extends Repository<InviteEntity, UUID> {

    InviteEntity save(InviteEntity invite);

    Optional<InviteEntity> findByIdAndConfigId(UUID id, UUID configId);

    Optional<InviteEntity> findByTokenHash(String tokenHash);

    List<InviteEntity> findByConfigIdOrderByCreatedAtDesc(UUID configId);

    boolean existsByConfigIdAndCandidateRef(UUID configId, String candidateRef);

    boolean existsByIdAndRevokedFalse(UUID id);

    /** GDPR purge on AssessmentDeletedEvent (Decision 4.4). */
    @Modifying
    @Query("DELETE FROM InviteEntity i WHERE i.configId = :configId")
    void deleteByConfigId(@Param("configId") UUID configId);
}
