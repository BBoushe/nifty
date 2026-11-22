# Nifty — Evidence-First Skill Assessment Platform

An evidence-first AI skill assessment and deterministic team-formation platform built during the **FH Dortmund Innovation Driven Software Engineering Block Week**.

Every skill score traces to a word-for-word verified quote in an `assessment-transcript/v1` transcript, untested skills are recorded explicitly as `N/A`, all totals and team placements are computed deterministically in Java/SQL/Timefold, and a human approves every configuration, score, and team before publication.

- **Product Vision & Requirements:** [`docs/discovery-canvas.md`](docs/discovery-canvas.md)
- **Architecture & 9-Step Pipeline:** [`docs/architecture.md`](docs/architecture.md)
- **Team Workflow & Branching:** [`docs/workflow.md`](docs/workflow.md)
- **Architectural Decisions & Open Questions:** [`docs/decisions.md`](docs/decisions.md)
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
| **GitHub CLI (`gh`)** *(optional)* | Opening PRs & applying GitHub rulesets | https://cli.github.com/ |

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
```

*(On Windows PowerShell, use `Copy-Item .env.example .env` instead of `cp`, or run all commands in Git Bash / WSL.)*

### 2. Start the full stack with Docker Compose

```sh
docker compose up --build
```

- **Frontend:** http://localhost:3000
- **Backend Health:** http://localhost:8080/api/v1/health
- **OpenAPI / Swagger UI:** http://localhost:8080/swagger-ui.html
- **PostgreSQL:** `localhost:5432` (credentials in `.env`)

> The backend boots cleanly with placeholder LLM settings (`AI_PROVIDER_MODE=placeholder`) even before an API key is configured.

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
| **Claude Code (`claude`)** | `CLAUDE.md`, `AGENTS.md`, `.claude/skills/`, `.claude/settings.json` | Run `claude` in repo root; first run executes the Bootstrap check in `CLAUDE.md`. Great as **Builder** implementing `openspec/changes/<feature>/tasks.md`. |
| **Antigravity (`agy`) / Gemini CLI** | `GEMINI.md`, `AGENTS.md`, `.agents/rules/*.md`, `.agents/skills/` | Run `agy` in repo root. Ideal as **Spec Architect** and independent **Verifier / Auditor** against EARS requirements. |
| **OpenAI Codex (`codex`)** | `AGENTS.md`, `.agents/skills/` | Run `codex` in repo root against `openspec/changes/<feature>/` tasks. |
| **T3 Code (optional UI)** | Reads `AGENTS.md`, `CLAUDE.md`, and `openspec/` via attached CLI backend | Optional multi-agent UI; works identically alongside direct CLI teammates. |

### Branch & Commit Rules (Summary)

- Branch off `dev` (`feat/*`, `fix/*`, `refactor/*`, `chore/*`, `docs/*`).
- Promotion path: `feat/* -> dev` (PR, CI passes) `-> alpha` (PR, ≥2 approvals) `-> master` (PR, ≥4 approvals).
- Direct pushes to `master`/`main` and `alpha` are blocked. Direct pushes to `dev` are restricted to `docs:` and `hotfix:` commits only.
- Use **Conventional Commits** (`feat(scope): ...`, `fix(scope): ...`, `docs(scope): ...`, `chore(scope): ...`).
