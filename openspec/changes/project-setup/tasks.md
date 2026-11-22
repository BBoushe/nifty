# Tasks: Project Setup (`project-setup`)

## Phase 0 — Git Branching & Repository Hygiene
- [x] 0.1 Inspect git status, branches, and remotes (`master`, `alpha`, `dev` verified on `origin`)
- [x] 0.2 Ensure local `alpha` and `dev` branches exist and create/checkout `feat/setup` from `dev`
- [x] 0.3 Harden root `.gitignore` for Java/Maven, Node/Vite, Docker, `.env`, IDEs, OS clutter, and local agent state
- [x] 0.4 Commit Phase 0 (`chore(repo): update root .gitignore for full-stack and agent tooling`)

## Phase 1 — Documentation Foundation (`docs/`)
- [x] 1.1 Create `docs/` and move `discovery-canvas.md` to `docs/discovery-canvas.md`
- [x] 1.2 Author `docs/architecture.md`, `docs/workflow.md`, and `docs/decisions.md`
- [x] 1.3 Commit Phase 1 (`chore(docs): initialize docs folder and discovery canvas`)

## Phase 2 — OpenSpec & Seed Domain Specs
- [x] 2.1 Install `openspec` CLI and initialize in project scope (`claude,antigravity,codex,gemini`)
- [x] 2.2 Configure `openspec/config.yaml` with project context, stack, EARS rules, and HitL/deterministic invariants
- [x] 2.3 Author setup change spec in `openspec/changes/project-setup/` (`proposal.md`, `requirements.md`, `design.md`, `tasks.md`)
- [x] 2.4 Seed all 7 MVP domain specs under `openspec/specs/` with all 10 §14.2 EARS requirements
- [x] 2.5 Commit Phase 2 (`chore(specs): install openspec, project setup plan, and seed domain specs`)

## Phase 3 — Multi-Agent Steering, Skills & Hooks
- [x] 3.1 Create universal root `AGENTS.md`
- [x] 3.2 Create Antigravity `GEMINI.md` and flat scoped `.agents/rules/` (`spec-driven.md`, `backend-java.md`, `frontend-vue.md`, `git-workflow.md`) with valid snake_case YAML frontmatter (< 24 KB each)
- [x] 3.3 Create Claude Code `CLAUDE.md` (with First-Run Bootstrap section), baseline `.claude/skills/*/SKILL.md`, and `.claude/settings.json` lifecycle hooks
- [x] 3.4 Create executable `.githooks/commit-msg` and `.githooks/pre-push`
- [x] 3.5 Commit Phase 3 (`chore(agents): configure AGENTS.md, CLAUDE.md, GEMINI.md, rules, skills, and hooks`)

## Phase 4 — Local Tooling, Monorepo Scaffold & Docker
- [x] 4.1 Verify/install Java 21 SDK and Maven locally
- [x] 4.2 Scaffold `backend/` (Java 21, `./mvnw`, Spring Boot 4.x, Web/SSE, Security 2-role skeleton, JPA, Validation, Actuator, Postgres, Flyway `V1__init.sql`, Spring AI 2.0, Timefold, springdoc-openapi, JUnit 5/Mockito/Testcontainers) + health endpoint + passing `./mvnw test`
- [x] 4.3 Commit backend (`feat(backend): scaffold Spring Boot 4 Java 21 backend with Flyway, Spring AI 2.0, and Timefold`)
- [x] 4.4 Scaffold `frontend/` (Vue 3.5 stable, TypeScript, Vite 8, Vue Router, Pinia, TanStack Query, Tailwind v4, Apache ECharts, OpenAPI script stub, Assessment/Ranking shell) + passing `typecheck`, `lint`, `build`
- [x] 4.5 Commit frontend (`feat(frontend): scaffold Vue 3.5 TypeScript Vite 8 frontend with Tailwind v4 and ECharts`)
- [x] 4.6 Add `backend/Dockerfile`, `frontend/Dockerfile`, `docker-compose.yml`, `.env.example`, and verify `docker compose config` / build
- [x] 4.7 Commit Docker setup (`build(docker): add backend/frontend Dockerfiles, docker-compose, and env template`)

## Phase 5 — GitHub Branch Protection Rulesets & CI
- [x] 5.1 Add `.github/rulesets/{main,alpha,dev}.json` and executable `scripts/apply-github-rulesets.sh`
- [x] 5.2 Attempt to apply rulesets via `gh api` (or document exact status cleanly if blocked by auth/plan)
- [x] 5.3 Add `.github/workflows/ci.yml` and `.github/PULL_REQUEST_TEMPLATE.md`
- [x] 5.4 Commit Phase 5 (`ci(github): add branch protection rulesets, CI workflow, and PR template`)

## Phase 6 — Dual Hydrogen Audit & `README.md`
- [ ] 6.1 Run 2 Gemini 4 Hydrogen audit subagents in parallel (Stack/Build/Docker Auditor + Harness/Specs/Governance Auditor)
- [ ] 6.2 Fix all genuine findings from both Hydrogen reports and re-verify
- [ ] 6.3 Write concise cross-platform `README.md` (macOS/Windows/Linux)
- [ ] 6.4 Mark all tasks complete and commit (`docs(readme): add cross-platform onboarding guide and verification fixes`)

## Phase 7 — Changelog, Push & PR
- [ ] 7.1 Create `CHANGELOG.md` (Keep a Changelog v1.1.0) and commit (`docs(changelog): initialize CHANGELOG.md`)
- [ ] 7.2 Push `feat/setup` to `origin`
- [ ] 7.3 Open plain-language PR from `feat/setup` to `dev` (or print exact command + body if `gh` is not authenticated)
