# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Documentation Foundation (`docs/`):** Added `docs/discovery-canvas.md`, `docs/architecture.md` (monorepo map, 9-step evidence-first pipeline, `assessment-transcript/v1` contract), `docs/workflow.md`, and `docs/decisions.md` (ADRs D1–D13 and open team decisions O1–O7).
- **Spec-Driven Layer (`openspec/`):** Initialized OpenSpec for Claude Code, Antigravity, Codex, and Gemini CLI; created `openspec/changes/project-setup/` and seeded all 7 MVP domain specs (`interviewer-studio`, `assessment-chat`, `evidence-pipeline`, `formula-engine`, `skills-matrix-review`, `team-builder`, `candidate-results`) with all §14.2 EARS requirements (`openspec validate --all` passing 8/8).
- **Tool-Agnostic Multi-Agent Harness:** Added root `AGENTS.md`, `GEMINI.md`, `CLAUDE.md` (with First-Run Bootstrap & Self-Configuration), scoped `.agents/rules/*.md` rules, `.claude/skills/*/SKILL.md` skills, `.claude/settings.json` lifecycle hooks, and portable `.githooks/` (`commit-msg` and `pre-push`).
- **Backend Scaffold (`backend/`):** Initialized Java 21 + Maven Wrapper (`./mvnw`) Spring Boot 4.0.10 application with Spring Web, Spring Security (2-role skeleton: `PROFESSOR`/`REVIEWER` and `CANDIDATE`), Spring Data JPA, Validation, Actuator, PostgreSQL, Flyway (`V1__init.sql` audit log baseline), Spring AI 2.0.0 (booting cleanly with placeholder config), Timefold Solver 2.8.0, springdoc-openapi 3.0.2, JUnit 5, Mockito, Testcontainers, `/api/v1/health`, and passing smoke tests.
- **Frontend Scaffold (`frontend/`):** Initialized Vue 3.5 (stable, pinned `~3.5.13`) + TypeScript + Vite 8 application with Vue Router, Pinia, TanStack Query, Tailwind CSS v4, Apache ECharts (`vue-echarts`), OpenAPI TypeScript generation script stub, and Assessment View / Ranking View placeholder shells connected to `/api/v1/health`.
- **Docker & Local Environment:** Added multi-stage `backend/Dockerfile` and `frontend/Dockerfile` (`dev` + `prod` Nginx proxy targets) with IPv4-first DNS, resilient npm/Maven retry settings, BuildKit cache mounts, `.dockerignore` files, `docker-compose.yml` (`db` + `backend` + `frontend`), and `.env.example`.
- **EU LLM Evaluator (`ki:connect` NRW / FH AI):** Configured OpenAI-compatible evaluator endpoint (`https://chat.kiconnect.nrw/api/v1`, hosted in Aachen / NRW, free for students) with `KICONNECT_API_KEY` and model selection (`OpenAI GPT OSS 120B`, `Qwen 3.8 27B`, `Mistral Small 4 119B`) in `.env.example`, `docker-compose.yml`, `application.yml`, and `docs/decisions.md` (D14).
- **Harness v2 Upgrade (SDD/ADD Tooling & Kiro Crew):**
  - Replaced `.claude/hooks/*.sh` with zero-dependency cross-platform Node `.mjs` guards (`lib.mjs`, `guard-files.mjs`, `guard-bash.mjs`, `guard-agent.mjs`, `test-gate.mjs`) + `node:test` suite (`hooks.test.mjs`, wired into CI), added `.claude/settings.json` `permissions.allow`/`deny`, added `.githooks/pre-commit` secret/branch bridge, and integrated fast path-scoped `test-gate.mjs` into `.githooks/pre-push`.
  - Added `.claude/agents/{spec-builder,reviewer,test-writer}.md` role separation (mirrored in `AGENTS.md`), shared skills (`record-decision`, `question-round`, `pr-memo`) in `.agents/skills/` symlinked into `.claude/skills/`, and `## Handoff Log` convention in `openspec/config.yaml` and `spec-implementer`.
  - Extended `.agents/rules/{backend-java,frontend-vue}.md`, added Cross-Layer Enum & State Lock-In Table to `docs/architecture.md`, added Day-to-Day Ticket Loop, Module Ownership Table, and Blocking vs. Convention Candidate rules to `docs/workflow.md`, and added Cross-Tool Rule & Hook Parity table to `AGENTS.md`.
  - Added thin Kiro IDE / Kiro Crew steering bridge (`.kiro/steering/nifty-core.md`) and stack-agnostic SDD/ADD guide (`docs/setup.md` with `For Teammates` and `For Agents` sections).
- **GitHub Governance & CI:** Added declarative branch protection rulesets in `.github/rulesets/` (`main.json`, `alpha.json`, `dev.json` — applied to `BBoushe/nifty`), `scripts/apply-github-rulesets.sh`, `.github/workflows/ci.yml` (Backend, Frontend, Hook Tests, Conventional Commits, and Branch Promotion Policy), and `.github/PULL_REQUEST_TEMPLATE.md`.

### Changed
- Hardened root `.gitignore` for Java/Maven, Node/Vite, Docker overrides, `.env` secrets, IDEs, OS clutter, and local-only AI agent caches.
- Replaced placeholder `README.md` with a cross-platform (Windows, macOS, Linux) onboarding and AI agent collaboration guide.
