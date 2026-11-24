# Requirements: Project Setup (EARS Notation)

## Phase 0 — Git Branching & Repository Hygiene

- **R0.1:** **THE SYSTEM SHALL** maintain `master` (default), `alpha`, and `dev` branches, and perform all setup work on a dedicated `feat/setup` branch created from `dev`.
- **R0.2:** **THE SYSTEM SHALL** provide a root `.gitignore` that excludes Java/Maven artifacts, Node/Vite build outputs, Docker override files, `.env` secret files (while keeping `.env.example` tracked), IDE metadata, OS clutter, and local-only AI agent caches.

## Phase 1 — Documentation Source of Truth

- **R1.1:** **THE SYSTEM SHALL** store the product vision at `docs/discovery-canvas.md` along with concise reference documents `docs/architecture.md`, `docs/workflow.md`, and `docs/decisions.md`.
- **R1.2:** **THE SYSTEM SHALL** document the 9-step evidence-first pipeline and the `assessment-transcript/v1` JSON contract in `docs/architecture.md`.

## Phase 2 — Spec-Driven Layer (OpenSpec)

- **R2.1:** **THE SYSTEM SHALL** initialize OpenSpec in project scope (`openspec/config.yaml`, `openspec/specs/`, `openspec/changes/`) so Claude Code, Antigravity, Codex, and Gemini CLI share the same plain-file source of truth without harness lock-in.
- **R2.2:** **THE SYSTEM SHALL** include a complete setup specification (`proposal.md`, `requirements.md`, `design.md`, `tasks.md`) under `openspec/changes/project-setup/` before scaffolding application code.
- **R2.3:** **THE SYSTEM SHALL** seed baseline specification files for all 7 MVP modules (`interviewer-studio`, `assessment-chat`, `evidence-pipeline`, `formula-engine`, `skills-matrix-review`, `team-builder`, `candidate-results`) under `openspec/specs/`, embedding all 10 seed EARS requirements from `docs/discovery-canvas.md` §14.2.

## Phase 3 — Multi-Agent Steering, Skills & Hooks

- **R3.1:** **THE SYSTEM SHALL** provide a concise, always-on root `AGENTS.md` encoding the monorepo map, mandatory stack versions, non-negotiable domain invariants (no LLM math, explicit `N/A`, verbatim quote verification, human approval, no secrets), OpenSpec workflow, Conventional Commits, and branch rules.
- **R3.2:** **THE SYSTEM SHALL** provide Antigravity-native configuration via root `GEMINI.md` and flat scoped rule files under `.agents/rules/` (`spec-driven.md`, `backend-java.md`, `frontend-vue.md`, `git-workflow.md`) using valid `snake_case` YAML frontmatter (`trigger: always_on`, `trigger: glob`, `trigger: model_decision`), each strictly under 24 KB.
- **R3.3:** **THE SYSTEM SHALL** provide Claude Code configuration via root `CLAUDE.md` (including a "First-Run Bootstrap & Self-Configuration" section), pre-seeded `.claude/skills/*/SKILL.md` files, and valid JSON `.claude/settings.json` defining `PreToolUse` safety guards and `PostToolUse` lightweight validation hooks.
- **R3.4:** **WHEN** a developer or agent attempts to commit a non-Conventional message or push directly to `master`/`main`, `alpha`, or non-`docs`/`hotfix` commits to `dev`, **THE SYSTEM SHALL** reject the operation via executable version-controlled hooks in `.githooks/`.

## Phase 4 — Local Tooling, Monorepo Scaffold & Docker

- **R4.1:** **THE SYSTEM SHALL** scaffold `backend/` as a Java 21 + Maven (with `./mvnw` wrapper) Spring Boot 4.x application configured with Spring Web, Spring Security (2-role skeleton: `PROFESSOR`/`REVIEWER` and `CANDIDATE`), Spring Data JPA, Validation, Actuator (`/actuator/health`), PostgreSQL driver, Flyway (`V1__init.sql`), Spring AI 2.0 (booting cleanly without an API key), Timefold Solver, springdoc-openapi, JUnit 5, Mockito, and Testcontainers (PostgreSQL).
- **R4.2:** **WHEN** `./mvnw test` is executed in `backend/` on Java 21, **THE SYSTEM SHALL** compile cleanly and pass a smoke test verifying the application context and health/ping endpoint.
- **R4.3:** **THE SYSTEM SHALL** scaffold `frontend/` as a Vue 3.5 (stable, NOT 3.6 RC) + TypeScript + Vite 8 application configured with Vue Router, Pinia, `@tanstack/vue-query`, Tailwind CSS v4, Apache ECharts (`echarts` + `vue-echarts`), an OpenAPI TypeScript client generation script, and a placeholder shell with Assessment View & Ranking View stubs connected to backend health.
- **R4.4:** **WHEN** `npm run typecheck`, `npm run lint`, and `npm run build` are executed in `frontend/`, **THE SYSTEM SHALL** exit with code `0`.
- **R4.5:** **THE SYSTEM SHALL** provide `backend/Dockerfile` (Java 21 multi-stage), `frontend/Dockerfile` (multi-stage build + dev target), root `docker-compose.yml` (`db` + `backend` + `frontend`), and `.env.example` validated with `docker compose config` (or `docker-compose config`).

## Phase 5 — GitHub Governance & CI

- **R5.1:** **THE SYSTEM SHALL** define declarative GitHub branch protection rulesets in `.github/rulesets/` enforcing: `master`/`main` (PR, ≥4 approvals, CI required, no direct/force push), `alpha` (PR, ≥2 approvals, CI required, no direct/force push), and `dev` (PR with 0 approvals, CI required, no force push) alongside an executable `scripts/apply-github-rulesets.sh`.
- **R5.2:** **WHEN** a push or PR targets `dev`, `alpha`, or `master`/`main`, **THE SYSTEM SHALL** run `.github/workflows/ci.yml` to verify backend tests, frontend typecheck/lint/build, Conventional Commits, and the `dev` direct-push `docs:`/`hotfix:` policy.
- **R5.3:** **THE SYSTEM SHALL** provide `.github/PULL_REQUEST_TEMPLATE.md` prompting concise plain-language **What** and **Why** descriptions.

## Phase 6 & 7 — Verification, Onboarding, Changelog & PR

- **R6.1:** **BEFORE** opening a Pull Request, **THE SYSTEM SHALL** undergo an independent dual-agent audit (Stack/Build/Docker Auditor and Harness/Specs/Governance Auditor) with all confirmed defects fixed and re-verified.
- **R6.2:** **THE SYSTEM SHALL** provide a crisp, cross-platform `README.md` (macOS, Windows, Linux) explaining why Docker is used, linked prerequisites, quickstart commands, `.githooks` activation, and AI agent usage, plus a Keep a Changelog `CHANGELOG.md`.
- **R7.1:** **THE SYSTEM SHALL** publish all setup commits via Conventional Commits on `feat/setup` and open a plain-language Pull Request into `dev` without pushing directly to `dev`, `alpha`, or `master`.
