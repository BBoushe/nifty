package de.fhdortmund.nifty.studio.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Private to the {@code studio} slice (ArchUnit Decision 1.10). */
public interface SkillRepository extends Repository<SkillEntity, UUID> {

    SkillEntity save(SkillEntity skill);

    List<SkillEntity> findByConfigIdOrderByNameAsc(UUID configId);

    long countByConfigId(UUID configId);
}
