package de.fhdortmund.nifty.studio.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Private to the {@code studio} slice (ArchUnit Decision 1.10). No generic update/delete methods. */
public interface AssessmentConfigRepository extends Repository<AssessmentConfigEntity, UUID> {

    AssessmentConfigEntity save(AssessmentConfigEntity config);

    Optional<AssessmentConfigEntity> findById(UUID id);

    Optional<AssessmentConfigEntity> findByIdAndProfessorId(UUID id, UUID professorId);

    Optional<AssessmentConfigEntity> findByIdempotencyKey(UUID idempotencyKey);

    List<AssessmentConfigEntity> findByProfessorIdOrderByCreatedAtDesc(UUID professorId);

    boolean existsById(UUID id);

    boolean existsByIdAndProfessorId(UUID id, UUID professorId);

    /** GDPR purge only (Decision 4.4): caller must have set {@code SET LOCAL nifty.allow_purge = 'on'}. */
    @Modifying
    @Query("DELETE FROM AssessmentConfigEntity c WHERE c.id = :id")
    void purgeById(@Param("id") UUID id);
}
