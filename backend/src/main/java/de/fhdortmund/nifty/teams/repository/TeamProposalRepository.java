package de.fhdortmund.nifty.teams.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Private to the {@code teams} slice (ArchUnit Decision 1.10). */
public interface TeamProposalRepository extends Repository<TeamProposalEntity, UUID> {

    TeamProposalEntity save(TeamProposalEntity proposal);

    Optional<TeamProposalEntity> findById(UUID id);

    List<TeamProposalEntity> findByConfigIdOrderByCreatedAtDesc(UUID configId);

    /** GDPR purge (Decision 4.4); the database cascade removes the members. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TeamProposalEntity p WHERE p.configId = :configId")
    void deleteByConfigId(@Param("configId") UUID configId);
}
