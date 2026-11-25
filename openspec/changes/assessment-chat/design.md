# Design: Assessment Chat (`assessment`)

> **Canonical Contract Reference:** `docs/architecture.md`, `docs/api.md`, `docs/tickets/00-parallel-waves-and-contracts.md`, `docs/tickets/03-assessment-chat.md`.

## Package Layout (`de.fhdortmund.nifty.assessment`)
- `web/`: `AssessmentSessionController` (`/api/v1/assessment/sessions/**`, `CANDIDATE` only), record DTOs (`AnswerRequest`, `SessionStateResponse`, `Bfi10SubmitRequest`).
- `service/`: `AssessmentSessionService`, `AttemptStateMachine`, `AttemptExpiryScheduler`, `Bfi10ScoringService` (zero Streams), `PasteVelocityDetector` (zero Streams), `AssessmentQueryService` (public interface), `AssessmentQueryServiceImpl`, `AssessmentCleanupListener` (`AssessmentDeletedEvent`).
- `repository/`: Private `@Entity` classes (`AssessmentAttemptEntity`, `ConversationEntryEntity`, `BfiResponseEntity`) and private `JpaRepository` interfaces.

## Flyway Migration (`V6__assessment.sql`)
- `assessment_attempt(id UUID PK, invite_id UUID NOT NULL, config_id UUID NOT NULL, config_version INT NOT NULL DEFAULT 1, attempt_no INT NOT NULL, status VARCHAR(16) NOT NULL, started_at TIMESTAMPTZ, completed_at TIMESTAMPTZ, UNIQUE (invite_id, attempt_no))`
- `conversation_entry(id UUID PK, attempt_id UUID FK, seq INT NOT NULL, author VARCHAR(16) NOT NULL, core_question_id UUID, origin VARCHAR(16) NOT NULL, content TEXT NOT NULL, target_skills JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ, timed_out BOOLEAN NOT NULL DEFAULT FALSE, paste_suspected BOOLEAN NOT NULL DEFAULT FALSE, injection_suspected BOOLEAN NOT NULL DEFAULT FALSE, UNIQUE (attempt_id, seq))`
- PostgreSQL trigger blocking `UPDATE` and unflagged `DELETE` on `conversation_entry` (allowing delete only with `SET LOCAL nifty.allow_purge = 'on'`).
- `bfi_response(attempt_id UUID PK FK, item_1..item_10 SMALLINT CHECK (1..5), conscientiousness NUMERIC(4,2), agreeableness NUMERIC(4,2), neuroticism NUMERIC(4,2), extraversion NUMERIC(4,2), openness NUMERIC(4,2), submitted_at TIMESTAMPTZ)`

## Upstream/Downstream Contracts
- Reads `StudioQueryService.interviewerView(configId)` (`studio`) and `ContextCreationService.buildInterviewerContext(...)` / `inspectCandidateInput(...)` (`context`).
- Publishes `AttemptCompletedEvent(attemptId, configId, inviteId)` inside the committing transaction when entering `COMPLETED`.
- Exposes `AssessmentQueryService` (`getTranscriptForAttempt`, `getLatestCompletedAttemptId`, `getSoftSkillProfilesForConfig`, `getSoftSkillProfileForAttempt`).
- **No professor transcript route under `/api/v1/assessment/**`:** Professor export lives in `evaluation` at `GET /api/v1/ranking/transcripts/{attemptId}` (Decision 1.19).
