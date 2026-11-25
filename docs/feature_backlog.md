# Feature Backlog

Planned features in delivery waves (Decision 3.14). Use this file to choose the next change and to find work that can run in parallel.

- **Status:** `queued` (you can start it when its dependencies have merged), `in progress` (a change exists, its name is in the Feature column), `blocked` (an open decision must close first). No feature is blocked: the 2026-11-25 decisions closed O1–O7.
- **Wave:** features in the same wave can run in parallel. Start a wave when the features it depends on have merged.
- **Slice:** the backend package and frontend views from the [Feature Slice Map](architecture.md#feature-slice-map). Each feature works only in its own slice.
- **Migration:** the Flyway file is reserved here, so parallel worktrees never use the same `V<n>`. A feature that needs one more file claims the next free number above `V9` in this table first.
- **Stories:** each `US-` story is one RED → GREEN → REFACTOR loop and one commit. Copy the stories of a feature into its `tasks.md` when you propose the change.

Lifecycle (see `AGENTS.md` §3): when you propose a change, set its row to `in progress` and add the change name. When you archive the change, remove its row and add an entry to [`feature_history.md`](feature_history.md).

## MVP Features

| # | Feature (spec) | Wave | Status | Slice | Depends on | Migration |
|---|---|---|---|---|---|---|
| 1 | `backend-foundation` | 0 | queued | `shared`, `backend/pom.xml`, CI `backend` jobs | — | — |
| 2 | `frontend-foundation` | 0 | queued | `frontend/` shell, CI `frontend` jobs | — | — |
| 3 | `auth-accounts` ([`auth-access`](../openspec/specs/auth-access/spec.md)) | 1 | queued | `auth`, `views/auth/` | 1, 2 | `V2__auth_accounts.sql` |
| 4 | `audit-log-service` | 1 | queued | `audit` | 1 | — (uses `V1`) |
| 5 | [`interviewer-studio`](../openspec/specs/interviewer-studio/spec.md) | 2 | queued | `studio`, `views/studio/` | 3, 4 | `V3__studio.sql` |
| 6 | `invite-links` ([`auth-access`](../openspec/specs/auth-access/spec.md)) | 3 | queued | `auth`, `views/studio/` (invite panel) | 5 | `V4__auth_invites.sql` |
| 7 | [`formula-engine`](../openspec/specs/formula-engine/spec.md) | 3 | queued | `formula`, `views/ranking/` (formula presets) | 5 | `V5__formula.sql` |
| 8 | [`assessment-chat`](../openspec/specs/assessment-chat/spec.md) | 4 | queued | `assessment`, `views/assessment/` | 5, 6 | `V6__assessment.sql` |
| 9 | [`evidence-pipeline`](../openspec/specs/evidence-pipeline/spec.md) | 4 | queued | `evidence` (backend only) | 5, 7, ledger from 8 (start on fixture ledger rows) | `V7__evidence.sql` |
| 10 | [`skills-matrix-review`](../openspec/specs/skills-matrix-review/spec.md) | 5 | queued | `matrix`, `views/ranking/` | 7, 9 | `V8__matrix.sql` |
| 11 | [`candidate-results`](../openspec/specs/candidate-results/spec.md) | 6 | queued | `results`, `views/assessment/` (results page) | 10 | — |
| 12 | [`team-builder`](../openspec/specs/team-builder/spec.md) | 6 | queued | `teams`, `views/teams/` | 8 (BFI-10), 10 | `V9__teams.sql` |
| 13 | `harness-review-followups` | any | queued | harness, CI, `shared` | — | — |

`auth-access` is a new spec. It exists in `openspec/specs/` after the `nov24-reconciliation` change is archived.

## Stories

### 1. `backend-foundation`
Check each dependency change against `docs/ai_docs/` first.
- **US-BF-01:** Replace `spring-boot-starter-web` with `spring-boot-starter-webmvc`. All existing tests pass.
- **US-BF-02:** Add `spring-boot-starter-data-jpa-test` and a PostgreSQL Testcontainers base class. One `@DataJpaTest` runs Flyway `V1` on PostgreSQL.
- **US-BF-03:** Add a `docker:dind` service to the GitLab `backend` job, so Testcontainers runs on both forges. The CI drift test passes.
- **US-BF-04:** Add `timefold-solver-spring-boot-starter`. Move `spring.ai.openai.chat.options.model` to `spring.ai.openai.chat.model`. The app boots with no LLM key.

### 2. `frontend-foundation`
- **US-FF-01:** Add Vitest and `@vue/test-utils`, an `npm run test` script, and the test step in both CI files.
- **US-FF-02:** Install PrimeVue. Define the design tokens as Tailwind `@theme` variables and map them into one PrimeVue preset. A component test shows `ScoreBadge` for levels 0–4 and hatched `N/A`.
- **US-FF-03:** Move `RankingView.vue` and `AssessmentView.vue` into `views/ranking/` and `views/assessment/`. Routes stay the same.
- **US-FF-04:** Add a shared API client. It parses ProblemDetail errors and reads an SSE stream from a `POST` response (`fetch` + `ReadableStream`, because `EventSource` cannot send a `POST`).

### 3. `auth-accounts`
- **US-AU-01:** Reduce `Role` to `PROFESSOR` and `CANDIDATE`. Apply the path rules of REQ-AA-01. Test `403` in both directions.
- **US-AU-02:** Add professor accounts with a hashed password (`DelegatingPasswordEncoder`) and sign-in. Unknown email and wrong password give the same `401` (REQ-AA-02). The design picks JWT or a server session.
- **US-AU-03:** Add the sign-in page in `views/auth/` and router guards for `/ranking/*` and `/assessment/*`.

### 4. `audit-log-service`
- **US-AL-01:** Add the `AuditLog` entity over `V1` and `AuditService.record(actor, entity, action, oldValue, newValue, reason)`. ArchUnit: no class outside `audit` uses its repository.
- **US-AL-02:** The service has no update or delete method. A test shows that an existing row stays unchanged.

### 5. `interviewer-studio`
- **US-IS-01:** The professor saves a draft configuration from the form with every REQ-IS-05 field.
- **US-IS-02:** Invalid values give `400` with one `errors` entry per field (REQ-IS-06).
- **US-IS-03:** Publishing creates an immutable version. A draft cannot start a session (REQ-IS-01, REQ-IS-04).
- **US-IS-04:** A new skill or core question after interviews creates a new version and marks earlier candidates incomplete (REQ-IS-03).
- **US-IS-05:** Tags go through `pending → approved | rejected | merged` in the `studio` service (REQ-IS-02).
- **US-IS-06:** Deleting an assessment publishes `AssessmentDeletedEvent`. Each slice deletes its own rows in the same transaction, and `audit` records the deletion (REQ-IS-07). Later slices add their listener in their own change.
- **US-IS-07:** The assessment form UI in `views/studio/` (PrimeVue).

### 6. `invite-links`
- **US-IL-01:** Create one invite: a random token of at least 128 bits, stored only as a hash (REQ-AA-03).
- **US-IL-02:** CSV import creates one invite per row and returns the links.
- **US-IL-03:** Invite sign-in starts a `CANDIDATE` session. A revoked or unknown token gives `401` (REQ-AA-04).
- **US-IL-04:** One scope check limits candidate requests to their own assessment and candidate (REQ-AA-05).

### 7. `formula-engine`
- **US-FE-01:** A preset catalogue, with the canvas §11 formula as the default. The professor picks one preset per assessment. Free-form expressions give `400` (REQ-FE-05).
- **US-FE-02:** A pure `score(s)` calculator (canvas §11.1), with unit tests.
- **US-FE-03:** `fit` over scored skills and `coverage` over weighted core questions. Timed-out questions count as unanswered (REQ-FE-03).
- **US-FE-04:** A weight change creates a new immutable formula version (REQ-FE-02).
- **US-FE-05:** What-if preview on stored candidates, without saving.
- **US-FE-06:** The formula preset UI in `views/ranking/`.

### 8. `assessment-chat`
- **US-AC-01:** Add the ledger table with a trigger that blocks `UPDATE` and an unflagged `DELETE` (REQ-AC-04).
- **US-AC-02:** Starting a session streams the greeting and question 1 with `expiresAt`. The attempt limit gives `409` (REQ-AC-05, REQ-AC-11).
- **US-AC-03:** An answer streams the reply. The state machine checks question order and the follow-up limit, and answers `409` for a disallowed input (REQ-AC-01, REQ-AC-05).
- **US-AC-04:** Deadlines: store `expiresAt`, set `timedOut`, give `409` after the grace period, and close expired questions on the next request (REQ-AC-07, REQ-AC-08).
- **US-AC-05:** An answer under the minimum time gets `pasteSuspected` (REQ-AC-09).
- **US-AC-06:** `GET` returns the session state from the ledger for a page refresh (REQ-AC-06).
- **US-AC-07:** The interviewer prompt builder contains no rubric or score data and has the guardrails. A test captures the prompt (REQ-AC-12, REQ-AC-13).
- **US-AC-08:** The BFI-10 step, scoring and storage. No BFI-10 data reaches the evaluator (REQ-AC-14..16).
- **US-AC-09:** The `assessment-transcript/v1` export is built from the ledger (REQ-AC-04).
- **US-AC-10:** Completion publishes `AttemptCompletedEvent`.
- **US-AC-11:** The chat UI shows the countdown, auto-submits at zero, and shows questions left. It has no pause and no scores (REQ-AC-03, REQ-AC-10).

### 9. `evidence-pipeline`
- **US-EP-01:** An `AFTER_COMMIT` listener starts the evaluator asynchronously. A rollback starts nothing (REQ-EP-05).
- **US-EP-02:** The evaluator call returns schema-validated evidence items. Tests replay recorded `ChatClient` fixtures.
- **US-EP-03:** The quote verifier discards quotes that are not word-for-word in the entry (REQ-EP-01).
- **US-EP-04:** A 2-run judge and the (question, skill) confidence calculator (REQ-EP-03).
- **US-EP-05:** `N/A` for no verified evidence and for timed-out questions (REQ-EP-02, REQ-EP-06).
- **US-EP-06:** Scores from paste-suspected entries become `flagged` (REQ-EP-07).
- **US-EP-07:** Tag suggestions go through the `studio` service (REQ-IS-02).
- **US-EP-08:** No number from LLM output enters a score without validation (REQ-EP-04).

### 10. `skills-matrix-review`
- **US-SM-01:** The release gate sets `auto_released` or sends the attempt to the queue (REQ-SM-01, REQ-SM-05, REQ-SM-06).
- **US-SM-02:** The "Needs attention" queue: approve or override each score. An override needs a reason and calls `AuditService` (REQ-SM-02).
- **US-SM-03:** Bulk approval skips flagged scores (REQ-SM-03).
- **US-SM-04:** Only the latest attempt counts (REQ-SM-07).
- **US-SM-05:** The Cohort Gap SQL uses released scores of counted attempts (REQ-SM-04).
- **US-SM-06:** The professor's conversation log with `timedOut` and `pasteSuspected` markers (REQ-SM-08).
- **US-SM-07:** The matrix heatmap UI (ECharts): hatched `N/A`, flagged border, confidence as opacity.

### 11. `candidate-results`
- **US-CR-01:** The results endpoint opens only after release (REQ-CR-01, REQ-CR-02).
- **US-CR-02:** A candidate who asks for another candidate's results gets `403` (REQ-CR-03).
- **US-CR-03:** The candidate's own conversation log (REQ-CR-04).
- **US-CR-04:** Code selects strengths and improvement areas. An optional AI sentence is labelled (REQ-CR-05).
- **US-CR-05:** The candidate's own BFI-10 trait scores in neutral words (REQ-CR-06).
- **US-CR-06:** The results page UI.

### 12. `team-builder`
- **US-TB-01:** The Timefold model with hard constraints: configured team size within one, each candidate in exactly one team (REQ-TB-05).
- **US-TB-02:** Soft constraints: balanced fit and at least one strong member per key skill.
- **US-TB-03:** The BFI-10 balance soft constraint with the configured weight (REQ-TB-06).
- **US-TB-04:** Drag-and-drop recalculates balance without an LLM (REQ-TB-03).
- **US-TB-05:** Mode B parses text in a fresh LLM context. The professor confirms the constraints (REQ-TB-02).
- **US-TB-06:** Teams stay in draft until the professor publishes them (REQ-TB-04).
- **US-TB-07:** The team builder UI. Trait scores show only to the professor (REQ-TB-07).

## Post-MVP (Nice to Have)

- Team Builder Mode C: a validated LLM team proposal (REQ-TB-01 stays as its guard).
- Case-study and project pool matching for teams (Decision 4.2).
- LLM-suggested core questions in the Studio (Decision 4.2).
- The sandboxed SpEL formula editor (Decision 2.19 removed REQ-FE-04).
- A full dispute workflow on the results page.
- An onboarding self-assessment, and re-probe or multi-session interviews.
- The recruiter persona (P4) and a candidate-owned skill passport.
- The Ask AI mode of `SearchBar`.
- A `REVIEWER` role for teaching assistants (Decision 1.15 keeps this to one enum value and a few rules).

## Notes

- **Wave 0 touches CI:** rows 1 and 2 edit different jobs in `.github/workflows/ci.yml` and `.gitlab-ci.yml`. Merge one first, and merge `origin/dev` into the other before its PR.
- **Shared `views/ranking/`:** rows 7 and 10 use the same folder. Row 10 starts after row 7, so they do not overlap.
- **Shared `views/assessment/`:** rows 8 and 11 use the same folder. Row 11 starts after row 8.
- **Code drift until row 3 merges:** `Role.java` and `SecurityConfig` still have `REVIEWER`.
- **`harness-review-followups`:** non-blocking findings from the 2026-11-24 harness reviews (see the archived `tasks.md` Status blocks):
  - CI drift test (`scripts/harness-scripts.test.mjs`): it ignores other toolchains (pnpm, `docker compose build`) and skips multi-line `run: |` blocks.
  - `ArchitectureTest`: the composite sub-rules (layout packages/persistence/services, repository→service/web) have no violating test classes of their own.
  - `GlobalExceptionHandler`: exceptions marked `@ResponseStatus` become 500. Use `DomainException`, or let the handler honour the annotation.
  - Bash guard (third review of PR #13, gaps older than that PR): quoted redirection targets (`cat <".env"`, `$(<'.env')`) and `git commit -F .env` are allowed; `git commit -m x <path>` stages a path without a separate `git add`.
  - Docs: `docs/workflow.md` Handoff step lacks the RED/GREEN Verification format; the `docs/setup.md` `.githooks/` row omits the archive gate; the Delivery template runs tests before the archive step.
  - Docs: some entries in `docs/decisions/1-architecture.md` and `3-team-process.md` point to `openspec/changes/<name>/` paths that are now under `archive/`.
