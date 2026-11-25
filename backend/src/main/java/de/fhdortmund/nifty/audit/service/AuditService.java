package de.fhdortmund.nifty.audit.service;

import java.util.UUID;

/** Cross-slice contract (00-parallel-waves-and-contracts.md §3.2). Owner: audit-log-service. */
public interface AuditService {

    void record(
            String actorId,
            AuditActorRole actorRole,
            String entityType,    // e.g. "ASSESSMENT_CONFIG", "RESPONSE_EVALUATION", "ATTEMPT", "TEAM_PROPOSAL"
            UUID entityId,
            String action,        // e.g. "CONFIG_CREATED", "CONFIG_DELETED", "PROMPT_INJECTION_DETECTED", "SCORE_OVERRIDDEN", "TEAMS_PUBLISHED"
            String oldValue,      // nullable
            String newValue,      // nullable (never raw candidate PII)
            String reason         // nullable unless override
    );
}
