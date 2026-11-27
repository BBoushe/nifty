package de.fhdortmund.nifty.audit.repository;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * REQ-AL-02: insert and read only. Extends {@link Repository}, not {@code JpaRepository}, so no delete or update
 * method exists. Only the {@code audit} slice uses it (ArchUnit).
 */
public interface AuditLogRepository extends Repository<AuditLogEntity, Long> {

    AuditLogEntity save(AuditLogEntity entry);

    Optional<AuditLogEntity> findById(Long id);
}
