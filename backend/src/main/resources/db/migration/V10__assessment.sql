-- assessment-chat (Decisions 1.11, 1.19, 2.20, 2.24, 4.3, 4.4)
-- Attempts, the append-only conversation ledger and BFI-10 responses. V5 stays reserved (unused).
-- invite_id and config_id have no FK: invite (auth) and assessment_config (studio) belong to other slices.

CREATE TABLE assessment_attempt (
    id             UUID PRIMARY KEY,
    invite_id      UUID NOT NULL,
    config_id      UUID NOT NULL,
    config_version INT NOT NULL DEFAULT 1,
    attempt_no     INT NOT NULL CHECK (attempt_no >= 1),
    status         VARCHAR(16) NOT NULL CHECK (status IN ('IN_PROGRESS', 'BFI10', 'COMPLETED')),
    started_at     TIMESTAMP WITH TIME ZONE,
    completed_at   TIMESTAMP WITH TIME ZONE,
    UNIQUE (invite_id, attempt_no)
);

CREATE INDEX idx_assessment_attempt_config ON assessment_attempt (config_id);

CREATE TABLE conversation_entry (
    id                  UUID PRIMARY KEY,
    attempt_id          UUID NOT NULL REFERENCES assessment_attempt(id) ON DELETE CASCADE,
    seq                 INT NOT NULL CHECK (seq >= 1),
    author              VARCHAR(16) NOT NULL CHECK (author IN ('interviewer', 'candidate')),
    core_question_id    UUID,
    origin              VARCHAR(16) NOT NULL CHECK (origin IN ('core', 'follow_up', 'invented')),
    content             TEXT NOT NULL,
    target_skills       JSONB NOT NULL CHECK (jsonb_typeof(target_skills) = 'array'),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at          TIMESTAMP WITH TIME ZONE,
    timed_out           BOOLEAN NOT NULL DEFAULT FALSE,
    paste_suspected     BOOLEAN NOT NULL DEFAULT FALSE,
    injection_suspected BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (attempt_id, seq)
);

CREATE TABLE bfi_response (
    attempt_id        UUID PRIMARY KEY REFERENCES assessment_attempt(id) ON DELETE CASCADE,
    item_1            SMALLINT NOT NULL CHECK (item_1 BETWEEN 1 AND 5),
    item_2            SMALLINT NOT NULL CHECK (item_2 BETWEEN 1 AND 5),
    item_3            SMALLINT NOT NULL CHECK (item_3 BETWEEN 1 AND 5),
    item_4            SMALLINT NOT NULL CHECK (item_4 BETWEEN 1 AND 5),
    item_5            SMALLINT NOT NULL CHECK (item_5 BETWEEN 1 AND 5),
    item_6            SMALLINT NOT NULL CHECK (item_6 BETWEEN 1 AND 5),
    item_7            SMALLINT NOT NULL CHECK (item_7 BETWEEN 1 AND 5),
    item_8            SMALLINT NOT NULL CHECK (item_8 BETWEEN 1 AND 5),
    item_9            SMALLINT NOT NULL CHECK (item_9 BETWEEN 1 AND 5),
    item_10           SMALLINT NOT NULL CHECK (item_10 BETWEEN 1 AND 5),
    conscientiousness NUMERIC(4,2) NOT NULL,
    agreeableness     NUMERIC(4,2) NOT NULL,
    neuroticism       NUMERIC(4,2) NOT NULL,
    extraversion      NUMERIC(4,2) NOT NULL,
    openness          NUMERIC(4,2) NOT NULL,
    submitted_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

-- REQ-AC-19 / Decision 4.4: the ledger is append-only. Block every UPDATE, and block DELETE unless the
-- GDPR deletion transaction sets SET LOCAL nifty.allow_purge = 'on'. An FK cascade fires this trigger too.
CREATE OR REPLACE FUNCTION assessment_block_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'conversation_entry rows are append-only (UPDATE blocked)';
    END IF;
    IF current_setting('nifty.allow_purge', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'conversation_entry rows cannot be deleted without nifty.allow_purge=on';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_conversation_entry_append_only
    BEFORE UPDATE OR DELETE ON conversation_entry
    FOR EACH ROW EXECUTE FUNCTION assessment_block_ledger_mutation();
