-- studio-assessment-creation (Decisions 1.19, 2.21, 2.22, 2.23, 4.4)
-- One-shot immutable assessment configurations, skills (5-level hidden rubrics, no weight),
-- weighted core questions, join table, and PostgreSQL immutability triggers.

CREATE TABLE assessment_config (
    id                   UUID PRIMARY KEY,
    professor_id         UUID NOT NULL, -- No FK: professor_account lives in the auth slice (V2).
    title                TEXT NOT NULL,
    vision               TEXT NOT NULL,
    min_coverage         NUMERIC(5,4) NOT NULL CHECK (min_coverage >= 0 AND min_coverage <= 1),
    max_attempts         INT NOT NULL DEFAULT 1 CHECK (max_attempts >= 1),
    invented_questions   BOOLEAN NOT NULL DEFAULT FALSE,
    bfi10_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    team_size            INT NOT NULL CHECK (team_size >= 2),
    grouping_mode        VARCHAR(16) NOT NULL CHECK (grouping_mode IN ('HARD_SKILLS', 'SOFT_SKILLS', 'COMBINED')),
    soft_preset          VARCHAR(32) NOT NULL CHECK (soft_preset IN ('ENGINEERING_DEFAULT', 'EQUAL_WEIGHTS', 'CUSTOM')),
    combined_hard_weight NUMERIC(5,4) NOT NULL CHECK (combined_hard_weight >= 0 AND combined_hard_weight <= 1),
    alpha                NUMERIC(5,4) NOT NULL CHECK (alpha >= 0 AND alpha <= 1),
    constraint_weights   JSONB NOT NULL DEFAULT '{}'::jsonb,
    status               VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED' CHECK (status = 'PUBLISHED'),
    version              INT NOT NULL DEFAULT 1 CHECK (version = 1),
    idempotency_key      UUID NOT NULL UNIQUE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assessment_config_professor_created
    ON assessment_config (professor_id, created_at DESC);

CREATE TABLE skill (
    id            UUID PRIMARY KEY,
    config_id     UUID NOT NULL REFERENCES assessment_config(id) ON DELETE CASCADE,
    name          TEXT NOT NULL,
    rubric_levels JSONB NOT NULL CHECK (jsonb_typeof(rubric_levels) = 'array' AND jsonb_array_length(rubric_levels) = 5)
    -- Decision 2.22: no weight column on skill.
);

CREATE UNIQUE INDEX uq_skill_config_name
    ON skill (config_id, lower(btrim(name)));

CREATE TABLE core_question (
    id                   UUID PRIMARY KEY,
    config_id            UUID NOT NULL REFERENCES assessment_config(id) ON DELETE CASCADE,
    position             INT NOT NULL CHECK (position >= 1),
    text                 TEXT NOT NULL,
    weight               NUMERIC(6,2) NOT NULL CHECK (weight > 0),
    confidence_threshold NUMERIC(5,4) NOT NULL CHECK (confidence_threshold >= 0 AND confidence_threshold <= 1),
    min_time_sec         INT NOT NULL CHECK (min_time_sec > 0),
    max_time_sec         INT NOT NULL,
    follow_up_limit      INT NOT NULL CHECK (follow_up_limit >= 0 AND follow_up_limit <= 3),
    CHECK (min_time_sec < max_time_sec),
    UNIQUE (config_id, position)
);

CREATE TABLE core_question_skill (
    core_question_id UUID NOT NULL REFERENCES core_question(id) ON DELETE CASCADE,
    skill_id         UUID NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    PRIMARY KEY (core_question_id, skill_id)
);

-- REQ-IS-08 / Decision 2.21 / Decision 4.4: block every UPDATE, and block DELETE unless
-- the GDPR deletion transaction sets SET LOCAL nifty.allow_purge = 'on'.
CREATE OR REPLACE FUNCTION studio_block_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'Studio rows are immutable (UPDATE blocked on %)', TG_TABLE_NAME;
    END IF;
    IF TG_OP = 'DELETE' AND current_setting('nifty.allow_purge', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'Studio rows cannot be deleted without nifty.allow_purge=on (DELETE blocked on %)', TG_TABLE_NAME;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_assessment_config_immutable
    BEFORE UPDATE OR DELETE ON assessment_config
    FOR EACH ROW EXECUTE FUNCTION studio_block_mutation();

CREATE TRIGGER trg_skill_immutable
    BEFORE UPDATE OR DELETE ON skill
    FOR EACH ROW EXECUTE FUNCTION studio_block_mutation();

CREATE TRIGGER trg_core_question_immutable
    BEFORE UPDATE OR DELETE ON core_question
    FOR EACH ROW EXECUTE FUNCTION studio_block_mutation();

CREATE TRIGGER trg_core_question_skill_immutable
    BEFORE UPDATE OR DELETE ON core_question_skill
    FOR EACH ROW EXECUTE FUNCTION studio_block_mutation();
