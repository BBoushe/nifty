# Feature History

Newest first. One entry per folder in `openspec/changes/archive/` (enforced by `scripts/harness-scripts.test.mjs`).
The date in each heading is the archive date. Before 2026-11-24, the team did not archive finished changes, so the first four entries share one date.

## 2026-11-24 — decisions-coding-rules-gitlab
- **What:** Added the `docs/decisions/` registry with `X.Y` IDs and `scripts/record-decision.mjs`. Added the canonical coding standards and the living `docs/api.md`. Added a GitHub/GitLab switch in `.env` with `scripts/check-setup.mjs` and a GitLab CI file.
- **Why:** The course grades decision traceability and clean code. The team also needs GitLab next to GitHub.
- **PR/MR:** GitHub #2 (GitLab: none, the mirror starts with #4).
- **Specs:** REQ-DCR-01 to REQ-DCR-04   **Decisions:** 1.7, 2.9, 3.1, 3.2, 3.3

## 2026-11-24 — harness-v2-upgrade
- **What:** Replaced the shell hooks with tested Node `.mjs` hooks and a staged-diff secret scan. Added the spec-builder, cold reviewer and test-writer agents, the shared skills, Kiro steering and `docs/setup.md`.
- **Why:** The old hooks failed on Windows and missed `.env` reads. Self-review let domain invariant errors pass.
- **PR/MR:** GitHub #2 (GitLab: none, the mirror starts with #4).
- **Specs:** REQ-H2-01 to REQ-H2-06   **Decisions:** 3.1

## 2026-11-24 — docker-and-kiconnect
- **What:** Fixed the frontend Docker build timeout with IPv4-first DNS, npm retry flags and BuildKit cache mounts. Wired the ki:connect NRW evaluator LLM through `.env.example`, `docker-compose.yml` and `application.yml`.
- **Why:** `docker compose up --build` failed with `ETIMEDOUT`. The team chose ki:connect NRW as the GDPR-safe evaluator provider.
- **PR/MR:** GitHub #1 (GitLab: none).
- **Specs:** REQ-DK-01, REQ-DK-02   **Decisions:** 1.6

## 2026-11-24 — project-setup
- **What:** Created the branches, docs, OpenSpec with the 7 seed domain specs, and the multi-agent harness. Scaffolded the Spring Boot 4 backend, the Vue 3.5 frontend and Docker Compose. Added GitHub rulesets, CI and the PR template.
- **Why:** Five people with different AI tools must deliver in one block week. Without shared specs and guardrails from day 1, they drift and break builds.
- **PR/MR:** GitHub #1 (GitLab: none).
- **Specs:** seed EARS requirements in `openspec/specs/` (see `requirements.md` in the archive)   **Decisions:** 1.1 to 1.5, 2.1 to 2.8
