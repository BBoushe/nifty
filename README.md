# Nifty — Evidence-First Skill Assessment Platform

An evidence-first AI skill assessment and deterministic team-formation platform built during the **FH Dortmund Innovation Driven Software Engineering Block Week**.

Every skill score traces to a word-for-word verified quote in an `assessment-transcript/v1` transcript, untested skills are recorded explicitly as `N/A`, all totals and team placements are computed deterministically in Java/SQL/Timefold, and a human approves every configuration, score, and team before publication.

- **Product Vision & Requirements:** [`docs/discovery-canvas.md`](docs/discovery-canvas.md)
- **Architecture & 9-Step Pipeline:** [`docs/architecture.md`](docs/architecture.md)
- **Living API Reference:** [`docs/api.md`](docs/api.md)
- **Team Workflow & Branching:** [`docs/workflow.md`](docs/workflow.md)
- **AI / SDD / ADD Harness Guide:** [`docs/setup.md`](docs/setup.md)
- **Traceable Decision Registry (`X.Y`) & Open Questions:** [`docs/decisions/index.md`](docs/decisions/index.md)
- **Living Coding Standards:** [`.agents/rules/coding-standards.md`](.agents/rules/coding-standards.md)
- **Shared Spec Layer:** [`openspec/`](openspec/)

---

## Why We Use Docker

Our 5-person team works across **Windows, macOS, and Linux** during a one-week sprint. Docker Compose runs the exact same PostgreSQL 17 database, Java 21 Spring Boot backend, and Vue 3.5 frontend container everywhere so nobody loses half a day debugging local Postgres versions, OS-specific paths, or "works on my machine" issues.

---

## Prerequisites

Install the following tools before starting (follow each official download page for your OS):

| Tool | Required For | Official Download |
|---|---|---|
| **Git** | Version control & `.githooks/` | https://git-scm.com/downloads |
| **Docker Desktop** | Running the full stack (`db` + `backend` + `frontend`) | https://www.docker.com/products/docker-desktop/ |
| **Java 21 SDK (Temurin / OpenJDK)** | Local backend development & `./mvnw test` | https://adoptium.net/temurin/releases/?version=21 |
| **Apache Maven 3.9+** *(optional — `./mvnw` is included)* | Local Maven commands | https://maven.apache.org/download.cgi |
| **Node.js 22+ (LTS)** | Local frontend development (`npm run dev`, lint, typecheck) | https://nodejs.org/ |
| **OpenSpec CLI** *(optional)* | Validating and managing specs (`npm install -g @fission-ai/openspec@latest`) | https://github.com/Fission-AI/OpenSpec |
| **GitHub CLI (`gh`)** *(when `GIT_FORGE=github` or `both`)* | Opening GitHub PRs & applying GitHub rulesets | https://cli.github.com/ |
| **GitLab CLI (`glab`)** *(when `GIT_FORGE=gitlab` or `both`)* | Opening GitLab MRs & checking GitLab pipelines | https://gitlab.com/gitlab-org/cli |
| **RTK (Rust Token Killer)** *(optional, Claude Code users)* | Shrinks shell output before it reaches the LLM context — see [Optional: RTK](#optional-rtk-token-savings-for-claude-code) | https://github.com/rtk-ai/rtk |

---

## Quickstart (Windows, macOS & Linux)

### 1. Clone, configure environment & enable Git hooks

```sh
git clone https://github.com/BBoushe/nifty.git
cd nifty
git checkout dev

# Create your local .env (never committed to Git)
cp .env.example .env

# Activate shared Conventional Commits & branch-protection hooks (once per clone)
git config core.hooksPath .githooks

# Verify local tooling, hooks, and active Git forge (GitHub `gh` vs. GitLab `glab`)
node scripts/check-setup.mjs
```

> **GitLab / GitHub Switch:** Set `GIT_FORGE=github` (default), `gitlab`, or `both` (+ `GITLAB_HOST` for university GitLab) in your local `.env`. With `both`, add the second remote once (`git remote add gitlab https://git.inf.fh-dortmund.de/01/alpac001/nifty.git`; `origin` stays GitHub) and use `node scripts/forge.mjs push|pr|sync|status`. Both `.github/workflows/ci.yml` and `.gitlab-ci.yml` are maintained in parallel, and AI agents inspect only the non-secret forge setting via `node scripts/check-setup.mjs --forge`.

*(On Windows PowerShell, use `Copy-Item .env.example .env` instead of `cp`, or run all commands in Git Bash / WSL.)*

### 2. Start the full stack with Docker Compose

```sh
docker compose up --build
```

- **Frontend:** http://localhost:3000
- **Backend Health:** http://localhost:8080/api/v1/health
- **OpenAPI / Swagger UI:** http://localhost:8080/swagger-ui.html
- **PostgreSQL:** `localhost:5432` (credentials in `.env`)

> The backend boots cleanly even before an API key is configured.

### LLM Evaluator Configuration (`ki:connect` / FH AI)

For the **LLM evaluator** (evidence extraction & constrained judging only), we currently use an **AI Connect (`ki:connect` NRW / FH AI) API key** — hosted in **Aachen / NRW** (EU data residency) and **free for students** *(later support for other OpenAI-API-format providers is planned)*.

1. Copy `.env.example` to `.env` (never commit `.env`).
2. Configure your personal AI Connect (`ki:connect` / FH AI) API key in `.env` under `KICONNECT_API_KEY=`.
3. Endpoint: `https://chat.kiconnect.nrw/api/v1` (`AI_EVALUATOR_BASE_URL`).
4. Pick one of the 3 available evaluator models via `AI_EVALUATOR_MODEL`:
   - `OpenAI GPT OSS 120B` *(default)*
   - `Qwen 3.8 27B`
   - `Mistral Small 4 119B`

### 3. Run Backend & Frontend Locally (Fast Inner Loop)

Start only Postgres in Docker if you prefer hot-reloading on your host machine:

```sh
docker compose up -d db
```

**Backend (`backend/`):**

```sh
cd backend
./mvnw test               # Run JUnit 5 smoke & unit tests (Windows: .\mvnw.cmd test)
./mvnw spring-boot:run    # Run API on http://localhost:8080
```

**Frontend (`frontend/`):**

```sh
cd frontend
npm install
npm run dev               # Vite dev server on http://localhost:5173 (proxies /api to :8080)
npm run typecheck && npm run lint && npm run build
```

---

## Working with AI Agents

All specs (`openspec/`), rules (`AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.agents/rules/`), skills (`.claude/skills/`, `.agents/skills/`), and hooks (`.githooks/`) live as plain files in Git — **zero lock-in** to any single wrapper or IDE.

| Tool / Harness | Auto-Loaded Config | Typical Usage |
|---|---|---|
| **Claude Code (`claude`)** | `CLAUDE.md`, `AGENTS.md`, `.claude/agents/`, `.claude/skills/`, `.claude/settings.json` | Run `claude` in repo root; first run executes the Bootstrap check in `CLAUDE.md`. Great as **Spec Builder**, **Test Writer**, or cold **Reviewer** against `openspec/changes/<feature>/tasks.md`. |
| **Antigravity (`agy`) / Gemini CLI** | `GEMINI.md`, `AGENTS.md`, `.agents/rules/*.md`, `.agents/skills/` | Run `agy` in repo root. Ideal as **Spec Architect** and independent cold **Reviewer / Auditor** against EARS requirements. |
| **OpenAI Codex (`codex`)** | `AGENTS.md`, `.agents/skills/` | Run `codex` in repo root against `openspec/changes/<feature>/` tasks. |
| **Kiro Crew / Kiro IDE (optional)** | `.kiro/steering/nifty-core.md` (bridges to `AGENTS.md` & `openspec/`) | Connect existing **Claude Code** / **Codex** CLI subscriptions as Conductor + parallel Builder + cold Reviewer workers on isolated branches (see [`docs/workflow.md`](docs/workflow.md)). |
| **T3 Code (optional UI)** | Reads `AGENTS.md`, `CLAUDE.md`, and `openspec/` via attached CLI backend | Optional multi-agent UI; works identically alongside direct CLI teammates. |

### Optional: RTK Token Savings for Claude Code

[RTK](https://github.com/rtk-ai/rtk) is a per-developer CLI proxy that compresses noisy command output (`git`, `gh`, `npm run ...`, test runs) before Claude reads it. Nothing in the repo requires it, and it adds no files to the repo. It is a one-time setup on your own machine:

```sh
brew install rtk            # or: curl -fsSL https://raw.githubusercontent.com/rtk-ai/rtk/refs/heads/master/install.sh | sh
rtk gain                    # must print savings stats; "command not found" means the wrong package (Rust Type Kit)
rtk init -g --hook-only     # adds one Bash PreToolUse hook to ~/.claude/settings.json; no RTK.md, no CLAUDE.md edits
```

Do **not** use `cargo install rtk` from crates.io (wrong package). Then run **one extra step**, because RTK rewrites `./mvnw` to the system `mvn` and would bypass our pinned Maven wrapper. Run `rtk config` to print your config file path, and add to it:

```toml
[hooks]
exclude_commands = ["./mvnw", "mvnw", "mvnw.cmd"]
```

Restart Claude Code afterwards. Our safety guards still apply: `.claude/hooks/guard-bash.mjs` strips a leading `rtk` before checking, so `rtk git push origin master` is blocked exactly like `git push origin master`. Undo anytime with `rtk init -g --uninstall`. Details for agents: [`docs/setup.md`](docs/setup.md).

### Branch & Commit Rules (Summary)

- Branch off `dev` (`feat/*`, `fix/*`, `refactor/*`, `chore/*`, `docs/*`).
- Promotion path: `feat/* -> dev` (PR, CI passes) `-> alpha` (PR, ≥2 approvals) `-> master` (PR, ≥4 approvals).
- Direct pushes to `master`/`main` and `alpha` are blocked. Direct pushes to `dev` are restricted to `docs:` and `hotfix:` commits only.
- Use **Conventional Commits** (`feat(scope): ...`, `fix(scope): ...`, `docs(scope): ...`, `chore(scope): ...`).
