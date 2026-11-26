# Tasks: MVP Hardening (`mvp-hardening`)

## Status
- **Branch:** `fix/mvp-hardening` (worktree `../nifty-mvp-hardening`, from `origin/dev` `d0480e6`)
- **Current:** Tasks 1 and 2 done (Group A complete, committed, not pushed).
- **Next action:** Task 3 (Group B, live status and Release state; needs the backend `evaluationStatus` and `candidateRef` fields first).
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

- [ ] **Task 3: Live status and Release state (REQ-SM-11, REQ-CR-07)**
  - [ ] Backend first: `AttemptEvaluationsResponse` gains `evaluationStatus` and `candidateRef`, and matrix rows gain `candidateRef` (design §3, names and status). RED: controller test asserts both fields. GREEN. Update `docs/api.md`.
  - [ ] Frontend RED: (1) the matrix query refetches while any row is `QUEUED` or `RUNNING` (fake timers, 5 s) and stops when all rows are `COMPLETED`; (2) Release is disabled for `RUNNING` and `FAILED`, with the tooltip text; (3) ResultsView refetches while `released === false`.
  - [ ] GREEN: `refetchInterval` plus a Refresh button per design §2.
  - [ ] Commit `fix: refresh evaluation status and gate the release button`.

- [ ] **Task 4: Names, route sync, Teams link, cache key (REQ-SM-12, REQ-NAV-11)**
  - [ ] RED: (1) the matrix label shows `candidateRef (#n)`; no `inviteId.slice` is left (`grep -rn "inviteId.slice" frontend/src` is empty); (2) a route param change A → B updates the selection; (3) a dropdown change calls `router.replace`; (4) the "Build teams" link points to `/ranking/teams/<id>`; (5) a Studio delete invalidates the key that Ranking reads.
  - [ ] GREEN per design §2.
  - [ ] Commit `fix(frontend): show candidate names and sync the ranking route`.

- [ ] **Task 5: Retake UI (REQ-AC-22)**
  - [ ] Backend RED: `SessionStateResponse.attemptsLeft` is correct for 0, 1 and 2 used of a maximum of 2. GREEN. Update `docs/api.md`.
  - [ ] Frontend RED: (1) a stored `COMPLETED` attempt with `attemptsLeft = 1` shows "Start new attempt" and clears the `sessionStorage` key on start; (2) `attemptsLeft = 0` shows "No attempts left" and no start button.
  - [ ] GREEN. Commit `fix(assessment): let candidates start a retake`.

- [ ] **Task 6: Shell errors, 404 and invites scroll (REQ-NAV-10, REQ-NAV-12, REQ-NAV-13)**
  - [ ] RED: (1) an unknown path renders `NotFoundView` with the role start link; (2) a network error in `fetchMe` shows "Server not reachable", and Retry calls it again; (3) a `500` from `logout` shows "Sign-out failed. Try again." and does not navigate; (4) the `studio-invites` route scrolls `#invites` into view (mock `scrollIntoView`).
  - [ ] GREEN per design §2. Delete `frontend/src/api/schema.d.ts` after `grep -rn "schema" frontend/src` shows no import.
  - [ ] Safe `localStorage`: RED test where `setItem` throws and the form store still updates. GREEN: `try/catch` in `stores/assessmentForm.ts`.
  - [ ] Commit `fix(frontend): handle server errors, unknown routes and storage failures`.

## Group C — Evaluation backend

- [ ] **Task 7: Tolerant judge parsing (REQ-EP-10)**
  - [ ] RED in `TwoRunResponseJudgeTest`: a fenced reply ` ```json\n{…}\n``` ` parses; text before and after the object parses; `level` 7 inside a fence still fails; the judge call options carry temperature `0.0` (captor on the prompt).
  - [ ] GREEN per design §3. Commit `fix(evaluation): accept fenced judge JSON and call the judge at temperature 0`.

- [ ] **Task 8: Final-try crash, re-run, lost events (REQ-EP-11..13)**
  - [ ] RED. (1) `EvaluationRetrySchedulerTest`: a stale `RUNNING` run at max tries becomes `FAILED` with `STALE_FINAL_TRY`. (2) The same test: a completed attempt with no run gets exactly one run, also when the pass runs twice. (3) Controller and service tests for `POST /api/v1/ranking/attempts/{attemptId}/evaluation/retry`: `FAILED` → `QUEUED`, tries 0, audit `EVALUATION_RETRIED`; `COMPLETED` → `409`; another professor's attempt → `404`.
  - [ ] GREEN per design §3. The new assessment read method lives in `AssessmentQueryService` and its implementation, with no cross-slice repository access. Add the audit action. Update `docs/api.md`. The Evidence dialog shows "Re-run evaluation" for `FAILED`, with a frontend test.
  - [ ] Commit `fix(evaluation): fail stale final tries, recover missed runs, add professor re-run`.

- [ ] **Task 9: Snapshot weights and lock order (REQ-FE-07, REQ-SM-14)**
  - [ ] RED: (1) the first scoring creates formula version 1 and uses its stored weights. Change the live weights in the test stub, and the score must not change. (2) Mockito `verify(summaryRepo).findByAttemptIdForUpdate(id)` is called before any response-evaluation write, for approve, override and the cleanup listener. Use `InOrder`.
  - [ ] Optional race test (Testcontainers, like `EvaluationReleaseRaceTest`): an approve and a release at the same time both finish, neither with a `500`.
  - [ ] GREEN per design §3. Commit `fix(evaluation): score with the stored formula version and lock the summary first`.

- [ ] **Task 10: Soft-only auto-release (REQ-SM-13, Decision 2.31)**
  - [ ] RED in `ReleaseGateEvaluatorTest`: zero hard skills with a complete profile → `AUTO_RELEASED`; zero hard skills without a profile → `PENDING`; 3 hard skills with no evidence → `PENDING`.
  - [ ] GREEN: pass `hardSkillCount` (from `StudioQueryService`) and `softProfileComplete` (from `AssessmentQueryService`) into the gate. Add `// Decision 2.31`. Add one `AsyncEvaluationRunner` test for a soft-only attempt end to end with mocks.
  - [ ] Commit `fix(evaluation): auto-release soft-skills-only attempts`.

## Group D — Assessment and context backend

- [ ] **Task 11: Remove the invented-questions toggle (REQ-AC-02, REQ-IS-05, REQ-CC-02, Decision 2.32)**
  - [ ] RED: (1) `PromptTemplatesTest`: the interviewer prompt always forbids invented questions, whatever the stored value. (2) The Studio form test: no invented-questions control. (3) The create API accepts and ignores an old `inventedQuestions` field.
  - [ ] GREEN per design §4. Keep the DB column. Remove the `shortcut:` comment in `AttemptStateMachine` that names invented questions. Follow-up behaviour stays unchanged: existing follow-up tests stay green.
  - [ ] Commit `refactor: remove the invented-questions toggle`.

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
