# AGENTS.md — Universal Engineering Rules

This repository builds the **Evidence-First Skill Assessment Platform** (see [`docs/discovery-canvas.md`](docs/discovery-canvas.md)). All AI agents (Claude Code, Antigravity `agy`, Codex, Cursor, Copilot, T3 Code) and human contributors follow these rules identically.

## 1. Monorepo Map

- `backend/` — Java 21, Spring Boot 4.x, Maven (`./mvnw`), Spring Security (2 roles: `PROFESSOR`/`REVIEWER`, `CANDIDATE`), Spring Web (SSE), Spring Data JPA, Spring AI 2.0, Timefold Solver, PostgreSQL (JSONB), Flyway, springdoc-openapi, JUnit 5, Mockito, Testcontainers.
- `frontend/` — Vue 3.5 (**stable only — NEVER upgrade to Vue 3.6 RC**), TypeScript, Vite 8, Vue Router, Pinia, TanStack Query (`@tanstack/vue-query`), Tailwind CSS v4, Apache ECharts (`vue-echarts`). UI component kit (`PrimeVue` vs. `shadcn-vue`) is still TBD (`docs/decisions/index.md` O1).
- `openspec/` — Spec-driven source of truth (`config.yaml`, `specs/<module>/spec.md`, `changes/<change>/`).
- `docs/` — Product vision (`discovery-canvas.md`), `architecture.md`, `api.md`, `workflow.md`, hierarchical decisions (`decisions/index.md`), and AI/SDD/ADD harness guide (`setup.md`).
- `scripts/` — Zero-dependency harness helpers (`record-decision.mjs` for `X.Y` decisions, `check-setup.mjs` for secret-safe GitHub/GitLab forge switch & preflight checks).
- `.githooks/` — Portable Git hooks (`git config core.hooksPath .githooks`) enforcing secret scans, Conventional Commits, branch protection, and fast compile gates for all tools and humans.

## 2. Non-Negotiable Domain & Regulatory Invariants

1. **Deterministic math only:** LLMs **MUST NEVER** compute totals, fit scores, coverage, rankings, cohort gap statistics, or team-balance metrics. All numerical aggregation runs in Java/SQL/Timefold.
2. **Explicit `N/A`:** Missing verified evidence for a skill **MUST** be recorded as `N/A` (never `0` or `1`) and excluded from fit calculations. Fit and coverage are always reported separately.
3. **Verbatim quote verification:** Every evidence quote extracted by an LLM **MUST** appear word-for-word in the referenced `assessment-transcript/v1` entry; otherwise it **MUST** be discarded.
4. **Calculated confidence:** Confidence (`0..1`) is computed deterministically from measurable signals (evidence count, quote verification ratio, judge agreement, question origin, prompted vs. unprompted terms) — never LLM self-report.
5. **Human-in-the-loop (EU AI Act Annex III & GDPR):** Humans approve interviewer configurations, flagged/final scores, and team placements. Every score override requires a reason and writes an immutable audit log entry.
6. **Candidate transparency timing:** Formula, weights, and verified evidence are revealed to the candidate **only after** human approval — never during the interview.
7. **Fresh context for team-builder LLM:** Mode B (text → constraints) and Mode C (validated LLM proposal) must run in a fresh, empty LLM context and pass deterministic validation.
8. **Zero committed secrets or real PII:** Never commit `.env`, API keys, credentials, or real student transcripts. Use `.env.example` and clearly labelled synthetic fixtures.

## 3. Spec-First Workflow, Decision Traceability & Coding Standards

- **Specs First (`openspec/`):** Before building any non-trivial feature, read `openspec/specs/<module>/spec.md` and create/update a change spec in `openspec/changes/<change>/` (`proposal.md`, `specs/`, `design.md`, `tasks.md`). Verify every claim with real build/test exit codes before checking off tasks.
- **Traceable Decisions (`docs/decisions/`):** Whenever a `question-round` is answered OR the prompter changes, redirects, or overrides a design, architecture, coding, team, or direction choice, record it via `node scripts/record-decision.mjs` (see `record-decision` skill) and leave concise inline code/spec pointers (`// Decision X.Y: <short why>`).
- **Living Coding Standards (`.agents/rules/coding-standards.md`):** Canonical coding rules enforced across all tools (ETC/DRY, small testable functions, avoid Java Streams in hot/repeated functions, living [`docs/api.md`](docs/api.md) synced with OpenAPI, minimal reusable classes, frontend reuse/HOFs/feature-grouped views). Append newly agreed team rules there — never duplicate into `.claude/rules/`.

### Scoped Rules & Templates Dispatch (Cross-Tool — Decision 3.4)

Tools that do not auto-parse `.agents/rules/*.md` YAML frontmatter (Claude Code, Codex, Kiro, Gemini CLI, T3 Code) MUST read the canonical file(s) below whenever the trigger applies:

| When You Are... | Mandatory Canonical File(s) to Read / Follow |
|---|---|
| Starting any task | `.agents/rules/coding-standards.md`, `.agents/rules/spec-driven.md` (auto-imported in `CLAUDE.md`) |
| Editing `backend/**` | `.agents/rules/backend-java.md`, `docs/api.md` |
| Editing `frontend/**` | `.agents/rules/frontend-vue.md` |
| Branching, committing, pushing, or opening a PR/MR | `.agents/rules/git-workflow.md`, `.github/PULL_REQUEST_TEMPLATE.md` (mirrored in `.gitlab/merge_request_templates/Default.md`), `pr-memo` skill |
| Answering a `question-round` or receiving a prompter direction change | `record-decision` skill (`node scripts/record-decision.mjs`) |

## 4. Git, Forge Switch (`gh` / `glab`) & Commit Rules

- **Forge Switch (GitHub primary, GitLab secondary):** Check the active forge via `node scripts/check-setup.mjs --forge` (reads only non-secret `GIT_FORGE` / `GITLAB_HOST` from `.env` — never inspect `.env` directly). Use `gh` when `GIT_FORGE=github`, `glab` when `GIT_FORGE=gitlab`, and for `GIT_FORGE=both` always go through `node scripts/forge.mjs push | pr | sync | status` (remote `origin` = GitHub, remote `gitlab` = GitLab; Decision 1.8). Merge PRs on GitHub only, then run `node scripts/forge.mjs sync` so GitLab fast-forwards instead of drifting.
- **Branches:** Work on `feat/*`, `fix/*`, `refactor/*`, `chore/*`, or `docs/*` branched from `dev`. Promotion flow: `feat/* -> dev -> alpha -> master` (also covers `main` if renamed).
- **Protected branches:** Never push directly to `master`, `main`, or `alpha`. Direct pushes to `dev` are permitted **only** when every commit is prefixed with `docs:` or `hotfix:`.
- **Conventional Commits (mandatory):** `type(scope): imperative summary` (e.g., `feat(backend): ...`, `fix(frontend): ...`, `chore(specs): ...`, `docs(readme): ...`).
- **PRs / MRs:** Every PR/MR body MUST use the unified repository template (`.github/PULL_REQUEST_TEMPLATE.md` / `.gitlab/merge_request_templates/Default.md`) via the forge-aware `pr-memo` skill — keep it concise (<60s read) without dropping the `Spec / Issue Reference` or `Verification` checklists (Decision 3.4).

## 5. Adopting Agent Roles (Cross-Tool Parity)

Every harness (Claude Code via `.claude/agents/`, Antigravity `agy`, Codex, Kiro Crew) uses the same three-role separation so builders never self-approve domain invariants:

| Role | Canonical Prompt | Scope & Guardrail |
|---|---|---|
| **Spec Builder** | `.claude/agents/spec-builder.md` | Implements unchecked `openspec/changes/<change>/tasks.md` items, escalates `O1`–`O7` TBDs via `question-round`, verifies builds/tests, appends 5-line `## Handoff Log` entry. |
| **Cold Reviewer** | `.claude/agents/reviewer.md` | **Read-only** (`guard-agent.mjs --mode readonly`). Audits *only* `git diff dev...HEAD` + `openspec/` specs (no builder chat history). Outputs <=30-line `Verdict / Blocking / Convention Candidates`. |
| **Test Writer** | `.claude/agents/test-writer.md` | **Tests-only** (`guard-agent.mjs --mode tests-only`: `backend/src/test/**`, `frontend/src/**/*.test.*`, `**/*.spec.*`). Writes tests strictly from EARS specs with invariant-named tests. |

## 6. Cross-Tool Rule & Hook Parity

Claude Code enforces these checks automatically via `.claude/hooks/*.mjs`; Antigravity (`agy`), Codex, Kiro CLI/Crew, and humans follow the exact same checks via `.githooks/` and manual discipline:

| Guardrail | Automated in Claude Code | Equivalent for Antigravity / Codex / Kiro / Humans |
|---|---|---|
| Block reading/editing `.env*` (allow `.env.example`) | `.claude/hooks/guard-files.mjs` + `permissions.deny` | Never open, grep, or print `.env` files; inspect `.env.example` only. |
| Block shell `.env` leaks & `git add && git commit` | `.claude/hooks/guard-bash.mjs` | Stage with `git add` first, then run `git commit` separately; never `cat`/`grep` `.env`. |
| Scan staged diff for API keys / private keys | `.claude/hooks/guard-bash.mjs` | Enforced automatically for all tools via `.githooks/pre-commit`. |
| Block direct commits/pushes to `master`/`main`/`alpha` & non-`docs:`/`hotfix:` `dev` pushes | `.claude/hooks/guard-bash.mjs` | Enforced automatically for all tools via `.githooks/pre-commit` + `.githooks/pre-push` + GitHub Rulesets. |
| Fast path-scoped compile gate (no slow Testcontainers) | `.githooks/pre-push` -> `.claude/hooks/test-gate.mjs` | Enforced automatically on push via `.githooks/pre-push` for all tools and humans (`NIFTY_SKIP_GATE=1` escape hatch). |
| Role write boundaries (`readonly` / `tests-only`) | `.claude/hooks/guard-agent.mjs` | Honor role boundaries strictly when acting as cold reviewer or spec test-writer. |
