# Proposal: Project Setup (`project-setup`)

## Problem & Motivation

A 5-person student engineering team needs to deliver the **Evidence-First Skill Assessment Platform** (`docs/discovery-canvas.md`) within a one-week block week across macOS, Windows, and Linux. Team members will use different AI harnesses (Claude Code, Antigravity `agy`, OpenAI Codex, optional T3 Code, and traditional IDEs). Without a shared spec layer, unified steering rules, reproducible Docker environment, and branch/CI guardrails from Day 1, agents and developers will drift, break builds, or violate critical domain invariants.

## Scope (In Scope)

1. **Git Hygiene & Branching (Phase 0):** Ensure `master`/`dev`/`alpha` branches exist, branch `feat/setup` off `dev`, harden root `.gitignore`.
2. **Documentation Foundation (Phase 1):** Move `discovery-canvas.md` into `docs/` and author concise `architecture.md`, `workflow.md`, and `decisions.md`.
3. **Spec-Driven Layer (Phase 2):** Install and initialize OpenSpec (`openspec/`), author this `project-setup` change spec, and seed the 7 MVP domain specs in `openspec/specs/` with all seed EARS requirements from `docs/discovery-canvas.md` §14.2.
4. **Tool-Agnostic Multi-Agent Harness (Phase 3):** Configure `AGENTS.md`, `GEMINI.md`, `.agents/rules/*.md`, `CLAUDE.md` (with First-Run Bootstrap), `.claude/skills/`, `.claude/settings.json` hooks, and portable `.githooks/`.
5. **Monorepo Scaffold & Docker (Phase 4):** Verify/install Java 21 + Maven, scaffold Spring Boot 4.x backend (`backend/`) and Vue 3.5 + Vite 8 frontend (`frontend/`), orchestrate Postgres + backend + frontend via `docker-compose.yml` and `.env.example`, and verify real builds/tests.
6. **GitHub Governance & CI (Phase 5):** Declarative branch rulesets in `.github/rulesets/`, `scripts/apply-github-rulesets.sh`, `.github/workflows/ci.yml`, and `.github/PULL_REQUEST_TEMPLATE.md`.
7. **Independent Audit, Onboarding Docs & PR (Phases 6–7):** Run dual Gemini 4 Hydrogen audit subagents, remediate findings, write cross-platform `README.md` and `CHANGELOG.md`, push `feat/setup`, and open PR to `dev`.

## Out of Scope

- Implementing domain business logic for the 7 MVP modules (only baseline spec stubs and runnable backend/frontend skeletons are included).
- Selecting the final UI component library (PrimeVue vs. shadcn-vue) or production EU LLM provider (documented as open team decisions in `docs/decisions.md`).
- Deploying to production cloud infrastructure.
