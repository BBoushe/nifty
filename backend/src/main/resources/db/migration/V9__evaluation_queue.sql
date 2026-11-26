-- Decision 2.28: durable evaluation queue. V7 is already applied on dev, so the queue columns change here.
-- attempt_count counts LLM tries: a QUEUED run has made none yet.
ALTER TABLE evaluation_run DROP CONSTRAINT evaluation_run_attempt_count_check;
ALTER TABLE evaluation_run ALTER COLUMN attempt_count SET DEFAULT 0;
ALTER TABLE evaluation_run ADD CONSTRAINT evaluation_run_attempt_count_check CHECK (attempt_count >= 0);

-- Heartbeat: the retry pass reclaims a RUNNING run whose updated_at is stale.
ALTER TABLE evaluation_run
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
