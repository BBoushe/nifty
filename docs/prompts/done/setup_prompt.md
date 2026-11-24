You are the lead setup architect running as Gemini 4 Neon in Antigravity CLI (`agy`). Your job is to configure this repository as a multi-agent, spec-driven AI software factory for the **Evidence-First Skill Assessment Platform** defined in `discovery-canvas.md`.

### Core Constraints
- **Tool-agnostic / No T3 Code lock-in:** Some team members will use T3 Code, while others will use CLI tools directly (`claude`, `agy`, `codex`) or IDEs. Every Kiro-style workflow (specs, steering rules, hooks) MUST live as plain files in Git so it works identically regardless of harness.
- **Spec-first execution:** Before scaffolding code or configs, you MUST first create a setup specification plan (`openspec/changes/project-setup/`) and follow it step by step, checking off tasks as you complete them.
- **Logical, revertible commits:** Make small, atomic commits at each logical milestone using **Conventional Commits** (`chore(docs): ...`, `chore(agents): ...`, `feat(backend): ...`, etc.) so any layer can be revised or reverted independently.
- **Verify, don't assume:** Run real build, test, and Docker checks. Do not claim something works unless you ran it and verified the exit code.

---

### Phase 0: Git Branching & Repository Hygiene
1. Inspect the current git status, branches, and remotes (`git status`, `git branch -a`, `git remote -v`).
2. Ensure `dev` and `alpha` branches exist (if this is a fresh repo with only `main`, create `alpha` and `dev` from `main` and push them if remote permissions allow, or document the exact push commands if blocked).
3. Create and switch to a new branch named `feat/setup` branched off `dev`.
4. Ensure a clean root `.gitignore` covering Java/Maven, Node/Vite, Docker overrides, `.env` (never commit secrets), IDE files, OS clutter, and local agent state/cache files.

### Phase 1: `docs/` Folder & Source-of-Truth Foundation
1. Create a `docs/` directory at the repository root.
2. Move (or copy if referenced externally) `discovery-canvas.md` into `docs/discovery-canvas.md` as the foundational product and domain vision document.
3. Create concise reference docs inside `docs/`:
   - `docs/architecture.md`: High-level monorepo layout (`backend/`, `frontend/`, `openspec/`, `docs/`), the 9-step evidence-first pipeline from `discovery-canvas.md` §9, and the JSON Assessment Transcript contract (`assessment-transcript/v1`).
   - `docs/workflow.md`: How the team works with branches (`feat/*` -> `dev` -> `alpha` -> `main`), OpenSpec specs, human-in-the-loop approvals, and multi-agent division of labor (e.g., Builder vs. Verifier agents, optional T3 Code usage vs. pure CLI).
   - `docs/decisions.md`: Record the initial architectural decisions from `discovery-canvas.md` §12–§13, plus open team decisions (e.g., PrimeVue vs. shadcn-vue, LLM provider & EU region).
4. Commit this phase (`chore(docs): initialize docs folder and discovery canvas`).

### Phase 2: Kiro-Like Spec Layer (OpenSpec) & Setup Plan
Replicate Kiro's spec-driven workflow (`requirements.md` in EARS notation -> `design.md` -> `tasks.md`) using **OpenSpec** as the shared source of truth readable by all agents:
1. Check if `openspec` CLI is installed; if not, install it (prefer standalone install or `npm install -g @fission-ai/openspec@latest`) and initialize it non-interactively for all team agents:
   `openspec init --tools claude,antigravity,codex,gemini`
   *(If installation is blocked by environment constraints, scaffold the exact OpenSpec directory structure `openspec/config.yaml`, `openspec/specs/`, `openspec/changes/` manually and document the CLI install command.)*
2. Configure `openspec/config.yaml` with the project context derived from `docs/discovery-canvas.md` (Evidence-First Skill Assessment Platform, Java 21 / Spring Boot 4 / Spring AI 2.0 / Timefold / Postgres / Vue 3.5 / Vite 8, EARS requirements notation, EU AI Act human-in-the-loop constraints, deterministic scoring rule).
3. **Create the setup spec plan first** in `openspec/changes/project-setup/`:
   - `proposal.md`: Scope and goals of the initial repo, monorepo, agent harness, and CI/CD setup.
   - `requirements.md`: Concrete acceptance criteria in **EARS notation** covering Phases 0–7.
   - `design.md`: Folder layout, Docker topology, agent steering/hook architecture, and GitHub branch protection design.
   - `tasks.md`: Ordered checklist you will execute and check off as you build.
4. **Seed the domain specs** in `openspec/specs/` based on `docs/discovery-canvas.md` §14.1 and §14.2:
   - Create baseline spec stubs for the 7 MVP modules (`interviewer-studio`, `assessment-chat`, `evidence-pipeline`, `formula-engine`, `skills-matrix-review`, `team-builder`, `candidate-results`), embedding the seed EARS requirements from §14.2 in the appropriate module specs so the team can start feature work immediately.
5. Commit this phase (`chore(specs): install openspec, project setup plan, and seed domain specs`).

### Phase 3: Multi-Agent Steering, Skills & Kiro-Style Hooks
Configure consistent steering, skills, and automated guardrails across **Claude Code**, **OpenAI Codex**, and **Google Antigravity (`agy`)** without duplicating long walls of text:
1. **`AGENTS.md` (Root universal standard — Codex, Antigravity, Cursor, Copilot, etc.):**
   - Keep it concise and always-on. Define: project purpose, monorepo map, mandatory tech stack & versions, non-negotiable invariants (LLM never computes totals/rankings; missing evidence is always `N/A`; all quotes verified verbatim; human approval required; no secrets committed), spec-first workflow (`openspec/`), Conventional Commits rule, and branch rules.
2. **Antigravity (`agy`) native configuration:**
   - Create root `GEMINI.md` aligning with `AGENTS.md`, adding Gemini/Antigravity-specific guidance (using `openspec/` as source of truth, acting as Verifier/Auditor when paired with Claude Builder, targeted context reading to respect quota).
   - Create scoped rule files directly under `.agents/rules/` (flat directory, no nested subfolders) using valid **snake_case** YAML frontmatter (`trigger: always_on`, `trigger: glob`, `trigger: model_decision`):
     - `.agents/rules/spec-driven.md` (`trigger: always_on`)
     - `.agents/rules/backend-java.md` (`trigger: glob`, `globs: "backend/**"`)
     - `.agents/rules/frontend-vue.md` (`trigger: glob`, `globs: "frontend/**"`)
     - `.agents/rules/git-workflow.md` (`trigger: model_decision`, with `description`)
   - Ensure every file stays well under 24KB.
3. **Claude Code (`CLAUDE.md`, `.claude/`):**
   - Create root `CLAUDE.md` referencing `AGENTS.md` and `openspec/` so rules never drift.
   - Include a dedicated **"First-Run Bootstrap & Self-Configuration"** section inside `CLAUDE.md` that instructs Claude Code, when opened by a developer for the first time, to:
     1. Inspect `.claude/skills/` and `.claude/settings.json`.
     2. Verify or generate any missing project skills (e.g., `spec-implementer`, `backend-spring-check`, `frontend-vue-check`, `evidence-pipeline-guard`), fill in any `[TBD]` team-specific settings (such as chosen UI kit or LLM provider), and confirm local hook executables work on the developer's OS.
   - Pre-seed baseline `.claude/skills/*/SKILL.md` files and `.claude/settings.json` with Kiro-equivalent lifecycle hooks (`PreToolUse` blocking direct pushes to `main`/`alpha` or editing `.env`; `PostToolUse` running lightweight formatting/lint validation when backend/frontend files change).
4. **Cross-agent portable hooks (Kiro `.kiro/hooks/` parity for ALL agents & humans):**
   - Create a version-controlled `.githooks/` directory (`commit-msg` validating Conventional Commits; `pre-push` blocking accidental pushes to `main`/`alpha` and blocking non-`docs`/`hotfix` direct pushes to `dev`) plus a lightweight one-command activation step (`git config core.hooksPath .githooks`) documented in `README.md`.
5. Commit this phase (`chore(agents): configure AGENTS.md, CLAUDE.md, GEMINI.md, rules, skills, and hooks`).

### Phase 4: Local Tooling, Monorepo Scaffold (`backend/`, `frontend/`) & Docker
Follow the technical stack in `docs/discovery-canvas.md` §13:
1. **Local SDK check & installation:**
   - Check if **Java 21 SDK** (`java -version`) and **Maven** (`mvn -v`) are installed on this machine. If missing or wrong version, install Java 21 and Maven cleanly for the current OS (e.g., via SDKMAN, Homebrew, apt, or winget) and verify both work.
2. **Backend (`backend/`):**
   - Initialize a **Java 21 + Maven** (with Maven Wrapper `./mvnw` included for OS-independent builds) **Spring Boot 4.x** project.
   - Configure dependencies per §13:
     - Spring Web (with SSE streaming readiness), Spring Security (2-role skeleton: `PROFESSOR`/`REVIEWER` and `CANDIDATE`), Spring Data JPA, Validation, Actuator (`/actuator/health`).
     - **PostgreSQL** driver + **Flyway** migrations (with an initial `V1__init.sql` baseline migration).
     - **Spring AI 2.0** starter (configured via env vars with provider-agnostic/EU-ready placeholder config so the app boots even before an API key is supplied).
     - **Timefold Solver** dependency ready for the Team Builder module.
     - **springdoc-openapi** for OpenAPI 3 spec generation.
     - Testing: **JUnit 5**, **Mockito**, **Testcontainers** (PostgreSQL).
   - Add a working health/ping endpoint and a passing smoke test (`./mvnw test`).
   - Add a multi-stage `backend/Dockerfile` (using Java 21).
3. **Frontend (`frontend/`):**
   - Initialize a **Vue 3.5 + TypeScript + Vite 8** project (explicitly stay on Vue 3.5 stable, NOT 3.6 RC).
   - Install and wire up: **Vue Router**, **Pinia**, **TanStack Query (`@tanstack/vue-query`)**, **Tailwind CSS v4**, **Apache ECharts (`echarts` + `vue-echarts`)**, and an OpenAPI TypeScript client generation script stub (`package.json` script pointing at the backend OpenAPI endpoint).
   - Leave the component library choice (PrimeVue vs. shadcn-vue) open as noted in §13, with a clean placeholder landing shell showing both views (Assessment View & Ranking View navigation stubs) and verifying connection to the backend health endpoint.
   - Add lint/typecheck/build scripts (`npm run typecheck`, `npm run lint`, `npm run build`) and verify they pass.
   - Add a `frontend/Dockerfile` (multi-stage build + dev target).
4. **Docker Compose & Environment:**
   - Create root `docker-compose.yml` orchestrating:
     - `db`: PostgreSQL (with persistent volume + healthcheck)
     - `backend`: Spring Boot service (depends on healthy `db`, configured via env vars)
     - `frontend`: Vue app (proxying `/api` to `backend`)
   - Create `.env.example` documenting all required and optional environment variables (Postgres credentials, ports, LLM API key/endpoint placeholders).
   - Verify `docker compose config` (and `docker compose build` / startup smoke check if Docker daemon is running).
5. Commit cleanly in logical steps:
   - `feat(backend): scaffold Spring Boot 4 Java 21 backend with Flyway, Spring AI 2.0, and Timefold`
   - `feat(frontend): scaffold Vue 3.5 TypeScript Vite 8 frontend with Tailwind v4 and ECharts`
   - `build(docker): add backend/frontend Dockerfiles, docker-compose, and env template`

### Phase 5: GitHub Branch Protection Rulesets & CI
Implement the required GitHub governance:
- **`main`:** Protected. Requires PR (from `alpha`), **at least 4 approvals**, CI status checks passing, no direct pushes, no force pushes.
- **`alpha`:** Protected. Requires PR (from `dev`), **at least 2 approvals**, CI status checks passing, no direct pushes, no force pushes.
- **`dev`:** Protected. Feature/refactor/fix/chore/etc. branches merge via PR with **0 required approvals** (CI still runs). Direct pushes are disallowed except for `docs` and `hotfix` commits.
  - *Note on GitHub platform limits:* Because GitHub Rulesets cannot natively express "allow direct push bypass *only* when commit prefix is `docs:` or `hotfix:`" via a single toggle, implement defense-in-depth:
    1. Store declarative GitHub Ruleset JSON definitions in `.github/rulesets/` (`main.json`, `alpha.json`, `dev.json`).
    2. Create an executable script `scripts/apply-github-rulesets.sh` using `gh api` to apply these rulesets to the GitHub repository. Run it if `gh` is authenticated with repo admin rights; if permissions or repo plan (e.g., private free-tier repo) block API creation, log the exact status cleanly without faking success and document how the repo admin applies or adjusts them.
    3. Create `.github/workflows/ci.yml` that runs on PRs and pushes to `dev`, `alpha`, and `main`:
       - Backend build & tests (`./mvnw test`)
       - Frontend typecheck, lint & build
       - Conventional commit validation
       - Direct-push policy check on `dev` (flags any direct push to `dev` whose commits do not match `^(docs|hotfix)(\(.+\))?!?:`).
    4. Add `.github/PULL_REQUEST_TEMPLATE.md` prompting contributors to keep PRs short and explain **What** and **Why** in plain language.
- Commit this phase (`ci(github): add branch protection rulesets, CI workflow, and PR template`).

### Phase 6: Dual Hydrogen Verification Pass & `README.md`
1. **Spin up 2 Gemini 4 Hydrogen subagents in parallel** to audit the completed work against `openspec/changes/project-setup/`:
   - **Hydrogen Agent 1 (Stack, Build & Docker Auditor):** Independently verify Java 21 / Maven build & tests, frontend typecheck / lint / build, Dockerfiles & `docker-compose.yml` validity, `.env.example` completeness, and `.github/workflows/ci.yml` syntax.
   - **Hydrogen Agent 2 (Agent Harness, Specs & Governance Auditor):** Independently verify `AGENTS.md`, `CLAUDE.md` (including the Claude Code first-run bootstrap instructions), `GEMINI.md`, `.agents/rules/*.md` (valid snake_case YAML frontmatter and under 24KB), `.claude/settings.json` valid JSON, OpenSpec structure & EARS requirements from `discovery-canvas.md`, `.githooks/` executability, and `.github/rulesets/` accuracy.
2. Review both Hydrogen reports yourself (as Neon), fix every genuine defect they find, re-run failed checks, and mark all tasks complete in `openspec/changes/project-setup/tasks.md`.
3. **Update `README.md`** for a 5-person team using **Windows, macOS, and Linux**:
   - Clear project summary (linked to `docs/discovery-canvas.md`).
   - **Why we use Docker:** Brief, practical explanation (identical Postgres/Java/Node runtime across Windows/Mac/Linux, zero "works on my machine" issues during a 1-week block week).
   - **Prerequisites:** List what needs to be installed (Git, Docker Desktop, Java 21 SDK, Maven, Node.js 22+, optional `openspec` CLI / `gh` CLI). Keep it crisp: if installing a tool takes more than one line, simply state that it must be installed and link to its official download page instead of writing long OS-by-OS tutorials.
   - **Quickstart:** Exact commands to clone, copy `.env.example` to `.env`, activate `.githooks`, and start the project via Docker Compose (and how to run backend/frontend locally if needed).
   - **Working with AI Agents:** Short table showing how to run Claude Code, Antigravity (`agy`), Codex, or T3 Code against the shared `openspec/` specs.
4. Commit fixes and README (`docs(readme): add cross-platform onboarding guide and verification fixes`).

### Phase 7: Changelog, Push `feat/setup` & Open PR to `dev`
1. Create `CHANGELOG.md` following **Keep a Changelog** conventions (v1.1.0, `[Unreleased]` section summarizing all Added/Changed items cleanly). Commit it (`docs(changelog): initialize CHANGELOG.md`).
2. Push the `feat/setup` branch to `origin` (`git push -u origin feat/setup`). Do NOT push directly to `dev`, `alpha`, or `main`.
3. Open a Pull Request from `feat/setup` into `dev` using `gh pr create --base dev --head feat/setup`. (If `gh` is not authenticated, print the exact `gh pr create` command and PR body for me to run.)
4. **PR Description Rules:**
   - Focus strictly on **What** was set up and **Why** it helps the team — skip implementation minutiae of *how*.
   - Write in short, plain, human-friendly language so every team member regardless of experience level understands it in under 60 seconds.
   - End with a short list of any manual actions needed from the repo admin (e.g., applying GitHub rulesets if API access was unavailable, or deciding open questions like PrimeVue vs. shadcn-vue).