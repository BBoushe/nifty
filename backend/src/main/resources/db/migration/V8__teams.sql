-- team-builder (US-TB-01, Decisions 1.20, 2.23, 4.4): DRAFT/PUBLISHED team proposals and their members.
-- No FK to assessment_config or invite (cross-slice contract rule); AssessmentDeletedEvent purges these rows.

CREATE TABLE team_proposal (
    id                   UUID PRIMARY KEY,
    config_id            UUID NOT NULL,
    status               VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    num_teams            INT NOT NULL CHECK (num_teams >= 1),
    grouping_mode        VARCHAR(16) NOT NULL CHECK (grouping_mode IN ('HARD_SKILLS', 'SOFT_SKILLS', 'COMBINED')),
    alpha                NUMERIC(5,4) NOT NULL CHECK (alpha >= 0 AND alpha <= 1),
    combined_hard_weight NUMERIC(5,4) NOT NULL CHECK (combined_hard_weight >= 0 AND combined_hard_weight <= 1),
    seed                 BIGINT NOT NULL,
    cost_metrics         JSONB NOT NULL,
    created_by           VARCHAR(128) NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at         TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_team_proposal_config_created
    ON team_proposal (config_id, created_at DESC);

CREATE TABLE team_member (
    id          UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES team_proposal (id) ON DELETE CASCADE,
    team_no     INT NOT NULL CHECK (team_no >= 1),
    invite_id   UUID NOT NULL,
    UNIQUE (proposal_id, invite_id)
);
