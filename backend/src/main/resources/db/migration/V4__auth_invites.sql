-- invite-links (US-IL-01..02, REQ-AA-03, Decision 1.17): personal candidate invite links.
-- Stores only a SHA-256 hex hash of the random token; never stores plain tokens.
-- No FK to assessment_config or professor_account (cross-slice contract rule).

CREATE TABLE invite (
    id              UUID PRIMARY KEY,
    config_id       UUID NOT NULL,
    candidate_ref   VARCHAR(256) NOT NULL,
    token_hash      CHAR(64) NOT NULL UNIQUE,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (config_id, candidate_ref)
);

CREATE INDEX idx_invite_config_created
    ON invite (config_id, created_at DESC);
