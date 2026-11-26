# Tasks: Assessment Chat (`assessment-chat`)

## Status
- **Branch:** `feat/assessment-chat` (branch off `dev`)
- **Worktree:** `../nifty-assessment-chat`
- **Current:** US-AC-01..06 done, `origin/dev` merged (with `invite-links`), all checks green, change archived. Committed locally, not pushed.
- **Next action:** Ask the human before push and PR/MR (`node scripts/forge.mjs`). Never merge.
- **Uncommitted changes:** none
- **PR / MR:** —
- **Open review findings:** —

## Tasks

- [x] **US-AC-01: Flyway `V6__assessment.sql`, ledger immutability trigger, and `AssessmentCleanupListener`**
  - [x] Write RED `@DataJpaTest` (PostgreSQL Testcontainers) proving `conversation_entry` trigger rejects `UPDATE` and unflagged `DELETE`, and `AssessmentDeletedEvent` with `SET LOCAL nifty.allow_purge = 'on'` purges attempts, ledger rows, and BFI-10 rows cleanly.
  - [x] Implement `V6__assessment.sql`, entities/repositories in `assessment/repository/`, and `AssessmentCleanupListener`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(assessment): add V6 schema, immutable ledger trigger, and GDPR cleanup`

- [x] **US-AC-02: Pure `AttemptStateMachine`, `PasteVelocityDetector`, and `Bfi10ScoringService`**
  - [x] Write RED pure-JVM unit tests: core question order, `inventedQuestions = false`, `followUpLimit`, `SOFT_SKILLS`-only skip straight to `BFI10`, `maxAttempts` → `409`, `minTimeSec` + velocity (`> 12 chars/s` for `>= 150 chars`) → `pasteSuspected`, `expiresAt` grace window (`timedOut = true` with partial text kept per Decision 2.20), post-grace `409`, and BFI-10 boundary scoring (all 1s, all 5s, mixed 1R/3R/4R/5R/7R).
  - [x] Implement zero-Stream pure domain services.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(assessment): implement state machine, paste detector, and BFI-10 scorer`

- [x] **US-AC-03: `AssessmentSessionService` with `ContextCreationService`, SSE streaming, expiry sweep, and `AttemptCompletedEvent`**
  - [x] Write RED tests with mocked `StudioQueryService`, `ContextCreationService`, and `ChatClient`: delegates prompt building to `ContextCreationService`, stores `injection_suspected` from `SanitizationReport`, streams SSE chunks + closing `expiresAt`, sweeps expired open questions via `AttemptExpiryScheduler`, and publishes `AttemptCompletedEvent(attemptId, configId, inviteId)` on `COMPLETED`.
  - [x] Implement `AssessmentSessionService` and `AttemptExpiryScheduler`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(assessment): wire ContextCreationService, SSE streaming, and completion event`

- [x] **US-AC-04: `AssessmentQueryService` contracts for `evaluation` and `teams`**
  - [x] Write RED unit/persistence tests verifying `getTranscriptForAttempt` returns ordered ledger entries with zero BFI-10 fields, and `getSoftSkillProfilesForConfig` returns already-scored `1.0..5.0` trait means for each candidate's latest completed attempt.
  - [x] Implement `AssessmentQueryService` and `AssessmentQueryServiceImpl`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(assessment): add AssessmentQueryService read contracts`

- [x] **US-AC-05: `AssessmentSessionController` (`CANDIDATE` only) and `docs/api.md` sync**
  - [x] Write RED `@WebMvcTest` + `@MockitoBean` tests: `POST /api/v1/assessment/sessions`, `POST /api/v1/assessment/sessions/{id}/answers` (SSE), `GET /api/v1/assessment/sessions/{id}` (refresh resume), `POST /api/v1/assessment/sessions/{id}/bfi10` (`204` / `400` missing items / `409` wrong state), `PROFESSOR` → `403`, cross-candidate read → `403`, answer `content` of 4001 characters → `400` ProblemDetail (`errors[].field = content`) with no ledger write and no `ContextCreationService` call, 4000 characters → accepted (REQ-AC-20, Decision 2.26).
  - [x] Implement `AssessmentSessionController` and sync `docs/api.md`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test` (including `ArchitectureTest`).
  - [x] Commit: `feat(assessment): add candidate session SSE and BFI-10 endpoints`

- [x] **US-AC-06: Vue 3.5 + PrimeVue Chat & BFI-10 UI (`frontend/src/views/assessment/`)**
  - [x] Write RED Vitest tests verifying countdown auto-submit at zero, zero pause/resume button, zero mid-interview score display, SSE streaming via shared `fetch` + `ReadableStream` client, refresh resume, 10-item BFI-10 form validation, and an answer box with `maxlength=4000` and a character counter (REQ-AC-20).
  - [x] Implement `AssessmentChatView.vue` and `Bfi10Form.vue` under `frontend/src/views/assessment/` (keep `ResultsView.vue` untouched for `evaluation`).
  - [x] Verify GREEN with `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`.
  - [x] Commit: `feat(frontend): add timed SSE assessment chat and BFI-10 views`

## Delivery

- [x] `git fetch origin && git merge origin/dev` (never rebase a pushed branch, no force pushes)
- [x] Run `cd backend && ./mvnw -B test`, `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all`, and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` (expect exit 0).
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive assessment-chat -y`, update `docs/feature_backlog.md` and `docs/feature_history.md`, commit `chore(specs): archive assessment-chat`.
- [ ] Push and open PR/MR via `node scripts/forge.mjs`.
- [ ] Pass cold read-only review, CI checks, human merge approval, and mirror sync.

## Handoff Log

### US-AC-01 (2026-11-26)
- Commits: `feat(assessment): add V6 schema, immutable ledger trigger, and GDPR cleanup`
- Verification: RED: `./mvnw -B test -Dtest=AssessmentLedgerSchemaTest` → FAIL (entities, repositories and listener missing); GREEN: `cd backend && ./mvnw -B test` → exit 0
- Contracts/Migrations added: `V6__assessment.sql` (`assessment_attempt`, `conversation_entry` + append-only trigger, `bfi_response`), `AssessmentCleanupListener`
- Decisions: 4.4 (purge through the FK cascade; the cascade fires the ledger trigger too, so an unflagged attempt delete fails)
- Open Questions: none

### US-AC-02 (2026-11-26)
- Commits: `feat(assessment): implement state machine, paste detector, and BFI-10 scorer`
- Verification: RED: `./mvnw -B test -Dtest='AttemptStateMachineTest,PasteVelocityDetectorTest,Bfi10ScoringServiceTest'` → FAIL (classes missing); GREEN: `cd backend && ./mvnw -B test` → exit 0
- Contracts/Migrations added: none (package-private `AttemptStateMachine`, `PasteVelocityDetector`, `Bfi10ScoringService`, `Bfi10Traits`, `LedgerEntry`, public `AttemptStatus`)
- Decisions: 2.20 (grace window keeps text with `timedOut`), 2.23, 2.24 (`> 12 chars/s` for `>= 150 chars`), 4.3; shortcut: every allowed follow-up is asked, no invented questions
- Open Questions: none

### US-AC-03 (2026-11-26)
- Commits: `feat(assessment): wire ContextCreationService, SSE streaming, and completion event`
- Verification: RED: `./mvnw -B test -Dtest=AssessmentSessionServiceIntegrationTest` → FAIL (service classes missing); GREEN: `cd backend && ./mvnw -B test` → exit 0
- Contracts/Migrations added: public `AssessmentSessionService`, `SessionReply` (`delta` / `question` / `done` SSE payloads), `SseSink`, `SessionStateResponse`; `nifty.assessment.grace-seconds` (default 5) and `nifty.assessment.expiry-sweep-cron`; test pool capped at 4 (PostgreSQL ran out of connections with one more cached context)
- Decisions: 1.11 (writes commit before the LLM streams), 1.18, 2.20, 2.24; gap-fills: `POST /sessions` resumes an unfinished attempt as JSON; LLM failure or a blank reply falls back to the configured core question text; a blank or too-late answer skips follow-ups
- Open Questions: grace period value is not in the spec (5 s chosen); context template tells the LLM "Ask no follow-up ... the next question comes next" when the active core question has `followUpLimit = 0`, which may make it skip asking that core question (context owner)

### US-AC-04 (2026-11-26)
- Commits: `feat(assessment): add AssessmentQueryService read contracts`
- Verification: RED: `./mvnw -B test -Dtest=AssessmentQueryServiceImplTest` → FAIL (`AssessmentQueryServiceImpl` missing); GREEN: `cd backend && ./mvnw -B test` → exit 0
- Contracts/Migrations added: `AssessmentQueryServiceImpl` replaces `AssessmentQueryServicePlaceholder` (deleted)
- Decisions: 1.19 (transcript route lives in `evaluation`), 2.13 (latest completed attempt per invite), REQ-AC-16 (transcript types carry no BFI-10 field)
- Open Questions: none

### US-AC-05 (2026-11-26)
- Commits: `feat(assessment): add candidate session SSE and BFI-10 endpoints`
- Verification: RED: `./mvnw -B test -Dtest=AssessmentSessionControllerTest` → FAIL (`AssessmentSessionController` missing); GREEN: `cd backend && ./mvnw -B test` → exit 0 (36 suites, 0 failures)
- Contracts/Migrations added: `POST/GET /api/v1/assessment/sessions/**` (`docs/api.md` §4 + §4.1 SSE events), `spring.mvc.async.request-timeout: 180s`
- Decisions: 2.26 (`@Size(max = 4000)` rejects before the service), 1.19 (no transcript route here)
- Open Questions: candidate sessions return `403` until `invite-links` implements `AuthContextService.currentCandidate()`

### US-AC-06 (2026-11-26)
- Commits: `fix(assessment): send attemptId in the question SSE event`, `feat(frontend): add timed SSE assessment chat and BFI-10 views`
- Verification: RED: `npx vitest run src/views/assessment src/api/client.test.ts` → FAIL (components and `postJsonOrSse` missing); GREEN: `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build` → exit 0 (81 tests)
- Contracts/Migrations added: `postJsonOrSse` in `src/api/client.ts` (JSON or SSE start), `src/api/assessment.ts`; `question` SSE event now carries `attemptId`; `AssessmentView.vue` placeholder replaced by `AssessmentChatView.vue`
- Decisions: 2.14 (countdown from server `expiresAt`, auto-submit at zero, no pause), 2.26 (`maxlength=4000` + counter), REQ-AC-03 (no score, weight or trait text)
- Open Questions: none

### Invariant, mutation and cold-review round (2026-11-26)
- Commits: `test(assessment): add invariant property tests and kill surviving mutants`, `fix(assessment): apply cold-review findings`
- Verification: `AssessmentInvariantPropertyTest` (seed 20261126, 2000 rounds, 6 properties) green. PIT (temporary pom plugin, reverted): pure domain 82/83 killed, services and web 142/148 detected; the 7 left are equivalent or unreachable (documented). RED → GREEN for the review fixes: `AssessmentSessionServiceTest`, 2 integration tests, 1 controller test, 2 view tests. `cd backend && ./mvnw -B test` and `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build` → exit 0.
- Contracts/Migrations added: `serverTime` on the `question` event and `SessionStateResponse`; `nifty.assessment.stale-reply-seconds`; `Bfi10SubmitRequest.items` `@NotNull`
- Decisions: 2.28 (gap-fills). Not changed: `@EnableScheduling` stays in `assessment/service`, as `00-parallel-waves-and-contracts.md` §1 prescribes.
- Open Questions: context template tells the LLM "Ask no follow-up ... the next question comes next" for a core question with `followUpLimit = 0` (context owner)
