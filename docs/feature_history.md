# Feature History

Newest first. One entry per folder in `openspec/changes/archive/` (enforced by `scripts/harness-scripts.test.mjs`).
The date in each heading is the archive date. Before 2026-11-24, the team did not archive finished changes, so the early entries share one date.
An entry can come before its change merges. The PR/MR line then names an open PR.

## 2026-11-26 — evaluation-service
- **What:** Added `V7__evaluation.sql` (`formula_version`, `evaluation_run`, `candidate_evaluation_summary` with `UNIQUE(attempt_id)`, `response_evaluation`, `skill_score` with `chk_na_score_null`), private JPA repositories, synchronous `AssessmentDeletedEvent` GDPR cleanup (`EvaluationCleanupListener`), zero-Stream deterministic calculators (`VerbatimQuoteVerifier`, `ConfidenceCalculator`, `SkillFormulaCalculator`, `ReleaseGateEvaluator`, `DeterministicAdviceBuilder`), async `AFTER_COMMIT` 2-run fresh-context `TwoRunResponseJudge` via `ContextCreationService.buildJudgeContext`, review queue with `AuditService` overrides and zero-LLM Formula B recomputation, SQL-only `CohortGapQueryService`, `EvaluationQueryServiceImpl` (placeholder removed), named `WEIGHTED_MEAN` formula presets/versions/preview, `PROFESSOR` `/api/v1/ranking/**` and `CANDIDATE` `/api/v1/assessment/results/{configId}` endpoints, synced `docs/api.md`, and Vue 3.5 + PrimeVue + ECharts views (`views/ranking/` and `views/assessment/ResultsView.vue`).
- **Why:** Completed attempts need auditable, evidence-first scoring with explicit `N/A`, threshold-gated auto-release, human override traceability, SQL-only cohort gap insights, post-release candidate transparency, and a read-only hard-skill query contract for Team Builder (Decisions 1.18, 1.19, 2.10–2.13, 2.15, 2.16, 2.19, 2.20, 2.22, 2.24, 2.25, 4.3, 4.4).
- **PR/MR:** GitHub #29, GitLab !27
- **Specs:** REQ-EV-01..02 (added to `evidence-pipeline`); REQ-EP-01..09, REQ-FE-01..03, REQ-FE-05..06, REQ-SM-01..08, REQ-CR-01..06 (implemented)   **Decisions:** applies 1.10, 1.12, 1.14, 1.15, 1.18, 1.19, 2.10–2.13, 2.15, 2.16, 2.19, 2.20, 2.22, 2.24, 2.25, 4.3, 4.4

## 2026-11-26 — team-builder
- **What:** Added `V8__teams.sql` (`team_proposal`, `team_member` with `UNIQUE (proposal_id, invite_id)`), a zero-Stream `TeamObjectiveCalculator` (hard skills `0..4` and BFI-10 `1..5` normalized to `0..1`, `N/A` excluded per dimension), a seeded Simulated Annealing optimizer with 2-opt swaps, and `TeamBuilderService` (generate `DRAFT`, recalculate after drag and drop, publish with a `TEAMS_PUBLISHED` audit row). Added `PROFESSOR`-only `/api/v1/ranking/configs/{configId}/teams/**` and `/api/v1/ranking/teams/{proposalId}/**` endpoints scoped via `StudioQueryService.ownedBy`, `TeamCleanupListener` on `AssessmentDeletedEvent`, and the Vue Team Builder view in `views/teams/`.
- **Why:** Professors form balanced teams from released results only, deterministically and without an LLM, and approve them before publishing (Decisions 1.20, 2.23, 2.27, 4.3, 4.4).
- **PR/MR:** GitHub #27, GitLab !26
- **Specs:** REQ-TB-08 (added), REQ-TB-06 (modified); REQ-TB-03..05, REQ-TB-07, REQ-TB-09..12 (implemented)   **Decisions:** applies 1.20, 2.10, 2.13, 2.23, 2.27, 4.3, 4.4

## 2026-11-26 — invite-links
- **What:** Added `V4__auth_invites.sql` (`invite` with 256-bit random tokens stored only as SHA-256 hex), `PROFESSOR`-only invite create/list/revoke endpoints scoped via `StudioQueryService.ownedBy`, public `POST /api/v1/auth/invite/{token}` starting a `CANDIDATE` session, `AuthContextServiceImpl.currentCandidate()`, `RevokedInviteFilter` ending revoked sessions on their next request (`401`), synchronous `AssessmentDeletedEvent` invite purge, and frontend `/invite/:token` landing page + Studio detail invite panel (single add, CSV import, copy/download links, revoke).
- **Why:** Candidates sign in only through personal invite links, assessment/evaluation need `AuthContextService.currentCandidate()`, and revoking an invite or deleting an assessment must end access and purge invite rows immediately (Decisions 1.17, 1.23, 4.4).
- **PR/MR:** GitHub #26, GitLab !25
- **Specs:** REQ-AA-08..09 (added to `auth-access`); REQ-AA-03..05 (implemented)   **Decisions:** applies 1.10, 1.14, 1.15, 1.17, 1.23, 4.4

## 2026-11-26 — studio-assessment-creation
- **What:** Added `V3__studio.sql` (`assessment_config`, `skill`, `core_question`, `core_question_skill`, `studio_block_mutation()` immutability triggers), `@Immutable` JPA entities and repositories in `studio/repository/`, pure zero-Stream validators with the `SOFT_SKILLS` + BFI-10 zero-question exemption, one-shot idempotent creation (`CONFIG_CREATED` audit, `REQ-IS-12` key ownership), `StudioQueryServiceImpl` (with `ownedBy` and rubric-leak guard; placeholder deleted), `PROFESSOR`-only `/api/v1/ranking/studio/configs/**` REST endpoints + GDPR `DELETE` (`AssessmentDeletedEvent` + `SET LOCAL nifty.allow_purge = 'on'`), and Vue 3.5 + PrimeVue Studio form (`localStorage` draft), list, and read-only detail views.
- **Why:** Professors need one-shot immutable assessment creation, hidden 5-level rubrics, narrow downstream read-only views, and GDPR purge without editing live rubrics (Decisions 1.19, 2.21, 2.22, 2.23, 4.4).
- **PR/MR:** GitHub #25, GitLab !24
- **Specs:** REQ-IS-12 (added to `interviewer-studio`); REQ-IS-01, REQ-IS-05..11 (implemented)   **Decisions:** applies 1.10, 1.12, 1.14, 1.19, 2.21, 2.22, 2.23, 4.4

## 2026-11-26 — auth-accounts
- **What:** Added `V2__auth_accounts.sql` (`professor_account`), env bootstrap (`NIFTY_BOOTSTRAP_PROFESSOR_EMAIL` / `NIFTY_BOOTSTRAP_PROFESSOR_PASSWORD`), `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, server session cookies (`HttpOnly`, `SameSite=Lax`), SPA CSRF (`XSRF-TOKEN` / `X-XSRF-TOKEN`) and `AuthContextServiceImpl` (`AuthContextServicePlaceholder` deleted). Added the frontend `/sign-in` view, Pinia auth store, `/ranking/*` and `/assessment/*` route guards, and CSRF header support in `apiFetch` and `postSse`.
- **Why:** Studio and the Ranking View need a signed-in professor session and `AuthContextService.currentProfessorId()` without HTTP Basic or secrets in the repo (Decisions 1.23, 1.24).
- **PR/MR:** GitHub #23, GitLab !22
- **Specs:** REQ-AA-06..07 (added to `auth-access`); REQ-AA-01..02 (implemented)   **Decisions:** applies 1.14, 1.15, 1.23, 1.24

## 2026-11-26 — context-creation
- **What:** Built the stateless `context` slice, the only place that builds LLM prompts. A rubric-blind `interviewer-v1` builder and a per-(entry, skill) `evaluator-judge-v1` builder (levels `0..4` in order, evidence-only JSON, timed-out partial answers kept). Candidate text goes through a zero-Stream sanitizer and a deterministic injection detector, then inside `<candidate_answer>` or `<transcript_entry>` tags. A flagged answer writes one `PROMPT_INJECTION_DETECTED` audit row with a SHA-256 and rule IDs, never raw text. Added the `PROFESSOR` preview endpoint `POST /api/v1/ranking/context/preview/interviewer`. Deleted `ContextCreationServicePlaceholder`.
- **Why:** `assessment` and `evaluation` must not write their own prompts. One slice keeps the interviewer rubric-blind and treats candidate text as untrusted data.
- **PR/MR:** GitHub #24, GitLab !23
- **Specs:** REQ-CC-09 (added to the main spec), REQ-CC-01..08 (implemented)   **Decisions:** applies 1.12, 1.18, 1.19, 1.21, 2.20, 2.24, 2.25

## 2026-11-25 — audit-log-service
- **What:** `AuditService.record(...)` writes one `audit_log` row in the transaction of the caller. It rejects a missing actor, role, entity or action. `AuditLogEntity` is `@Immutable`, and `AuditLogRepository` has no update or delete method. `AuditServicePlaceholder` is gone.
- **Why:** Studio, context, evaluation and teams must write real audit rows for overrides, deletions and injection flags (Decision 1.16).
- **PR/MR:** GitHub #21, GitLab !20
- **Specs:** REQ-AL-01..02 (change-local, archived with `--skip-specs`)   **Decisions:** none; applies 1.10, 1.16

## 2026-11-25 — frontend-foundation
- **What:** Added Vitest (jsdom, ECharts mock) and `npm run test` in both CI files. Added the PrimeVue 4 preset from Tailwind `@theme` tokens and `ScoreBadge`, which never shows `N/A` as `0`. Moved views into feature folders with one `routes.ts` each. Added the shared API client for RFC 9457 errors and SSE streams from a `POST`. `npm run typecheck` now checks the app sources and `vite.config.ts`. `ScoreBadge` shows bad levels as invalid, and the preset maps the full primary scale.
- **Why:** The five teammate tickets need a test runner, the design system, separate route files and one client. Then they do not edit the same files.
- **PR/MR:** GitHub #18, GitLab !17
- **Specs:** REQ-FF-01..03 (change-local, archived with `--skip-specs`)   **Decisions:** 1.14

## 2026-11-25 — backend-foundation
- **What:** Moved to the `webmvc` starter and the Spring AI 2 model property. All Spring tests now run on PostgreSQL Testcontainers (`PostgresContainerBase`), and H2 is gone. The GitLab backend job uses `docker:dind`. Added `FieldValidationException` (400 with `errors[]`), the two cross-slice events, and compile-only stubs of every contract-pack interface, record and enum with 6 placeholder beans. `RoleAccessTest` probes unmapped paths.
- **Why:** The five teammate tickets need the shared types in code, a database that runs JSONB and triggers, and one validation error format before they start.
- **PR/MR:** GitHub #19, GitLab !18
- **Specs:** REQ-BF-01..03 (change-local, archived with `--skip-specs`)   **Decisions:** 1.22; applies 1.10, 1.18, 1.19

## 2026-11-25 — five-service-convergence
- **What:** Merged five teammate service designs into one contract: 7 backend slices, UUID keys, shared events, professor routes under `/api/v1/ranking/**`, one-shot immutable assessments, question-only weights, grouping modes, injection flags, separate per-attempt contexts and machine-parsable evaluator output. Replaced Timefold with a seeded Simulated Annealing optimizer. Added the `context-creation` spec, five teammate changes and the tickets in `docs/tickets/`.
- **Why:** The five designs disagreed on IDs, events, routes, weights and the solver. One contract lets the teammates build in parallel against mocks.
- **PR/MR:** GitHub #17, GitLab !16
- **Specs:** REQ-CC-01..08, REQ-AC-18/19, REQ-EP-08/09, REQ-FE-06, REQ-IS-08..11, REQ-TB-09..12 (added); REQ-AC-01/02/04/05/06/09/12/14/15/16, REQ-EP-01/03/05/07, REQ-FE-01/02/03/05, REQ-IS-01/05/06/07, REQ-SM-01/02/08, REQ-TB-03..07, REQ-CR-01/03 (modified); REQ-IS-02/03/04, REQ-TB-01/02 (removed)   **Decisions:** 1.18–1.21, 2.21–2.25

## 2026-11-25 — nov24-followups
- **What:** Answers submitted at timeout are now evaluated; only a question without answer text is `N/A`. No cost limit per interview. `REVIEWER` removed from the code: ranking routes allow only `PROFESSOR`, assessment routes only `CANDIDATE` (`RoleAccessTest`). Checked the wave-0 dependency names and closed the `nov24-reconciliation` Delivery boxes.
- **Why:** The prompter settled the items left open after the reconciliation merge. No static check can tell a partial answer from a complete one.
- **PR/MR:** GitHub #17, GitLab !16
- **Specs:** REQ-AC-08, REQ-EP-06 (renamed), REQ-FE-03 (modified); REQ-AA-01 (implemented)   **Decisions:** 2.20, 4.5; applies 1.15

## 2026-11-25 — nov24-reconciliation
- **What:** Reconciled specs and docs with the Nov 24 feature list and closed O1–O7. Delta specs for 7 domain specs plus the new `auth-access` spec: threshold-gated release, (question, skill) confidence, question-weighted coverage, attempt limit, min/max timers, append-only ledger with streamed replies, rubric-blind interviewer, invite-link sign-in, optional BFI-10. Canvas archived and updated; invariants, rules, architecture (dual-agent pipeline, domain model), API, design system, `docs/ai_logs/` and a wave-based backlog.
- **Why:** The feature list contradicted the canvas, specs and invariants, and open decisions blocked 6 of 7 MVP features. Builders can now start wave 0 without guessing.
- **PR/MR:** GitHub #16, GitLab !15
- **Specs:** REQ-AA-01..05, REQ-IS-05..07, REQ-AC-05..16, REQ-EP-05..07, REQ-FE-05, REQ-SM-05..08, REQ-CR-04..06, REQ-TB-05..07 (added); REQ-IS-02, REQ-AC-01/03/04, REQ-EP-03, REQ-FE-03, REQ-SM-01/03/04, REQ-CR-01/02 (modified); REQ-FE-04 (removed)   **Decisions:** 1.11–1.17, 2.10–2.19, 3.12–3.14, 4.1–4.4

## 2026-11-25 — agent-orchestration
- **What:** Added the `orchestrate-changes` skill: one session runs a builder agent per change in its own `../nifty-<change>` worktree and a cold reviewer per PR, handles PRs/MRs by branch name, and asks the user before every merge with an architecture summary. `forge.mjs prs` shows GitHub PRs and GitLab MRs side by side, and `forge.mjs` reads the main checkout's `.env` from a worktree.
- **Why:** One terminal per change and matching PR numbers to different MR numbers was too much manual work. The user wants to steer direction at every merge.
- **PR/MR:** GitHub #15, GitLab !14
- **Specs:** REQ-OR-01 to REQ-OR-04   **Decisions:** 3.11

## 2026-11-25 — docs-ste-rewrite
- **What:** Rewrote the agent entry points, `.agents/rules/`, README, setup, workflow, architecture, API, design-system and discovery-canvas docs, and the Purpose paragraphs of the 7 domain specs in ASD-STE100. A per-file obligation check kept every MUST/SHALL/NEVER, number, REQ ID and EARS statement unchanged.
- **Why:** Agents read these files in every session. Long, passive, multi-idea sentences cost tokens and can be read in more than one way.
- **PR/MR:** GitHub #14, GitLab !13
- **Specs:** REQ-SR-01 to REQ-SR-03   **Decisions:** 3.5

## 2026-11-25 — bash-guard-gaps
- **What:** Closed the bash guard gaps that two cold reviews found after PR #7: `-c` after other shell options and in `dash`/`ksh`, attached or `=` command strings, abbreviated `--no-verify`/`--force*`, `+` refspecs, `HEAD`/`@` destinations, `core.hooksPath` overrides, commands in groups, keywords and substitutions, pipes into a bare shell, and globs that match the secret env file. Quoted heredoc bodies now count as text.
- **Why:** PR #7 merged without a review. Its own requirements (REQ-BG-02, REQ-BG-04) were not met, and only this guard stops agents from reading the secret env file.
- **PR/MR:** GitHub #13, GitLab !12
- **Specs:** REQ-BG-06 to REQ-BG-13   **Decisions:** 3.10

## 2026-11-24 — ai-context-docs
- **What:** Added this feature history with a test that keeps it complete, a feature backlog and `docs/ai_docs/` library notes. Added the vetted `asd-ste100` skill, an STE writing rule and three `AGENTS.md` dispatch rows.
- **Why:** Agents could not see what was built, what to pick next, or current APIs for Spring Boot 4, Spring AI 2 and Timefold 2.
- **PR/MR:** GitHub #11, GitLab !10
- **Specs:** REQ-CD-01 to REQ-CD-06   **Decisions:** 3.5, 3.6

## 2026-11-24 — forge-ci-parity
- **What:** GitHub CI now builds the backend and frontend Docker images with the same commands as GitLab. The GitHub rulesets require the image and hook-test checks. A drift test fails when the two CI files run different commands. GitLab `dev` and `alpha` are now protected.
- **Why:** A Dockerfile error could pass GitHub, merge, and fail only on GitLab. Unprotected GitLab branches allowed force pushes and caused drift.
- **PR/MR:** GitHub #10, GitLab !9
- **Specs:** REQ-CP-01 to REQ-CP-03, REQ-GP-01, REQ-GP-02   **Decisions:** 1.8, 1.9

## 2026-11-24 — superpowers-loops
- **What:** Declared the Superpowers plugin in `.claude/settings.json`. Added the brainstorm, TDD, debug and verify loop to the role prompts, RED/GREEN evidence, the resumable `tasks.md` layout, and an archive gate hook.
- **Why:** Nothing proved that a test failed before the fix. Progress lived only in chat, and finished changes stayed active.
- **PR/MR:** GitHub #9, GitLab !8
- **Specs:** REQ-SL-01 to REQ-SL-06   **Decisions:** 3.7, 3.8, 3.9

## 2026-11-24 — backend-architecture-baseline
- **What:** Moved the backend to package-by-feature (`shared/` and `<feature>/{web,service,repository}`), with ArchUnit tests for the dependency rules. Added `DomainException` and a global RFC 9457 `ProblemDetail` handler. Added the feature slice map to `docs/architecture.md`.
- **Why:** The course guide sets these rules. The backend is still a skeleton, so the change costs little now. Feature slices keep the blast radius of each PR small.
- **PR/MR:** GitHub #8, GitLab !7
- **Specs:** REQ-BA-01 to REQ-BA-08   **Decisions:** 1.10

## 2026-11-24 — bash-guard-hardening
- **What:** The Bash guard now removes more command wrappers and checks shell `-c` strings recursively, with a depth limit. It blocks `git push --no-verify` to protected branches on any remote.
- **Why:** Wrappers such as `sh -c "cat .env"` or `rtk run` hid commands from the guard. Some of these could leak `KICONNECT_API_KEY` into the model context.
- **PR/MR:** GitHub #7, GitLab !6
- **Specs:** REQ-BG-01 to REQ-BG-05   **Decisions:** none recorded

## 2026-11-24 — dual-forge
- **What:** Added `GIT_FORGE=both` and `scripts/forge.mjs` (`push`, `pr`, `sync`, `status`). The script pushes to GitHub first, opens the PR and the MR with the same text, and fast-forwards GitLab after a GitHub merge. Added `scripts/apply-gitlab-protections.sh`.
- **Why:** The course needs the same work on the university GitLab. The two repositories must not drift apart.
- **PR/MR:** GitHub #4, GitLab !3 (archived later in GitHub #10, GitLab !9)
- **Specs:** REQ-DF-01 to REQ-DF-04   **Decisions:** 1.8

## 2026-11-24 — decisions-coding-rules-gitlab
- **What:** Added the `docs/decisions/` registry with `X.Y` IDs and `scripts/record-decision.mjs`. Added the canonical coding standards and the living `docs/api.md`. Added a GitHub/GitLab switch in `.env` with `scripts/check-setup.mjs` and a GitLab CI file.
- **Why:** The course grades decision traceability and clean code. The team also needs GitLab next to GitHub.
- **PR/MR:** GitHub #2, GitLab !2
- **Specs:** REQ-DCR-01 to REQ-DCR-04   **Decisions:** 1.7, 2.9, 3.1, 3.2, 3.3

## 2026-11-24 — harness-v2-upgrade
- **What:** Replaced the shell hooks with tested Node `.mjs` hooks and a staged-diff secret scan. Added the spec-builder, cold reviewer and test-writer agents, the shared skills, Kiro steering and `docs/setup.md`.
- **Why:** The old hooks failed on Windows and missed `.env` reads. Self-review let domain invariant errors pass.
- **PR/MR:** GitHub #2, GitLab !2
- **Specs:** REQ-H2-01 to REQ-H2-06   **Decisions:** 3.1

## 2026-11-24 — docker-and-kiconnect
- **What:** Fixed the frontend Docker build timeout with IPv4-first DNS, npm retry flags and BuildKit cache mounts. Wired the ki:connect NRW evaluator LLM through `.env.example`, `docker-compose.yml` and `application.yml`.
- **Why:** `docker compose up --build` failed with `ETIMEDOUT`. The team chose ki:connect NRW as the GDPR-safe evaluator provider.
- **PR/MR:** GitHub #1, GitLab !1
- **Specs:** REQ-DK-01, REQ-DK-02   **Decisions:** 1.6

## 2026-11-24 — project-setup
- **What:** Created the branches, docs, OpenSpec with the 7 seed domain specs, and the multi-agent harness. Scaffolded the Spring Boot 4 backend, the Vue 3.5 frontend and Docker Compose. Added GitHub rulesets, CI and the PR template.
- **Why:** Five people with different AI tools must deliver in one block week. Without shared specs and guardrails from day 1, they drift and break builds.
- **PR/MR:** GitHub #1, GitLab !1
- **Specs:** seed EARS requirements in `openspec/specs/` (see `requirements.md` in the archive)   **Decisions:** 1.1 to 1.5, 2.1 to 2.8
