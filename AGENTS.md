# AGENTS.md — Universal Engineering Rules

This repository builds the **Evidence-First Skill Assessment Platform** (see [`docs/discovery-canvas.md`](docs/discovery-canvas.md)). All AI agents (Claude Code, Antigravity `agy`, Codex, Cursor, Copilot, T3 Code) and human contributors follow these rules in the same way.

## 1. Monorepo Map

- `backend/` — Java 21, Spring Boot 4.x, Maven (`./mvnw`), Spring Security (2 roles: `PROFESSOR`, `CANDIDATE`, one enum, easy to extend, Decision 1.15), Spring Web (SSE). It also uses Spring Data JPA, Spring AI 2.0, PostgreSQL (JSONB), Flyway, springdoc-openapi, JUnit 5, Mockito and Testcontainers.
- `frontend/` — Vue 3.5 (**stable only — NEVER upgrade to Vue 3.6 RC**), TypeScript, Vite 8, Vue Router, Pinia, TanStack Query (`@tanstack/vue-query`), Tailwind CSS v4, Apache ECharts (`vue-echarts`). The UI component kit is PrimeVue, themed from the design tokens (Decision 1.14).
- `openspec/` — Source of truth for specs (`config.yaml`, `specs/<module>/spec.md`, `changes/<change>/`).
- `docs/` — Product vision (`discovery-canvas.md`), `architecture.md`, `api.md`, `workflow.md`, hierarchical decisions (`decisions/index.md`), and AI/SDD/ADD harness guide (`setup.md`).
- `scripts/` — Harness helpers with no dependencies. `record-decision.mjs` records `X.Y` decisions. `check-setup.mjs` runs preflight checks and the GitHub/GitLab forge switch without showing secrets.
- `.githooks/` — Git hooks (`git config core.hooksPath .githooks`). They enforce secret scans, Conventional Commits, branch protection and compile gates for all tools and humans.

## 2. Non-Negotiable Domain & Regulatory Invariants

1. **Deterministic math only:** LLMs **MUST NEVER** compute totals, fit scores, coverage, rankings, cohort gap statistics, or team-balance metrics. All numerical aggregation runs in Java/SQL. The evaluator LLM returns machine-parsable levels only (Decision 2.25).
2. **Explicit `N/A`:** The system **MUST** record missing verified evidence for a skill as `N/A` (never `0` or `1`) and exclude it from fit calculations. Always report fit and coverage separately.
3. **Verbatim quote verification:** Every evidence quote that an LLM extracts **MUST** appear word-for-word in the referenced `assessment-transcript/v1` entry. Otherwise the system **MUST** discard the quote.
4. **Calculated confidence:** The system computes confidence (`0..1`) for each (question, skill) score deterministically from measurable signals. The signals are evidence count, quote verification ratio, judge agreement, question origin, answer length, and prompted vs. unprompted terms. Never use LLM self-report.
5. **Human-in-the-loop (EU AI Act Annex III & GDPR):** Humans approve interviewer configurations and team placements. The professor sets a confidence threshold per question and a minimum coverage per assessment. The system releases an attempt automatically only when all gates pass. The gates: every scored (not `N/A`) score meets its threshold, coverage meets the minimum, and no score has a flag. A human reviews and approves every other attempt (Decision 2.10). Every score override requires a reason and writes an immutable audit log entry.
6. **Candidate transparency timing:** The system reveals the formula, weights and verified evidence to the candidate **only after** the results are released (Decision 2.10). Never reveal them during the interview.
7. **Fresh context for team-builder LLM:** Mode B (text → constraints) and Mode C (LLM proposal that passed validation) must run in a fresh, empty LLM context. They must pass deterministic validation.
8. **Zero committed secrets or real PII:** Never commit `.env`, API keys, credentials, or real student data. Real data lives only in the database until the professor deletes the assessment (Decision 4.4). Tests use `.env.example` and synthetic fixtures with clear labels.
9. **Rubric-blind interviewer:** The interviewer LLM **MUST NEVER** receive rubrics, level descriptions, scores, or evaluator output. The evaluator runs after commit and reads only the append-only conversation ledger and the rubric (Decision 1.12).
10. **Append-only ledger:** The system appends every candidate answer and every interviewer reply to the ledger with a server timestamp. Code never updates or deletes ledger rows, except when the professor deletes the assessment (Decision 1.11).

## 3. Spec-First Workflow, Decision Traceability & Coding Standards

- **Context Read Order (Decision 3.13):** Read `docs/architecture.md` and `docs/feature_history.md` before you scan source code. Read `docs/api.md` before you change a controller. Never scan the whole `backend/src` or `frontend/src` tree to orient yourself.
- **Specs First (`openspec/`):** Before you build any non-trivial feature, read `openspec/specs/<module>/spec.md`. Then create or update a change spec in `openspec/changes/<change>/` (`proposal.md`, `specs/`, `design.md`, `tasks.md`). Check every claim with real build/test exit codes before you mark a task as complete.
- **Traceable Decisions (`docs/decisions/`):** Record a decision with `node scripts/record-decision.mjs` (see `record-decision` skill) in each of these cases:
  - A `question-round` gets an answer.
  - The prompter changes, redirects, or overrides a design, architecture, coding, team, or direction choice.

  Leave concise inline code/spec pointers (`// Decision X.Y: <short why>`).
- **Living Coding Standards (`.agents/rules/coding-standards.md`):** Canonical coding rules that all tools enforce. They cover ETC/DRY, small testable functions, no Java Streams in hot/repeated functions, living [`docs/api.md`](docs/api.md) synced with OpenAPI, minimal reusable classes, and frontend reuse/HOFs/feature-grouped views. Append newly agreed team rules there. Never duplicate them into `.claude/rules/`.

### Scoped Rules & Templates Dispatch (Cross-Tool — Decision 3.4)

These tools do not auto-parse `.agents/rules/*.md` YAML frontmatter: Claude Code, Codex, Kiro, Gemini CLI and T3 Code. They MUST read the canonical file(s) below whenever the trigger applies:

| When You Are... | Mandatory Canonical File(s) to Read / Follow |
|---|---|
| Starting any task | `.agents/rules/coding-standards.md`, `.agents/rules/spec-driven.md` (auto-imported in `CLAUDE.md`) |
| Editing `backend/**` | `.agents/rules/backend-java.md`, `docs/api.md` |
| Editing `frontend/**` | `.agents/rules/frontend-vue.md` |
| Branching, committing, pushing, or opening a PR/MR | `.agents/rules/git-workflow.md`, `.github/PULL_REQUEST_TEMPLATE.md` (mirrored in `.gitlab/merge_request_templates/Default.md`), `pr-memo` skill |
| Answering a `question-round` or receiving a prompter direction change | `record-decision` skill (`node scripts/record-decision.mjs`) |
| Using a library API from the pinned stack | `docs/ai_docs/<lib>.md` (index: `docs/ai_docs/README.md`). Read the note before you write the code. |
| Proposing or archiving an OpenSpec change | `docs/feature_backlog.md` + `docs/feature_history.md`. When you propose a change, set its backlog row to `in progress`. When you archive it, set the row to `done` (other rows refer to it by number) and add a history entry (the harness test fails without it). |
| Writing or editing docs/specs | `asd-ste100` skill (`.agents/skills/asd-ste100/`), `.agents/rules/spec-driven.md` rule 6 |
| Running several changes in parallel, or managing their PRs/MRs | `orchestrate-changes` skill (`.agents/skills/orchestrate-changes/`), `node scripts/forge.mjs prs`. Agents never merge. Ask the user before every merge, with an architecture summary (Decision 3.11). |
| Finishing an AI session that made architecture or product decisions, or handed work to builder agents | `docs/ai_logs/README.md`. Add a dated handover file and its index row (Decision 3.12). |

## 4. Git, Forge Switch (`gh` / `glab`) & Commit Rules

- **Forge Switch (GitHub primary, GitLab secondary):** Check the active forge with `node scripts/check-setup.mjs --forge`. It reads only the non-secret `GIT_FORGE` / `GITLAB_HOST` from `.env`. Never inspect `.env` directly. Use `gh` when `GIT_FORGE=github`. Use `glab` when `GIT_FORGE=gitlab`. When `GIT_FORGE=both`, always use `node scripts/forge.mjs push | pr | sync | status` (Decision 1.8: remote `origin` = GitHub, remote `gitlab` = GitLab). Merge PRs on GitHub only. Then run `node scripts/forge.mjs sync` so GitLab fast-forwards instead of drifting.
- **Branches:** Work on `feat/*`, `fix/*`, `refactor/*`, `chore/*`, or `docs/*` branches. Create them from `dev`. Promotion flow: `feat/* -> dev -> alpha -> master`. The flow also covers `main` if you rename `master`.
- **Protected branches:** Never push directly to `master`, `main`, or `alpha`. Every change reaches `dev` through a PR, including `docs:` and `hotfix:` commits. The GitHub `dev` ruleset rejects direct pushes (Decision 3.10).
- **Conventional Commits (mandatory):** `type(scope): imperative summary` (for example, `feat(backend): ...`, `fix(frontend): ...`, `chore(specs): ...`, `docs(readme): ...`).
- **PRs / MRs:** Every PR/MR body MUST use the repository template (`.github/PULL_REQUEST_TEMPLATE.md` / `.gitlab/merge_request_templates/Default.md`) with the forge-aware `pr-memo` skill. Keep it concise (<60s read). Do not drop the `Spec / Issue Reference` or `Verification` checklists (Decision 3.4).

## 5. Adopting Agent Roles (Cross-Tool Parity)

Every harness (Claude Code via `.claude/agents/`, Antigravity `agy`, Codex, Kiro Crew) uses the same three roles. This separation makes sure that builders never approve their own domain invariants:

| Role | Canonical Prompt | Scope & Guardrail |
|---|---|---|
| **Spec Builder** | `.claude/agents/spec-builder.md` | Implements unchecked `openspec/changes/<change>/tasks.md` items. Escalates open items in `docs/decisions/index.md` §2 through `question-round`. Checks builds and tests. Appends a 5-line `## Handoff Log` entry. |
| **Cold Reviewer** | `.claude/agents/reviewer.md` | **Read-only** (`guard-agent.mjs --mode readonly`). Audits *only* `git diff dev...HEAD` + `openspec/` specs (no builder chat history). Outputs a <=30-line `Verdict / Blocking / Convention Candidates` report. |
| **Test Writer** | `.claude/agents/test-writer.md` | **Tests-only** (`guard-agent.mjs --mode tests-only`: `backend/src/test/**`, `frontend/src/**/*.test.*`, `**/*.spec.*`). Writes tests strictly from EARS specs, with invariant-named tests. |

## 6. Cross-Tool Rule & Hook Parity

Claude Code enforces these checks automatically with `.claude/hooks/*.mjs`. Antigravity (`agy`), Codex, Kiro CLI/Crew, and humans follow the same checks through `.githooks/` and manual discipline:

| Guardrail | Automated in Claude Code | Equivalent for Antigravity / Codex / Kiro / Humans |
|---|---|---|
| Block reading/editing `.env*` (allow `.env.example`) | `.claude/hooks/guard-files.mjs` + `permissions.deny` | Never open, grep, or print `.env` files. Inspect `.env.example` only. |
| Block shell `.env` leaks & `git add && git commit` | `.claude/hooks/guard-bash.mjs` | Run `git add` first. Then run `git commit` separately. Never `cat`/`grep` `.env`. |
| Scan staged diff for API keys / private keys | `.claude/hooks/guard-bash.mjs` | `.githooks/pre-commit` enforces this automatically for all tools. |
| Block direct commits/pushes to `master`/`main`/`alpha` & non-`docs:`/`hotfix:` `dev` pushes (GitHub rejects all direct `dev` pushes, Decision 3.10) | `.claude/hooks/guard-bash.mjs` | `.githooks/pre-commit`, `.githooks/pre-push` and GitHub Rulesets enforce this automatically for all tools. |
| Path-scoped compile gate, without slow Testcontainers | `.githooks/pre-push` -> `.claude/hooks/test-gate.mjs` | `.githooks/pre-push` enforces this automatically on push for all tools and humans (`NIFTY_SKIP_GATE=1` skips the gate). |
| Role write boundaries (`readonly` / `tests-only`) | `.claude/hooks/guard-agent.mjs` | Obey the role boundaries when you act as cold reviewer or spec test-writer. |
| Archive completed OpenSpec changes (Decision 3.8) | `.claude/hooks/archive-gate.mjs --stop-hook` (`Stop` hook) | `.githooks/pre-push` -> `archive-gate.mjs --pre-push` enforces this on feature-branch pushes (`NIFTY_SKIP_GATE=1` skips the gate). The repo-invariant test in `.claude/hooks/hooks.test.mjs` enforces it in CI. |
