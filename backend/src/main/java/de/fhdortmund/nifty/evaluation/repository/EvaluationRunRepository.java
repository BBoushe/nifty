package de.fhdortmund.nifty.evaluation.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRunEntity, UUID> {

    Optional<EvaluationRunEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<EvaluationRunEntity> findByAttemptIdAndFormulaVersionId(UUID attemptId, UUID formulaVersionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM EvaluationRunEntity r WHERE r.configId = :configId")
    void deleteByConfigId(UUID configId);
}
