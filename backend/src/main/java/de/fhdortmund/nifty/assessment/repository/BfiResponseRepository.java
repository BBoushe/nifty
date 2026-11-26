package de.fhdortmund.nifty.assessment.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** One BFI-10 row per attempt. Private to the {@code assessment} slice. */
public interface BfiResponseRepository extends Repository<BfiResponseEntity, UUID> {

    BfiResponseEntity save(BfiResponseEntity response);

    Optional<BfiResponseEntity> findById(UUID attemptId);
}
