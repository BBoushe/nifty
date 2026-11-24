# Feature History

Newest first. One entry per folder in `openspec/changes/archive/` (enforced by `scripts/harness-scripts.test.mjs`).
The date in each heading is the archive date. Before 2026-11-24, the team did not archive finished changes, so the early entries share one date.
An entry can come before its change merges. The PR/MR line then names an open PR.

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
- **PR/MR:** GitHub #9, GitLab !8 (open when this entry was written)
- **Specs:** REQ-SL-01 to REQ-SL-06   **Decisions:** 3.7, 3.8, 3.9

## 2026-11-24 — backend-architecture-baseline
- **What:** Moved the backend to package-by-feature (`shared/` and `<feature>/{web,service,repository}`), with ArchUnit tests for the dependency rules. Added `DomainException` and a global RFC 9457 `ProblemDetail` handler. Added the feature slice map to `docs/architecture.md`.
- **Why:** The course guide sets these rules. The backend is still a skeleton, so the change costs little now. Feature slices keep the blast radius of each PR small.
- **PR/MR:** GitHub #8, GitLab !7 (open when this entry was written)
- **Specs:** REQ-BA-01 to REQ-BA-08   **Decisions:** 1.10

## 2026-11-24 — bash-guard-hardening
- **What:** The Bash guard now removes more command wrappers and checks shell `-c` strings recursively, with a depth limit. It blocks `git push --no-verify` to protected branches on any remote.
- **Why:** Wrappers such as `sh -c "cat .env"` or `rtk run` hid commands from the guard. Some of these could leak `KICONNECT_API_KEY` into the model context.
- **PR/MR:** GitHub #7, GitLab !6 (open when this entry was written)
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
