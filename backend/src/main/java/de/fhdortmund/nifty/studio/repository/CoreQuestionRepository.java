package de.fhdortmund.nifty.studio.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Private to the {@code studio} slice (ArchUnit Decision 1.10). */
public interface CoreQuestionRepository extends Repository<CoreQuestionEntity, UUID> {

    CoreQuestionEntity save(CoreQuestionEntity question);

    @Query("""
            SELECT DISTINCT q FROM CoreQuestionEntity q
            LEFT JOIN FETCH q.skills
            WHERE q.config.id = :configId
            ORDER BY q.position ASC
            """)
    List<CoreQuestionEntity> findWithSkillsByConfigIdOrderByPositionAsc(@Param("configId") UUID configId);
}
