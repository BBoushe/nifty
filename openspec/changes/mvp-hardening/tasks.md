# Tasks: MVP Hardening (`mvp-hardening`)

## Status
- **Branch:** `fix/mvp-hardening` (worktree `../nifty-mvp-hardening`, from `origin/dev` `d0480e6`)
- **Current:** Tasks 1 to 11 done (committed, not pushed). Candidate sign-out removed (Decision 2.36).
- **Next action:** Group D, Task 12 (prompt wording, prompt failure, bounded streams). Not started.
- **Uncommitted changes:** no
- **PR / MR:** none yet
- **Open review findings:** none

Rules for every code task: RED test first, then the code, then GREEN. Write the Handoff entry. Tick the boxes. Overwrite `## Status`. Commit with the files named (no `git add -A`, no chained `git add && git commit`). Change files under `backend/**` only after you read `.agents/rules/backend-java.md`, and under `frontend/**` only after you read `.agents/rules/frontend-vue.md`. A controller change also updates `docs/api.md` in the same commit.

Backend check: `cd backend && ./mvnw -B test -Dtest='<Class>'`. Frontend check: `cd frontend && npx vitest run <file>`.

## Group A — Professor review in modals (user request; do first)

- [x] **Task 1: Shared Transcript dialog (REQ-SM-10, REQ-CR-07)**
  - [x] RED: `frontend/src/components/TranscriptDialog.test.ts` covers four cases. (1) Interviewer and candidate bubbles render in turn order with the origin labels "Core question N" and "Follow-up". (2) A `null` answer shows "No answer". (3) Markers render only with `showMarkers`. (4) `highlight` wraps the quote in `<mark>` with no `v-html`: a quote with `<b>` stays text. Also `transcript.test.ts` for `fromTranscriptExport`, `fromCandidateTurn` and `formatDuration` (`48 → "48 s"`, `72 → "1 min 12 s"`, `null → "—"`).
  - [x] GREEN: `frontend/src/components/transcript.ts` and `TranscriptDialog.vue` per design §1, using PrimeVue `Dialog`.
  - [x] Verify: `npx vitest run src/components` → pass.
  - [x] Commit `feat(frontend): add readable transcript dialog`.

- [x] **Task 2: Evidence review in a modal (REQ-SM-09, REQ-SM-10)**
  - [x] RED in `RankingViews.test.ts`. (1) Inspect opens a dialog (`role="dialog"`) and the matrix stays mounted. (2) Close or Esc closes it. (3) "View transcript" opens `TranscriptDialog` with markers. (4) "Show in transcript" passes `focusEntryId` and the highlighted quote. (5) The inline ledger section is gone from the evidence view.
  - [x] GREEN: `git mv EvidenceDrawer.vue EvidenceDialog.vue`, wrap it in `Dialog` per design §1, update `RankingView.vue` and the existing tests that mention the drawer.
  - [x] Candidate: `ResultsView.vue` replaces the inline conversation list with "Open conversation" and the dialog (`showMarkers = false`). Add a test in `ResultsView.test.ts`.
  - [x] Verify: `npx vitest run src/views/ranking src/views/assessment` → pass.
  - [x] Commit `feat(frontend): open evidence review and transcript as modals`.

## Group B — Frontend fixes

- [x] **Task 3: Live status and Release state (REQ-SM-11, REQ-CR-07)**
  - [x] Backend first: `AttemptEvaluationsResponse` gains `evaluationStatus` and `candidateRef`, and matrix rows gain `candidateRef` (design §3, names and status). RED: controller test asserts both fields. GREEN. Update `docs/api.md`.
  - [x] Frontend RED: (1) the matrix query refetches while any row is `QUEUED` or `RUNNING` (fake timers, 5 s) and stops when all rows are `COMPLETED`; (2) Release is disabled for `RUNNING` and `FAILED`, with the tooltip text; (3) ResultsView refetches while `released === false`.
  - [x] GREEN: `refetchInterval` plus a Refresh button per design §2.
  - [x] Commit `fix: refresh evaluation status and gate the release button`.

- [x] **Task 4: Names, route sync, Teams link, cache key (REQ-SM-12, REQ-NAV-11)**
  - [x] RED: (1) the matrix label shows `candidateRef (#n)`; no `inviteId.slice` is left (`grep -rn "inviteId.slice" frontend/src` is empty); (2) a route param change A → B updates the selection; (3) a dropdown change calls `router.replace`; (4) the "Build teams" link points to `/ranking/teams/<id>`; (5) a Studio delete invalidates the key that Ranking reads.
  - [x] GREEN per design §2.
  - [x] Commit `fix(frontend): show candidate names and sync the ranking route`.

- [x] **Task 5: Retake UI (REQ-AC-22)**
  - [x] Backend RED: `SessionStateResponse.attemptsLeft` is correct for 0, 1 and 2 used of a maximum of 2. GREEN. Update `docs/api.md`.
  - [x] Frontend RED: (1) a stored `COMPLETED` attempt with `attemptsLeft = 1` shows "Start new attempt" and clears the `sessionStorage` key on start; (2) `attemptsLeft = 0` shows "No attempts left" and no start button.
  - [x] GREEN. Commit `fix(assessment): let candidates start a retake`.

- [x] **Task 6: Shell errors, 404 and invites scroll (REQ-NAV-10, REQ-NAV-12, REQ-NAV-13)**
  - [x] RED: (1) an unknown path renders `NotFoundView` with the role start link; (2) a network error in `fetchMe` shows "Server not reachable", and Retry calls it again; (3) a `500` from `logout` shows "Sign-out failed. Try again." and does not navigate; (4) the `studio-invites` route scrolls `#invites` into view (mock `scrollIntoView`).
  - [x] GREEN per design §2. Delete `frontend/src/api/schema.d.ts` after `grep -rn "schema" frontend/src` shows no import.
  - [x] Safe `localStorage`: RED test where `setItem` throws and the form store still updates. GREEN: `try/catch` in `stores/assessmentForm.ts`.
  - [x] Commit `fix(frontend): handle server errors, unknown routes and storage failures`.

## Group C — Evaluation backend

- [x] **Task 7: Tolerant judge parsing (REQ-EP-10)**
  - [x] RED in `TwoRunResponseJudgeTest`: a fenced reply ` ```json\n{…}\n``` ` parses; text before and after the object parses; `level` 7 inside a fence still fails; the judge call options carry temperature `0.0` (captor on the prompt).
  - [x] GREEN per design §3. Commit `fix(evaluation): accept fenced judge JSON and call the judge at temperature 0`.

- [x] **Task 8: Final-try crash, re-run, lost events (REQ-EP-11..13)**
  - [x] RED. (1) `EvaluationRetrySchedulerTest`: a stale `RUNNING` run at max tries becomes `FAILED` with `STALE_FINAL_TRY`. (2) The same test: a completed attempt with no run gets exactly one run, also when the pass runs twice. (3) Controller and service tests for `POST /api/v1/ranking/attempts/{attemptId}/evaluation/retry`: `FAILED` → `QUEUED`, tries 0, audit `EVALUATION_RETRIED`; `COMPLETED` → `409`; another professor's attempt → `404`.
  - [x] GREEN per design §3. The new assessment read method lives in `AssessmentQueryService` and its implementation, with no cross-slice repository access. Add the audit action. Update `docs/api.md`. The Evidence dialog shows "Re-run evaluation" for `FAILED`, with a frontend test.
  - [x] Commit `fix(evaluation): fail stale final tries, recover missed runs, add professor re-run`.

- [x] **Task 9: Snapshot weights and lock order (REQ-FE-07, REQ-SM-14)**
  - [x] RED: (1) the first scoring creates formula version 1 and uses its stored weights. Change the live weights in the test stub, and the score must not change. (2) Mockito `verify(summaryRepo).findByAttemptIdForUpdate(id)` is called before any response-evaluation write, for approve, override and the cleanup listener. Use `InOrder`.
  - [x] Optional race test (Testcontainers, like `EvaluationReleaseRaceTest`): an approve and a release at the same time both finish, neither with a `500`. (Skipped: not cheap; the lock order is covered by the `InOrder` tests and the real-DB lifecycle tests.)
  - [x] GREEN per design §3. Commit `fix(evaluation): score with the stored formula version and lock the summary first`.

- [x] **Task 10: Soft-only auto-release (REQ-SM-13, Decision 2.31)**
  - [x] RED in `ReleaseGateEvaluatorTest`: zero hard skills with a complete profile → `AUTO_RELEASED`; zero hard skills without a profile → `PENDING`; 3 hard skills with no evidence → `PENDING`.
  - [x] GREEN: pass `hardSkillCount` (from `StudioQueryService`) and `softProfileComplete` (from `AssessmentQueryService`) into the gate. Add `// Decision 2.31`. Add one `AsyncEvaluationRunner` test for a soft-only attempt end to end with mocks.
  - [x] Commit `fix(evaluation): auto-release soft-skills-only attempts`.

## Group D — Assessment and context backend

- [x] **Task 11: Remove the invented-questions toggle (REQ-AC-02, REQ-IS-05, REQ-CC-02, Decision 2.32)**
  - [x] RED: (1) `PromptTemplatesTest`: the interviewer prompt always forbids invented questions, whatever the stored value. (2) The Studio form test: no invented-questions control. (3) The create API accepts and ignores an old `inventedQuestions` field.
  - [x] GREEN per design §4. Keep the DB column. Remove the `shortcut:` comment in `AttemptStateMachine` that names invented questions. Follow-up behaviour stays unchanged: existing follow-up tests stay green.
  - [x] Commit `refactor: remove the invented-questions toggle`.

- [ ] **Task 12: Prompt wording, prompt failure, bounded streams (REQ-CC-10, REQ-AC-23, REQ-AC-24)**
  - [ ] RED. (1) `PromptTemplatesTest`: core question 1 with `followUpLimit = 0` gives a prompt that contains "Ask this question now" and not "the next question comes next". (2) `AssessmentSessionServiceTest`: `buildInterviewerContext` throws → the configured question is sent in the same call, and `recordInterviewerReply` gets an empty text. (3) A context test asserts that the stream executor is a `ThreadPoolTaskExecutor` with max 32 and queue 100. (4) A rejected task gives `503` ProblemDetail.
  - [ ] GREEN per design §4. Commit `fix(assessment): ask the core question first, fall back at once, bound stream threads`.

- [ ] **Task 13: Expire and evaluate (REQ-AC-21, Decision 2.33)**
  - [ ] RED in `AttemptExpirySchedulerTest`. (1) An attempt in the BFI-10 step past its deadline becomes `COMPLETED` with no profile, and `AttemptCompletedEvent` is published. (2) A SOFT_SKILLS-only attempt that expires before the BFI-10 form also completes, and stays `pending` through Task 10.
  - [ ] GREEN. Add `// Decision 2.33`. Commit `fix(assessment): complete expired attempts without BFI-10`.

## Group E — Auth, invites, Studio backend

- [ ] **Task 14: Re-invite and concurrent creates (REQ-AA-10, REQ-AA-11, REQ-IS-14, Decision 2.34)**
  - [ ] RED. (1) `InviteServiceTest`: re-invite of a revoked candidate rotates the hash, sets `revoked = false`, and audits `INVITE_REISSUED`; the old token's session gets `401` on its next request, because the principal carries `tokenHash`. (2) A duplicate non-revoked invite → `409`. (3) A `DataIntegrityViolationException` from save → `409`, not `500`. (4) Studio: a unique violation on the idempotency key → re-read and replay, or `400` for another professor.
  - [ ] GREEN per design §5. No migration. Update `docs/api.md` (re-invite behaviour, `409`). Commit `fix(auth): allow re-invite after revoke and answer 409 on concurrent creates`.

- [ ] **Task 15: CSV, CSRF, rate limit, bcrypt (REQ-AA-12..15)**
  - [ ] RED. (1) The CSV parser test: a quoted comma, `""` and a line break inside quotes. Find where the CSV is parsed with `grep -rn -i csv frontend/src backend/src/main` and test it there. (2) A MockMvc test: a POST with the pre-login CSRF token after sign-in → `403`, and the new token → OK, for professor and invite login. (3) The 6th failed login in 15 min → `429` with `Retry-After`; a success resets the count (inject a `Clock`). (4) `ProfessorBootstrap` with an 80-byte password fails startup, and the message contains "72" and not the password.
  - [ ] GREEN per design §5. Update `docs/api.md` (`429`). Commit `fix(auth): renew CSRF on login, rate-limit sign-in, check bcrypt length, parse quoted CSV`.

- [ ] **Task 16: One list query (Studio)**
  - [ ] RED: a `@DataJpaTest` or Hibernate-statistics test asserts that listing 5 assessments runs a fixed number of queries, the same as for 1.
  - [ ] GREEN: one grouped count query in the repository. Commit `perf(studio): load assessment list counts in one query`.

## Group F — Teams and docs

- [ ] **Task 17: Teams precision and test clean-up (REQ-TB-13)**
  - [ ] RED: recalculate stores the same diagnostics as a fresh generate on the same teams (no 4-decimal rounding).
  - [ ] GREEN. Then: add `verify(repo).findByIdForUpdate(id)` for recalculate and publish; mark the three property tests `// design check, not required by spec`; delete the self-checking assert; merge the duplicated arrange blocks into one helper. All teams tests stay green.
  - [ ] Commit `fix(teams): store full-precision diagnostics and tidy tests`.

- [ ] **Task 18: Docs**
  - [ ] `.env.example`: the comment above the key says that the key, URL and model serve both the interviewer and the evaluator. Never open `.env`.
  - [ ] `docs/setup.md` troubleshooting: the "Interviewer call failed" log line (Decision 2.29).
  - [ ] `docs/feature_backlog.md`: set `mvp-hardening` to done at archive time.
  - [ ] Prose check: `python3 .agents/skills/asd-ste100/scripts/ste-lint.py <file>` on each changed doc.
  - [ ] Commit `docs: clarify shared LLM config and interviewer fallback`.

## Group G — Full verification

- [ ] **Task 19: Full checks**
  - [ ] `cd backend && ./mvnw -B test` → exit 0
  - [ ] `cd frontend && npm ci && npm run typecheck && npm run lint && npm run test && npm run build` → exit 0
  - [ ] `OPENSPEC_TELEMETRY=0 openspec validate --all --strict` → exit 0
  - [ ] `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0
  - [ ] Manual smoke on `docker compose up --build -d`, with the candidate in a private window: Inspect opens the modal, the transcript reads as a chat, the status refreshes after an interview, and a retake works.

## Delivery

- [ ] `git fetch origin && git merge origin/dev` (no rebase), re-run Task 19
- [ ] `OPENSPEC_TELEMETRY=0 openspec archive mvp-hardening -y`, replace any placeholder `## Purpose`, add the `docs/feature_history.md` entry (newest first)
- [ ] Ask the human, then `node scripts/forge.mjs push` and `node scripts/forge.mjs pr` (body via `pr-memo`)
- [ ] Cold `reviewer` (Opus) on `origin/dev...HEAD`; fix Blocking items
- [ ] GitHub checks green (GitLab has no CI minutes; accepted)
- [ ] Human merge approval → `gh pr merge fix/mvp-hardening --merge` → `node scripts/forge.mjs sync dev` → `node scripts/forge.mjs status` 0/0
- [ ] Branch and worktree clean-up

## Handoff Log

### Change authored
- Commits: (this commit) docs(specs): propose mvp-hardening change and ticket 06
- Verification: RED: n/a (spec text); GREEN: `OPENSPEC_TELEMETRY=0 openspec validate mvp-hardening --type change --strict` → exit 0
- Contracts/Migrations added: none yet; planned new endpoint `POST /api/v1/ranking/attempts/{attemptId}/evaluation/retry`; no migration planned
- Decisions: 2.31, 2.32, 2.33, 2.34 recorded (question-round, 2026-11-26)
- Open Questions: none

### Task 1: Shared Transcript dialog
- Commits: (this commit) feat(frontend): add readable transcript dialog
- Verification: RED: `npx vitest run src/components` → FAIL (`./transcript` and `./TranscriptDialog.vue` did not exist); GREEN: `npx vitest run src/components` → exit 0 (27 tests, 3 files)
- Contracts/Migrations added: none (new `frontend/src/components/transcript.ts`, `TranscriptDialog.vue`)
- Decisions: none new. Design choices: styling uses only the existing Tailwind tokens (`bg-surface-sunken`, `bg-green-soft`, `bg-brand-yellow-soft`, `text-ink*`), so it follows the tokens if dark mode is added (the app has `darkModeSelector: false` today). Extra helpers `formatClock`, `originLabels`, `splitHighlights` live in `transcript.ts` for unit tests. Extra optional props `loading` and `error` give the dialog the loading/error states that frontend rule 2 needs.
- Open Questions: none

### Task 2: Evidence review in a modal
- Commits: (this commit) feat(frontend): open evidence review and transcript as modals
- Verification: RED: `npx vitest run src/views/ranking src/views/assessment` → FAIL (`./EvidenceDialog.vue` did not exist; ResultsView still showed the inline log); GREEN: `npx vitest run src/views/ranking src/views/assessment src/components` → exit 0; full `npm run typecheck && npm run lint && npm run test && npm run build` → exit 0 (132 tests)
- Contracts/Migrations added: none (frontend only; `EvidenceDrawer.vue` renamed to `EvidenceDialog.vue`, no API change)
- Decisions: no new ID. Where design differed from code: (1) The drilldown API has no `candidateRef` yet, so the dialog header shows the short invite id through one `candidateName` computed, with a `shortcut:` note; Tasks 3 and 4 switch it. (2) PrimeVue binds Esc on the document for every open Dialog, so the evidence dialog sets `:close-on-escape="!transcriptOpen"`; one Esc closes only the top dialog. (3) "View transcript" and "Show in transcript" both pass every verified quote as `highlight`; only "Show in transcript" sets `focusEntryId`. (4) The Release Status tile was dropped because the header now carries that tag. (5) Release gating is unchanged (Task 3 owns `evaluationStatus`).
- Open Questions: none

### Task 3: Live status and Release state
- Commits: (this commit) fix: refresh evaluation status and gate the release button
- Verification: RED: `./mvnw -B test -Dtest='EvaluationReviewServiceTest,RankingEvaluationControllerTest'` → FAIL (compile: no `candidateRef`, `evaluationStatus`, `InviteService.candidateRefs`); `npx vitest run src/views/ranking/RankingLive.test.ts` → FAIL (9 of 10: no polling, no Refresh button, no tooltip); GREEN: `./mvnw -B test -Dtest='EvaluationReviewServiceTest,RankingEvaluationControllerTest,FormulaPresetServiceTest,InviteServiceTest,ArchitectureTest'` → exit 0 (41 run); `npx vitest run src/views` + `npm run typecheck && npm run lint` → exit 0 (70 run)
- Contracts/Migrations added: `docs/api.md` (matrix row `candidateRef`; drilldown `candidateRef` and `evaluationStatus`; formula preview `candidateRef`); no migration
- Decisions: no new ID. Design differed from code: (1) The DTO is named `AttemptEvaluationDrilldownResponse`, not `AttemptEvaluationsResponse`. (2) No suitable auth read method existed, so `InviteService.candidateRefs(configId)` is new (owner-checked, one query per config); it returns a map of invite ID to `candidateRef`. The evaluation slice never touches the invite repository. A missing invite falls back to the invite ID text, so the field is never null. (3) `FormulaPreviewCandidate` also gets `candidateRef`, because REQ-SM-12 lists the formula preview and the preview panel has no matrix data. (4) A disabled PrimeVue Button gets no hover events, so the Release tooltip is a native `title` on a wrapping span. (5) The candidate results page also gets a Refresh button. (6) The `settle` delay in `RankingViews.test.ts` went from 50 ms to 200 ms; the Esc test was flaky under parallel load.
- Open Questions: none

### Task 4: Names, route sync, Teams link, cache key
- Commits: (this commit) fix(frontend): show candidate names and sync the ranking route
- Verification: RED: `npx vitest run src/views/ranking/RankingNav.test.ts` → FAIL (7 of 7: UUID prefix labels, no route sync, no Build teams link, key `studio-assessments`); GREEN: `npm run typecheck && npm run lint && npx vitest run` → exit 0 (149 tests, 23 files); `grep -rn "inviteId.slice" frontend/src` → empty
- Contracts/Migrations added: none (frontend only; the backend `candidateRef` fields came with Task 3)
- Decisions: no new ID. Design differed from code: (1) The shared key is the constant `ASSESSMENTS_KEY` in `api/studio.ts` (Studio list, Studio delete, Ranking), not a repeated literal. (2) The `shortcut:` comment in `EvidenceDialog` is gone; `candidateName` reads `candidateRef`. (3) Ranking has two route records (`ranking`, `ranking-config`); the dropdown calls `router.replace({ name: 'ranking-config' })`, also for the first default selection. The `RankingViews.test.ts` router now has that route. (4) The Build teams link uses the path string `/ranking/teams/<id>`, like the existing Studio link.
- Open Questions: none

### Task 5: Retake UI
- Commits: (this commit) fix(assessment): let candidates start a retake
- Verification: RED: `./mvnw -B test -Dtest='AttemptStateMachineTest'` → FAIL (compile: no `attemptsLeft`); `npx vitest run src/views/assessment/AssessmentChatView.test.ts` → FAIL (4 of 11 new retake tests); GREEN: `./mvnw -B test -Dtest='AttemptStateMachineTest,AssessmentSessionServiceIntegrationTest,AssessmentSessionControllerTest,AssessmentSessionServiceTest,AssessmentSpecInvariantPropertyTest,ArchitectureTest'` → exit 0 (73 run); `npx vitest run src/views/assessment` + `npm run typecheck && npm run lint` → exit 0 (16 run)
- Contracts/Migrations added: `docs/api.md` (`SessionStateResponse.attemptsLeft`); no migration
- Decisions: no new ID. Design differed from code: (1) `attemptsLeft = max(0, maxAttempts - attempts of the invite)` is the pure function `AttemptStateMachine.attemptsLeft`; `AttemptService.stateOf` adds one `countByInviteId` query per state read. (2) The `sessionStorage` key is cleared when the candidate clicks "Start new attempt", not on read, so a reload of a finished attempt still shows the thank-you page. (3) The page cannot know `attemptsLeft` in a new tab (no stored attempt, no session endpoint without an attempt ID). The start button stays there; a `409` on start with no open attempt sets `attemptsLeft = 0` and the page shows "No attempts left" with no button. This meets the "No attempts left" scenario after one click, not on page load. A proactive answer needs a new read endpoint; not added. (4) An attempt that ends inside the page (SSE `done` or BFI-10 submit) fetches the session once to get `attemptsLeft`.
- Open Questions: none (see Decision 3 if the page-load wording of REQ-AC-22 must hold in a new tab)

### Task 6: Shell errors, 404 and invites scroll
- Commits: (this commit) fix(frontend): handle server errors, unknown routes and storage failures
- Verification: RED: `npx vitest run src/App.test.ts src/views/auth/NotFoundView.test.ts src/views/studio/StudioViews.test.ts src/stores/assessmentForm.test.ts` → FAIL (NotFoundView missing; no server-down or sign-out message; no scroll; `setItem` threw); GREEN: `npm run typecheck && npm run lint && npm run test && npm run build` → exit 0 (160 tests, 24 files); `grep -rn "schema" frontend/src` (after the delete) → empty
- Contracts/Migrations added: none (frontend only; `frontend/src/api/schema.d.ts` deleted, no import existed)
- Decisions: no new ID. Design differed from code: (1) The invites scroll runs from a `watch` on the loaded assessment, not `onMounted`: the `#invites` panel only exists after the query returns, so a mount-time scroll finds nothing. It scrolls through a template ref on `InvitePanel`. (2) `fetchMe` already swallows a `401`, so `App.vue` treats any error it throws as "Server not reachable" (network error or 5xx). (3) The route guard calls `fetchMe` too, so a down server also aborts the first navigation. Retry therefore re-runs the first navigation from the browser URL when the router is still at `START_LOCATION` (covered by a test). (4) `package.json` keeps the `api:generate` script; it recreates `schema.d.ts` on demand and nothing imports the file. (5) `assessmentForm.ts` wraps both `setItem` and `removeItem`, with a `shortcut:` note (no "draft not saved" notice yet).
- Open Questions: none

### Task 7: Tolerant judge parsing
- Commits: (this commit) fix(evaluation): accept fenced judge JSON and call the judge at temperature 0
- Verification: RED: `./mvnw -B test -Dtest='TwoRunResponseJudgeTest'` → FAIL (4 of 9: fenced and prose-wrapped replies failed; prompt options had no temperature); GREEN: `./mvnw -B test -Dtest='TwoRunResponseJudgeTest,AsyncEvaluationRunnerTest,EvaluationSpecInvariantPropertyTest,EvaluationInvariantPropertyTest'` → exit 0 (42 run)
- Contracts/Migrations added: none
- Decisions: no new ID. Design differed from code: (1) No separate fence-strip step. "First `{` to last `}`" already removes both fence lines, so one extraction covers fence and prose (`shortcut:` comment for a stray `{` in leading prose). (2) The temperature uses the portable `ChatOptions.builder().temperature(0.0)` per call instead of `OpenAiChatOptions`, so a provider switch stays config-only (backend rule 4); Spring AI maps it onto the OpenAI options. (3) `EvaluationInvariantPropertyTest` listed "Here is JSON: {valid}" as a bad payload; REQ-EP-10 now makes it valid, so that one payload was removed.
- Open Questions: none

### Task 8: Final-try crash, re-run, lost events
- Commits: (this commit) fix(evaluation): fail stale final tries, recover missed runs, add professor re-run
- Verification: RED: `./mvnw -B test -Dtest='EvaluationRetrySchedulerTest,EvaluationReviewServiceTest,RankingEvaluationControllerTest,AssessmentQueryServiceImplTest'` → FAIL (compile: no `findStaleFinalTryIds`, `completedAttemptsSince`, `retryEvaluation`, `RetryEvaluationResponse`); `npx vitest run src/views/ranking/RankingLive.test.ts` → FAIL (1 of 14: no Re-run button); GREEN: `./mvnw -B test -Dtest='EvaluationRetrySchedulerTest,EvaluationReviewServiceTest,RankingEvaluationControllerTest,AssessmentQueryServiceImplTest,AsyncEvaluationRunnerTest,EvaluationRetrySchedulerIntegrationTest,ArchitectureTest,AssessmentSpecInvariantPropertyTest'` → exit 0 (64 run); `npx vitest run src/views/ranking` + `npm run typecheck && npm run lint` → exit 0 (30 run)
- Contracts/Migrations added: `docs/api.md` (`POST /api/v1/ranking/attempts/{attemptId}/evaluation/retry`, `RetryEvaluationResponse`); `AssessmentQueryService.completedAttemptsSince(Instant)`; no migration
- Decisions: no new ID. Design differed from code: (1) The new assessment method is `completedAttemptsSince(Instant since)` and returns `AttemptCompletedEvent` (attempt, config, invite ids), not `completedAttemptIdsWithoutEvaluation`. The assessment slice cannot see which attempts have a run, and `enqueue` already skips known attempts, so evaluation re-submits every attempt completed in the last 7 days (`shortcut:` constant `RECOVERY_WINDOW`, one lookup each per pass). (2) The two scheduler steps live in `AsyncEvaluationRunner` (`failStaleFinalTries`, `recoverMissedRuns`), which already owns `maxAttempts`, `staleAfter` and `enqueue`; `EvaluationRetryScheduler.retryPass` calls both before it dispatches. (3) The re-run sets the run to `QUEUED` and does not dispatch at once; the scheduler (1 min) picks it up. (4) `EVALUATION_RETRIED` is a plain audit action string (no enum exists), entity `ATTEMPT`, old `FAILED`, new `QUEUED`. (5) The `shortcut:` note in `EvaluationEventListener` about the lost event is replaced by a pointer to the recovery.
- Open Questions: none

### Task 9: Snapshot weights and lock order
- Commits: (this commit) fix(evaluation): score with the stored formula version and lock the summary first
- Verification: RED: `./mvnw -B test -Dtest='AsyncEvaluationRunnerTest,EvaluationCleanupListenerTest,EvaluationReviewServiceTest'` → FAIL (3 tests: score 2.00 from the live weights instead of 1.33; approve and override wrote before any summary lock; the purge never locked); GREEN: `./mvnw -B test -Dtest='AsyncEvaluationRunnerTest,EvaluationCleanupListenerTest,EvaluationReviewServiceTest,EvaluationLifecycleIntegrationTest,EvaluationReleaseRaceTest,EvaluationSchemaAndCleanupTest,AsyncEvaluationRunnerIntegrationTest,FormulaPresetServiceTest,ArchitectureTest'` → exit 0 (59 run)
- Contracts/Migrations added: `docs/api.md` (approve, override: lock order and stored weights); repository methods `ResponseEvaluationRepository.findAttemptIdById`, `CandidateEvaluationSummaryRepository.findByConfigIdForUpdate`; no migration
- Decisions: no new ID. Design differed from code: (1) Formula version 1 was already created at enqueue (`ensureFormulaVersion`); the real gap was that `executeRun` read weights from the live `ScoringView`. It now reads them, and each pair's `questionWeight`, from `formulaVersion.getQuestionWeightsJson()`. A question missing from the snapshot falls back to the live weight, like `FormulaRecalculationHelper` does. (2) Approve and override resolve the attempt through a scalar `findAttemptIdById`, lock the summary with `findByAttemptIdForUpdate`, and only then load the response, so no stale response is in the session before the lock. The ownership check moved after the lock (a foreign professor still gets `404`). (3) The purge locks all summary rows of the configuration with one `findByConfigIdForUpdate` (ordered by id), the config-level form of the attempt lock, because the purge has a config ID and no attempt ID. (4) The optional Testcontainers race test is skipped. (5) The runner's own completion write keeps `findById`; nothing else writes an attempt's rows before it completes.
- Open Questions: none

### Task 10: Soft-only auto-release
- Commits: (this commit) fix(evaluation): auto-release soft-skills-only attempts
- Verification: RED: `./mvnw -B test -Dtest='ReleaseGateEvaluatorTest,AsyncEvaluationRunnerTest'` → FAIL (compile: `evaluateAttemptRelease` has no `hardSkillCount` and `softProfileComplete`); GREEN: `./mvnw -B test -Dtest='ReleaseGateEvaluatorTest,AsyncEvaluationRunnerTest,EvaluationSpecInvariantPropertyTest,EvaluationInvariantPropertyTest,EvaluationLifecycleIntegrationTest,AsyncEvaluationRunnerIntegrationTest,EvaluationRetrySchedulerIntegrationTest'` → exit 0 (52 run)
- Contracts/Migrations added: none (`ReleaseGateEvaluator.evaluateAttemptRelease` gains two parameters; no endpoint change, so no `docs/api.md` change)
- Decisions: 2.31 applied (`// Decision 2.31` in `ReleaseGateEvaluator`). Design differed from code: (1) `hardSkillCount` is `evaluatorView.skills().size()`, which the runner already loads; Studio skills are the hard skills (the BFI-10 traits are not Studio skills), so `StudioQueryService` needs no new method. (2) `softProfileComplete` uses the existing `AssessmentQueryService.getSoftSkillProfileForAttempt`: the assessment slice sets `softSkillScores` only when all five traits are scored, so a non-null map means complete. The lookup runs only when `hardSkillCount == 0`. (3) The gate changed its signature (no overload); the property tests pass 2 hard skills and no profile, which keeps their old behaviour. (4) The zero-hard-skills branch returns before the coverage check, because a soft-only assessment has no core questions and coverage is 0 by definition.
- Open Questions: none

### User request: no candidate sign-out (Decision 2.36)
- Commits: (this commit) feat(frontend): hide sign-out for candidates
- Verification: RED: n/a (one-line template guard; existing App header test updated); GREEN: `npx vitest run src/App.test.ts` → exit 0 (7 run); `npm run typecheck` → exit 0; `openspec validate mvp-hardening --type change --strict` → valid
- Contracts/Migrations added: none. The delta spec modifies REQ-NAV-02 (no candidate "Sign out") and REQ-NAV-05 (professor only).
- Decisions: 2.36 recorded (prompter change).
- Open Questions: none

### Task 11: Remove the invented-questions toggle
- Commits: (this commit) refactor: remove the invented-questions toggle
- Verification: RED: `./mvnw -B test -Dtest='PromptTemplatesTest'` → FAIL (compile: `InterviewerContextInput` still requires the flag); `npx vitest run src/views/studio/StudioViews.test.ts` → FAIL (form still shows "Allow interviewer-invented questions"); GREEN: `./mvnw -B test -Dtest='PromptTemplatesTest,InterviewerContextBuilderTest,StudioConfigControllerTest,ContextInvariantPropertyTest,AttemptStateMachineTest,ContextControllerTest,ContextCreationServiceImplTest,ConfigValidatorTest,ConfigCreationAndQueryIntegrationTest,AssessmentReadAndDeletionIntegrationTest,AssessmentSessionServiceIntegrationTest,AssessmentInvariantPropertyTest,AssessmentSpecInvariantPropertyTest,InviteRevocationAndPurgeTest'` → exit 0 (124 run); `npx vitest run src/views/studio` → exit 0 (19 run); typecheck and lint → exit 0
- Contracts/Migrations added: `docs/api.md` (preview request loses `inventedQuestionsEnabled`; create ignores an old `inventedQuestions`; detail response has no such field); no migration (column `invented_questions` stays, new rows store `false`)
- Decisions: 2.32 applied (`// Decision 2.32` in `PromptTemplates` and `ConfigCreationService`). Design differed from code: the field also lived in `InterviewerView`, `InterviewerPreviewRequest`, `AssessmentDetailResponse` and the Studio detail page. All lose it (a dead "Invented questions: Off" row would mislead). The `AttemptStateMachine` shortcut keeps its follow-up part and loses the invented part. Jackson ignores unknown JSON fields, so no extra code serves old clients (test added). `AssessmentConfigEntity` keeps its constructor parameter, and `isInventedQuestions()` is removed (no reader).
- Open Questions: none
