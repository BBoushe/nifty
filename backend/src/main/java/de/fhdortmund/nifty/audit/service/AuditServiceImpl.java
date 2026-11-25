package de.fhdortmund.nifty.audit.service;

import de.fhdortmund.nifty.audit.repository.AuditLogEntity;
import de.fhdortmund.nifty.audit.repository.AuditLogRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-AL-01: the single writer of {@code audit_log} rows. The row joins the caller's transaction (default
 * propagation). Never logs {@code oldValue} or {@code newValue}.
 */
@Service
@Transactional(readOnly = true)
class AuditServiceImpl implements AuditService {

    private final AuditLogRepository repository;

    AuditServiceImpl(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void record(String actorId, AuditActorRole actorRole, String entityType, UUID entityId, String action,
            String oldValue, String newValue, String reason) {
        require(actorId, "actorId");
        require(actorRole, "actorRole");
        require(entityType, "entityType");
        require(entityId, "entityId");
        require(action, "action");
        repository.save(new AuditLogEntity(actorId, actorRole.name(), entityType, entityId.toString(), action,
                oldValue, newValue, reason));
    }

    private static void require(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
