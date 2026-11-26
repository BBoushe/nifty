# Tasks: Assessment Chat (`assessment-chat`)

## Status
- **Branch:** `feat/assessment-chat` (branch off `dev`)
- **Worktree:** `../nifty-assessment-chat`
- **Current:** Ready to start in parallel against mocked `StudioQueryService`, `ContextCreationService`, and `AuthContextService`
- **Next action:** Start US-AC-01
- **Uncommitted changes:** none
- **PR / MR:** —
- **Open review findings:** —

## Tasks

- [ ] **US-AC-01: Flyway `V6__assessment.sql`, ledger immutability trigger, and `AssessmentCleanupListener`**
  - [ ] Write RED `@DataJpaTest` (PostgreSQL Testcontainers) proving `conversation_entry` trigger rejects `UPDATE` and unflagged `DELETE`, and `AssessmentDeletedEvent` with `SET LOCAL nifty.allow_purge = 'on'` purges attempts, ledger rows, and BFI-10 rows cleanly.
  - [ ] Implement `V6__assessment.sql`, entities/repositories in `assessment/repository/`, and `AssessmentCleanupListener`.
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [ ] Commit: `feat(assessment): add V6 schema, immutable ledger trigger, and GDPR cleanup`

- [ ] **US-AC-02: Pure `AttemptStateMachine`, `PasteVelocityDetector`, and `Bfi10ScoringService`**
  - [ ] Write RED pure-JVM unit tests: core question order, `inventedQuestions = false`, `followUpLimit`, `SOFT_SKILLS`-only skip straight to `BFI10`, `maxAttempts` → `409`, `minTimeSec` + velocity (`> 12 chars/s` for `>= 150 chars`) → `pasteSuspected`, `expiresAt` grace window (`timedOut = true` with partial text kept per Decision 2.20), post-grace `409`, and BFI-10 boundary scoring (all 1s, all 5s, mixed 1R/3R/4R/5R/7R).
  - [ ] Implement zero-Stream pure domain services.
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [ ] Commit: `feat(assessment): implement state machine, paste detector, and BFI-10 scorer`

- [ ] **US-AC-03: `AssessmentSessionService` with `ContextCreationService`, SSE streaming, expiry sweep, and `AttemptCompletedEvent`**
  - [ ] Write RED tests with mocked `StudioQueryService`, `ContextCreationService`, and `ChatClient`: delegates prompt building to `ContextCreationService`, stores `injection_suspected` from `SanitizationReport`, streams SSE chunks + closing `expiresAt`, sweeps expired open questions via `AttemptExpiryScheduler`, and publishes `AttemptCompletedEvent(attemptId, configId, inviteId)` on `COMPLETED`.
  - [ ] Implement `AssessmentSessionService` and `AttemptExpiryScheduler`.
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [ ] Commit: `feat(assessment): wire ContextCreationService, SSE streaming, and completion event`

- [ ] **US-AC-04: `AssessmentQueryService` contracts for `evaluation` and `teams`**
  - [ ] Write RED unit/persistence tests verifying `getTranscriptForAttempt` returns ordered ledger entries with zero BFI-10 fields, and `getSoftSkillProfilesForConfig` returns already-scored `1.0..5.0` trait means for each candidate's latest completed attempt.
  - [ ] Implement `AssessmentQueryService` and `AssessmentQueryServiceImpl`.
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [ ] Commit: `feat(assessment): add AssessmentQueryService read contracts`

- [ ] **US-AC-05: `AssessmentSessionController` (`CANDIDATE` only) and `docs/api.md` sync**
  - [ ] Write RED `@WebMvcTest` + `@MockitoBean` tests: `POST /api/v1/assessment/sessions`, `POST /api/v1/assessment/sessions/{id}/answers` (SSE), `GET /api/v1/assessment/sessions/{id}` (refresh resume), `POST /api/v1/assessment/sessions/{id}/bfi10` (`204` / `400` missing items / `409` wrong state), `PROFESSOR` → `403`, cross-candidate read → `403`, answer `content` of 4001 characters → `400` ProblemDetail (`errors[].field = content`) with no ledger write and no `ContextCreationService` call, 4000 characters → accepted (REQ-AC-20, Decision 2.26).
  - [ ] Implement `AssessmentSessionController` and sync `docs/api.md`.
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test` (including `ArchitectureTest`).
  - [ ] Commit: `feat(assessment): add candidate session SSE and BFI-10 endpoints`

- [ ] **US-AC-06: Vue 3.5 + PrimeVue Chat & BFI-10 UI (`frontend/src/views/assessment/`)**
  - [ ] Write RED Vitest tests verifying countdown auto-submit at zero, zero pause/resume button, zero mid-interview score display, SSE streaming via shared `fetch` + `ReadableStream` client, refresh resume, 10-item BFI-10 form validation, and an answer box with `maxlength=4000` and a character counter (REQ-AC-20).
  - [ ] Implement `AssessmentChatView.vue` and `Bfi10Form.vue` under `frontend/src/views/assessment/` (keep `ResultsView.vue` untouched for `evaluation`).
  - [ ] Verify GREEN with `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`.
  - [ ] Commit: `feat(frontend): add timed SSE assessment chat and BFI-10 views`

## Delivery

- [ ] `git fetch origin && git merge origin/dev` (never rebase a pushed branch, no force pushes)
- [ ] Run `cd backend && ./mvnw -B test`, `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all`, and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` (expect exit 0).
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive assessment-chat -y`, update `docs/feature_backlog.md` and `docs/feature_history.md`, commit `chore(specs): archive assessment-chat`.
- [ ] Push and open PR/MR via `node scripts/forge.mjs`.
- [ ] Pass cold read-only review, CI checks, human merge approval, and mirror sync.

## Handoff Log
