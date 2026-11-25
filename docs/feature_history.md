# Feature History

Newest first. One entry per folder in `openspec/changes/archive/` (enforced by `scripts/harness-scripts.test.mjs`).
The date in each heading is the archive date. Before 2026-11-24, the team did not archive finished changes, so the early entries share one date.
An entry can come before its change merges. The PR/MR line then names an open PR.

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
