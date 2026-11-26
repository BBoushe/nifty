-- evaluation-service (Decisions 1.18, 1.19, 2.10-2.13, 2.22, 2.24, 4.4)
-- Formula versions, async evaluation runs, per-attempt summaries (UNIQUE attempt_id),
-- response evaluations with verbatim quotes & review states, and explicit N/A skill scores.

CREATE TABLE formula_version (
    id                           UUID PRIMARY KEY,
    config_id                    UUID NOT NULL, -- No FK: assessment_config lives in the studio slice (V3).
    version_number               INT NOT NULL CHECK (version_number >= 1),
    formula_preset               VARCHAR(32) NOT NULL DEFAULT 'WEIGHTED_MEAN'
                                 CHECK (formula_preset IN ('WEIGHTED_MEAN')),
    max_entry_skill_weight_share NUMERIC(5,4) NOT NULL DEFAULT 0.5000
                                 CHECK (max_entry_skill_weight_share > 0 AND max_entry_skill_weight_share <= 1),
    question_weights_json        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by                   UUID,
    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (config_id, version_number)
);

CREATE INDEX idx_formula_version_config_version
    ON formula_version (config_id, version_number DESC);

CREATE TABLE evaluation_run (
    id                       UUID PRIMARY KEY,
    attempt_id               UUID NOT NULL, -- No FK: assessment_attempt lives in the assessment slice (V6).
    config_id                UUID NOT NULL,
    formula_version_id       UUID NOT NULL REFERENCES formula_version(id) ON DELETE CASCADE,
    context_template_version VARCHAR(64),
    status                   VARCHAR(16) NOT NULL
                             CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
    idempotency_key          VARCHAR(128) NOT NULL UNIQUE,
    attempt_count            INT NOT NULL DEFAULT 1 CHECK (attempt_count >= 1),
    error_message            TEXT,
    started_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at             TIMESTAMP WITH TIME ZONE,
    UNIQUE (attempt_id, formula_version_id)
);

CREATE INDEX idx_evaluation_run_config ON evaluation_run (config_id);
CREATE INDEX idx_evaluation_run_attempt ON evaluation_run (attempt_id);

CREATE TABLE candidate_evaluation_summary (
    id                 UUID PRIMARY KEY,
    attempt_id         UUID NOT NULL UNIQUE, -- Decision 2.13: retakes never collide; latest attempt counts.
    config_id          UUID NOT NULL,
    invite_id          UUID NOT NULL,
    attempt_no         INT NOT NULL DEFAULT 1 CHECK (attempt_no >= 1),
    formula_version_id UUID NOT NULL REFERENCES formula_version(id) ON DELETE CASCADE,
    latest_run_id      UUID NOT NULL REFERENCES evaluation_run(id) ON DELETE CASCADE,
    evaluation_status  VARCHAR(16) NOT NULL
                       CHECK (evaluation_status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
    fit_score          NUMERIC(4,2) CHECK (fit_score IS NULL OR (fit_score >= 0.00 AND fit_score <= 4.00)),
    coverage           NUMERIC(5,4) NOT NULL DEFAULT 0.0000 CHECK (coverage >= 0.0000 AND coverage <= 1.0000),
    flagged_count      INT NOT NULL DEFAULT 0 CHECK (flagged_count >= 0),
    release_status     VARCHAR(16) NOT NULL
                       CHECK (release_status IN ('pending', 'flagged', 'auto_released', 'approved', 'overridden')),
    released_at        TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_candidate_eval_summary_config_invite
    ON candidate_evaluation_summary (config_id, invite_id, attempt_no DESC, created_at DESC);

CREATE TABLE response_evaluation (
    id                    UUID PRIMARY KEY,
    evaluation_run_id     UUID NOT NULL REFERENCES evaluation_run(id) ON DELETE CASCADE,
    summary_id            UUID NOT NULL REFERENCES candidate_evaluation_summary(id) ON DELETE CASCADE,
    entry_id              UUID NOT NULL,
    core_question_id      UUID NOT NULL,
    skill_id              UUID NOT NULL,
    question_origin       VARCHAR(16) NOT NULL CHECK (question_origin IN ('core', 'follow_up', 'invented')),
    question_weight       NUMERIC(6,2) NOT NULL CHECK (question_weight > 0),
    run1_level            INT CHECK (run1_level IS NULL OR (run1_level >= 0 AND run1_level <= 4)),
    run2_level            INT CHECK (run2_level IS NULL OR (run2_level >= 0 AND run2_level <= 4)),
    run1_quote            TEXT,
    run2_quote            TEXT,
    quote_verified        BOOLEAN NOT NULL DEFAULT FALSE,
    verified_quote        TEXT,
    ai_rationale          TEXT,
    ai_level              INT CHECK (ai_level IS NULL OR (ai_level >= 0 AND ai_level <= 4)),
    calculated_confidence NUMERIC(5,4) NOT NULL
                          CHECK (calculated_confidence >= 0.0000 AND calculated_confidence <= 1.0000),
    flag_reasons          TEXT,
    review_status         VARCHAR(16) NOT NULL
                          CHECK (review_status IN ('pending', 'flagged', 'auto_released', 'approved', 'overridden')),
    effective_level       INT CHECK (effective_level IS NULL OR (effective_level >= 0 AND effective_level <= 4)),
    overridden_by         UUID,
    override_reason       TEXT,
    reviewed_at           TIMESTAMP WITH TIME ZONE,
    UNIQUE (evaluation_run_id, entry_id, skill_id)
);

CREATE INDEX idx_response_evaluation_summary ON response_evaluation (summary_id);

CREATE TABLE skill_score (
    id                      UUID PRIMARY KEY,
    summary_id              UUID NOT NULL REFERENCES candidate_evaluation_summary(id) ON DELETE CASCADE,
    formula_version_id      UUID NOT NULL REFERENCES formula_version(id) ON DELETE CASCADE,
    skill_id                UUID NOT NULL,
    status                  VARCHAR(16) NOT NULL CHECK (status IN ('SCORED', 'N/A')),
    score                   NUMERIC(4,2),
    avg_confidence          NUMERIC(5,4) CHECK (avg_confidence IS NULL OR (avg_confidence >= 0.0000 AND avg_confidence <= 1.0000)),
    verified_evidence_count INT NOT NULL DEFAULT 0 CHECK (verified_evidence_count >= 0),
    review_status           VARCHAR(16) NOT NULL
                            CHECK (review_status IN ('pending', 'flagged', 'auto_released', 'approved', 'overridden')),
    CONSTRAINT chk_na_score_null CHECK (
        (status = 'N/A' AND score IS NULL)
        OR (status = 'SCORED' AND score IS NOT NULL AND score >= 0.00 AND score <= 4.00)
    ),
    UNIQUE (summary_id, skill_id)
);

CREATE INDEX idx_skill_score_summary ON skill_score (summary_id);
