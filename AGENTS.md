# AGENTS.md — Universal Engineering Rules

This repository builds the **Evidence-First Skill Assessment Platform** (see [`docs/discovery-canvas.md`](docs/discovery-canvas.md)). All AI agents (Claude Code, Antigravity `agy`, Codex, Cursor, Copilot, T3 Code) and human contributors follow these rules identically.

## 1. Monorepo Map

- `backend/` — Java 21, Spring Boot 4.x, Maven (`./mvnw`), Spring Security (2 roles: `PROFESSOR`/`REVIEWER`, `CANDIDATE`), Spring Web (SSE), Spring Data JPA, Spring AI 2.0, Timefold Solver, PostgreSQL (JSONB), Flyway, springdoc-openapi, JUnit 5, Mockito, Testcontainers.
- `frontend/` — Vue 3.5 (**stable only — NEVER upgrade to Vue 3.6 RC**), TypeScript, Vite 8, Vue Router, Pinia, TanStack Query (`@tanstack/vue-query`), Tailwind CSS v4, Apache ECharts (`vue-echarts`). UI component kit (`PrimeVue` vs. `shadcn-vue`) is still TBD (`docs/decisions.md` O1).
- `openspec/` — Spec-driven source of truth (`config.yaml`, `specs/<module>/spec.md`, `changes/<change>/`).
- `docs/` — Product vision (`discovery-canvas.md`), `architecture.md`, `workflow.md`, `decisions.md`.
- `.githooks/` — Portable Git hooks (`git config core.hooksPath .githooks`).

## 2. Non-Negotiable Domain & Regulatory Invariants

1. **Deterministic math only:** LLMs **MUST NEVER** compute totals, fit scores, coverage, rankings, cohort gap statistics, or team-balance metrics. All numerical aggregation runs in Java/SQL/Timefold.
2. **Explicit `N/A`:** Missing verified evidence for a skill **MUST** be recorded as `N/A` (never `0` or `1`) and excluded from fit calculations. Fit and coverage are always reported separately.
3. **Verbatim quote verification:** Every evidence quote extracted by an LLM **MUST** appear word-for-word in the referenced `assessment-transcript/v1` entry; otherwise it **MUST** be discarded.
4. **Calculated confidence:** Confidence (`0..1`) is computed deterministically from measurable signals (evidence count, quote verification ratio, judge agreement, question origin, prompted vs. unprompted terms) — never LLM self-report.
5. **Human-in-the-loop (EU AI Act Annex III & GDPR):** Humans approve interviewer configurations, flagged/final scores, and team placements. Every score override requires a reason and writes an immutable audit log entry.
6. **Candidate transparency timing:** Formula, weights, and verified evidence are revealed to the candidate **only after** human approval — never during the interview.
7. **Fresh context for team-builder LLM:** Mode B (text → constraints) and Mode C (validated LLM proposal) must run in a fresh, empty LLM context and pass deterministic validation.
8. **Zero committed secrets or real PII:** Never commit `.env`, API keys, credentials, or real student transcripts. Use `.env.example` and clearly labelled synthetic fixtures.

## 3. Spec-First Workflow (`openspec/`)

- Before building any non-trivial feature, read the relevant spec in `openspec/specs/<module>/spec.md` and create/update a change spec in `openspec/changes/<change>/` (`proposal.md`, `requirements.md` in EARS notation, `design.md`, `tasks.md`).
- Implement tasks incrementally, verify every claim with real build/test exit codes, and check off completed items in `tasks.md`.

## 4. Git & Commit Rules

- **Branches:** Work on `feat/*`, `fix/*`, `refactor/*`, `chore/*`, or `docs/*` branched from `dev`. Promotion flow: `feat/* -> dev -> alpha -> master` (also covers `main` if renamed).
- **Protected branches:** Never push directly to `master`, `main`, or `alpha`. Direct pushes to `dev` are permitted **only** when every commit is prefixed with `docs:` or `hotfix:`.
- **Conventional Commits (mandatory):** `type(scope): imperative summary` (e.g., `feat(backend): ...`, `fix(frontend): ...`, `chore(specs): ...`, `docs(readme): ...`).
- **PRs:** Keep PRs small, focused, and written in plain language explaining **What** and **Why**.
