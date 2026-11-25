package de.fhdortmund.nifty.audit.service;

import java.util.UUID;
import org.springframework.stereotype.Service;

/** Compile-only placeholder. audit-log-service deletes this class when the real bean lands. */
@Service
class AuditServicePlaceholder implements AuditService {

    @Override
    public void record(String actorId, AuditActorRole actorRole, String entityType, UUID entityId, String action,
            String oldValue, String newValue, String reason) {
        throw new UnsupportedOperationException("AuditService is not implemented yet: owner audit-log-service");
    }
}
