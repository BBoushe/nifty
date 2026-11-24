# Design: Project Setup (`project-setup`)

## 1. Repository & Monorepo Layout

```text
nifty/
├── AGENTS.md                        # Universal always-on steering rules (Codex, Antigravity, Cursor, Copilot)
├── CLAUDE.md                        # Claude Code entry point + First-Run Bootstrap protocol
├── GEMINI.md                        # Antigravity / Gemini CLI steering & Verifier role guidance
├── README.md                        # Cross-platform team onboarding guide (macOS / Windows / Linux)
├── CHANGELOG.md                     # Keep a Changelog v1.1.0
├── docker-compose.yml               # Local full-stack stack: db (Postgres 17) + backend + frontend
├── .env.example                     # Documented environment variables (never commit .env)
├── .gitignore                       # Full-stack + IDE + OS + local agent state exclusions
├── .agents/
│   ├── rules/                       # Flat Antigravity rules (<24KB each, snake_case YAML frontmatter)
│   │   ├── spec-driven.md           # trigger: always_on
│   │   ├── backend-java.md          # trigger: glob ("backend/**")
│   │   ├── frontend-vue.md          # trigger: glob ("frontend/**")
│   │   └── git-workflow.md          # trigger: model_decision
│   └── skills/                      # OpenSpec + project skills (shared with Codex/Antigravity)
├── .claude/
│   ├── settings.json                # PreToolUse (branch/secret guards) & PostToolUse (lint/check) hooks
│   └── skills/                      # OpenSpec skills + project-specific SKILL.md definitions
├── .githooks/
│   ├── commit-msg                   # Portable Conventional Commits validator
│   └── pre-push                     # Blocks pushes to master/main/alpha & non-docs/hotfix direct pushes to dev
├── .github/
│   ├── PULL_REQUEST_TEMPLATE.md     # Short plain-language What / Why template
│   ├── rulesets/                    # Declarative GitHub Rulesets (main.json, alpha.json, dev.json)
│   └── workflows/ci.yml             # Backend, frontend, Conventional Commits, and dev direct-push policy CI
├── scripts/
│   └── apply-github-rulesets.sh     # Applies .github/rulesets/*.json via gh api (idempotent)
├── docs/
│   ├── discovery-canvas.md          # Foundational product vision
│   ├── architecture.md              # Monorepo map, 9-step pipeline, assessment-transcript/v1
│   ├── workflow.md                  # Branching, OpenSpec, HitL, Builder/Verifier agent model
│   └── decisions.md                 # Decided ADRs (D1–D13) & Open Questions (O1–O7)
├── openspec/
│   ├── config.yaml                  # Project context & EARS / HitL rules
│   ├── changes/project-setup/       # This setup change spec (proposal, requirements, design, tasks)
│   └── specs/<7-mvp-modules>/       # Seeded domain specs with §14.2 EARS requirements
├── backend/                         # Spring Boot 4.x / Java 21 / Maven Wrapper / Flyway / Spring AI 2.0 / Timefold
└── frontend/                        # Vue 3.5 / TypeScript / Vite 8 / Tailwind v4 / ECharts / Pinia / TanStack Query
```

## 2. Backend Skeleton Design (`backend/`)

- **Build & Runtime:** Java 21, Maven 3.9+ with `./mvnw` (`only-script` wrapper so zero binary `.jar` is committed).
- **Spring Boot 4.x Dependencies:**
  - `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`
  - `org.postgresql:postgresql`, `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql` (with `V1__init.sql` creating an initial metadata/audit readiness schema or baseline migration)
  - Spring AI 2.0 BOM + OpenAI-compatible starter (`spring-ai-starter-model-openai`) with autoconfiguration guarded/configured via placeholder env vars (`AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL`) so the application boots cleanly even without an external LLM key.
  - Timefold Solver Spring Boot starter (`ai.timefold.solver:timefold-solver-spring-boot-starter` or core solver library compatible with Spring Boot 4) ready for Team Builder.
  - `springdoc-openapi-starter-webmvc-ui` for `/v3/api-docs` and Swagger UI.
  - Test stack: `spring-boot-starter-test` (JUnit 5 + Mockito) + Testcontainers (`postgresql`, `junit-jupiter`) + H2 in-memory fallback profile for fast unit/smoke tests when Docker is unavailable in CI or local laptops, while PostgreSQL/Testcontainers are wired and ready for DB integration tests.
- **Security Skeleton:** Stateless HTTP security permitting `/actuator/health`, `/api/v1/health`, `/v3/api-docs/**`, `/swagger-ui/**`, and defining two authorities (`ROLE_PROFESSOR`, `ROLE_REVIEWER` grouped as staff, and `ROLE_CANDIDATE`) via a clean `SecurityConfig`.
- **Health/Ping Endpoint:** `GET /api/v1/health` returning `{ "status": "UP", "service": "nifty-backend", "version": "0.1.0-SNAPSHOT" }`.

## 3. Frontend Skeleton Design (`frontend/`)

- **Stack:** Vue `^3.5.x` (strictly 3.5 stable), TypeScript, Vite `^8.x`, Vue Router 4, Pinia, `@tanstack/vue-query`, Tailwind CSS v4 (`@tailwindcss/vite`), Apache ECharts (`echarts` + `vue-echarts`), ESLint + `vue-tsc`.
- **OpenAPI Stub:** `npm run api:generate` script invoking `openapi-typescript` against `http://localhost:8080/v3/api-docs` into `src/api/schema.d.ts`.
- **Views:** Minimal layout shell switching between `/assessment` (Assessment View stub) and `/ranking` (Ranking View stub), fetching `/api/v1/health` via TanStack Query and rendering a minimal ECharts health/placeholder chart to confirm ECharts bundling works.
- **UI Kit:** Intentionally neutral Tailwind v4 styling so the team can plug in PrimeVue or shadcn-vue once Decision O1 is resolved.

## 4. Docker Topology (`docker-compose.yml`)

- `db`: `postgres:17-alpine`, volume `postgres_data`, healthcheck `pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}`.
- `backend`: Built from `backend/Dockerfile` (multi-stage `maven:3.9-eclipse-temurin-21` -> `eclipse-temurin:21-jre-alpine`), `depends_on: db (condition: service_healthy)`, reads DB and AI vars from `.env`.
- `frontend`: Built from `frontend/Dockerfile` (`dev` target for Vite HMR and `prod` target with `nginx:alpine` proxying `/api/` and `/actuator/` to `http://backend:8080`).

## 5. Multi-Agent Steering & Hook Architecture

- **Single Source of Truth:** `AGENTS.md` holds invariant rules; `GEMINI.md` and `CLAUDE.md` reference `AGENTS.md` and `openspec/` rather than duplicating long walls of text.
- **Antigravity Rules:** Flat `.agents/rules/*.md` files with `snake_case` frontmatter (`trigger`, `globs`, `description`) kept compact (< 4 KB each, far below the 24 KB limit).
- **Claude Code Hooks:** `.claude/settings.json` uses portable shell checks in `PreToolUse` (blocking `.env` edits and pushes to `master`/`main`/`alpha`) and `PostToolUse` (lightweight syntax/format reminder/checks).
- **Portable Git Hooks:** `.githooks/commit-msg` and `.githooks/pre-push` work identically on macOS, Linux, and Windows Git Bash for all agents and humans once `git config core.hooksPath .githooks` is run.

## 6. GitHub Branch Protection Strategy

- Because GitHub Rulesets cannot natively filter direct-push bypasses by Conventional Commit prefix, we combine:
  1. Declarative `.github/rulesets/{main,alpha,dev}.json` (protecting `~DEFAULT_BRANCH` + `refs/heads/main` + `refs/heads/master`, `refs/heads/alpha`, and `refs/heads/dev`).
  2. Local `.githooks/pre-push` enforcing the `docs:`/`hotfix:` direct-push rule on `dev` before push.
  3. Server-side `.github/workflows/ci.yml` auditing pushes to `dev` and failing if any direct-push commit omits `docs:` or `hotfix:`.
