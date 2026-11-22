-- Baseline migration for Evidence-First Skill Assessment Platform
-- Establishes immutable audit log foundation (EU AI Act Annex III human-oversight trail)

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        VARCHAR(128) NOT NULL,
    actor_role      VARCHAR(32)  NOT NULL,
    entity_type     VARCHAR(64)  NOT NULL,
    entity_id       VARCHAR(128) NOT NULL,
    action          VARCHAR(64)  NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    reason          TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity
    ON audit_log (entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at
    ON audit_log (created_at);
