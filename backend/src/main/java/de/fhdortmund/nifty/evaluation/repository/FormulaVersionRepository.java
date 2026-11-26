package de.fhdortmund.nifty.evaluation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FormulaVersionRepository extends JpaRepository<FormulaVersionEntity, UUID> {

    Optional<FormulaVersionEntity> findTopByConfigIdOrderByVersionNumberDesc(UUID configId);

    List<FormulaVersionEntity> findByConfigIdOrderByVersionNumberDesc(UUID configId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FormulaVersionEntity f WHERE f.configId = :configId")
    void deleteByConfigId(UUID configId);
}
