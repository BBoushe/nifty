# Prompt & Execution Plan: Upgrade `nifty` SDD/ADD Harness (Curated from AWE + Kiro Crew)

> **Purpose:** Drop this prompt into Claude Code, Antigravity (`agy`), Codex, or Kiro Crew Conductor to upgrade our Spec-Driven & Agent-Driven Development (SDD/ADD) harness with the highest-ROI patterns cherry-picked from `/Users/alek/Developer/Uni/advanced-web-engineering/project` plus **Kiro Crew** multi-agent orchestration.
>
> **Core Rule:** Follow our spec-first workflow (`openspec/`). Before editing any harness file, create the OpenSpec change `openspec/changes/harness-v2-upgrade/` (`proposal.md`, `specs/`, `design.md`, `tasks.md`), implement incrementally with atomic Conventional Commits on a `chore/harness-v2` branch off `dev`, verify every claim with real exit codes, and open a PR into `dev`.

---

## Context & Architectural Decisions Already Made

1. **No Separate `plans/` Folder:** Do **NOT** create `.claude/plans/` or a root `plans/` folder. Our `openspec/changes/<change-slug>/` (`proposal.md`, `specs/`, `design.md`, `tasks.md` → `openspec/changes/archive/`) **already is** our Kiro-equivalent spec & execution plan directory. Adding a second `plans/` tree causes instant drift in a 1-week sprint. Instead, add a lightweight `## Handoff Log` section at the bottom of `tasks.md` for cross-session and multi-agent continuity.
2. **Single Source of Truth for Rules:** Keep `AGENTS.md` and `.agents/rules/*.md` canonical. Do **NOT** duplicate full rule files into `.claude/rules/` or `.kiro/steering/`. Use a compact cross-tool Rule & Hook Parity table in `AGENTS.md` and a thin pointer file in `.kiro/steering/nifty-core.md` (`inclusion: always`) pointing to `AGENTS.md` and `openspec/`.
3. **1-Week Block-Week Velocity First:** Zero semester-long lab ceremony (no `lab-check`, no per-area agents, no 12-subfolder `docs/` tree, no individual `NNNN-*.md` ADR files, no "write core by hand" / `AI-assisted:` comment gates, no slow Testcontainers in `pre-commit`).

---

## Implementation Plan (Execute via `openspec/changes/harness-v2-upgrade/`)

### Workstream 1 — Tested, Zero-Dependency Cross-Platform Node `.mjs` Hooks + Permissions
*Replaces fragile POSIX `sh` + regex hooks so Windows, macOS, and Linux behave identically and closes `.env` read/grep/diff secret leaks (critical now that `.env` holds `KICONNECT_API_KEY`).*

1. **Replace `.claude/hooks/*.sh` with zero-dependency Node `.mjs` hooks** (requires Node 22+, already mandatory for `frontend/`):
   - `.claude/hooks/lib.mjs`: Shared helpers (JSON stdin parsing, repo-root resolution, cross-platform path normalization, shell command segment splitting on `&&`, `||`, `;`, `|`, and `nifty`-specific secret regexes: `sk-*`, `sk-ant-*`, `AIza*`, `ghp_*`, `github_pat_*`, `-----BEGIN .* PRIVATE KEY-----`, non-placeholder `KICONNECT_API_KEY` / JDBC passwords).
   - `.claude/hooks/guard-files.mjs`: Blocks `Read`, `Edit`, `Write`, `MultiEdit`, `Grep`, `Glob`, `NotebookEdit` targeting `.env` or `.env.*` (explicitly allowing `.env.example`).
   - `.claude/hooks/guard-bash.mjs`:
     - Blocks direct commits or pushes to `master`, `main`, and `alpha` (including `--force`, `-f`, `--all`, bare push).
     - Blocks direct pushes to `dev` unless every commit matches `^(docs|hotfix)(\([a-z0-9._/-]+\))?!?: `.
     - Blocks reading/printing/staging `.env` via shell (`cat .env`, `git add .env`, etc.) and blocks `git add ... && git commit` chaining so staged diffs are always scanned.
     - Scans `git diff --cached -U0` for leaked secrets before any `git commit`.
   - `.claude/hooks/guard-agent.mjs`: Supports `readonly` mode (for cold `reviewer`) and `tests-only` mode (for independent `test-writer`, restricting edits strictly to `backend/src/test/**` and `frontend/src/**/*.test.*` / `**/*.spec.*`).
   - `.claude/hooks/test-gate.mjs`: Path-scoped **fast** build gate inspecting touched files:
     - `frontend/` changed → `npm run typecheck && npm run build` (`CI=true`).
     - `backend/` changed → `./mvnw -q test-compile` (Windows: `mvnw.cmd -q test-compile`), **never** running slow PostgreSQL Testcontainers on commit. Support `NIFTY_SKIP_GATE=1` escape hatch.
2. **Add built-in unit tests for all hooks:**
   - Create `.claude/hooks/hooks.test.mjs` using `node:test` + `node:assert/strict` (0 extra dependencies, runs in <100 ms) covering `.env` read/write/grep blocks, `.env.example` allow, chained bash commands, secret-in-diff detection, protected branch guards, and `guard-agent.mjs` modes.
   - Add a `Hook Tests` step (`node --test .claude/hooks/hooks.test.mjs`) to `.github/workflows/ci.yml`.
3. **Upgrade `.claude/settings.json`:**
   - Add `permissions.allow` for routine read/build commands (`./mvnw:*`, `mvnw.cmd:*`, `npm run:*`, `git status/diff/log/branch/show/rev-parse:*`, `openspec:*`) to eliminate prompt fatigue.
   - Add `permissions.deny` for `Read(./.env)`, `Read(./**/.env)`, `Read(./**/.env.local)`, `Bash(git push --force:*)`, `Bash(git push -f:*)`.
   - Wire `PreToolUse` on `Read|Edit|Write|MultiEdit|Grep|Glob|NotebookEdit` (`guard-files.mjs`) and `Bash` (`guard-bash.mjs`).
4. **Bridge `.githooks/` for ALL tools (Antigravity, Codex, T3 Code, Kiro Crew, humans):**
   - Add `.githooks/pre-commit` delegating to `node .claude/hooks/guard-bash.mjs --pre-commit` (instant secret + branch guard — zero slow tests).
   - Optionally call `node .claude/hooks/test-gate.mjs` from `.githooks/pre-push` so broken builds are caught before pushing without slowing down local commits.

---

### Workstream 2 — Cold Verifier, Independent Test-Writer & Lean SDD Skills
*Enforces real separation between building and verifying so LLM math, silent `0`/`1` instead of `N/A`, unverified quotes, or missing audit logs never slip past self-review.*

1. **Create 3 focused role definitions in `.claude/agents/`** (and add a 3-row "Adopting Agent Roles" section in `AGENTS.md` so Antigravity `agy`, Codex, and Kiro Crew follow the exact same split):
   - `.claude/agents/spec-builder.md`: Implements unchecked tasks from `openspec/changes/<change>/tasks.md`, runs real builds/tests, escalates unresolved `O1`–`O7` TBDs instead of guessing, appends a 5-line entry to `## Handoff Log` in `tasks.md`.
   - `.claude/agents/reviewer.md`: **Cold, read-only auditor.** Receives *only* `git diff dev...HEAD` + relevant `openspec/` EARS specs (never builder chat history). Enforced read-only via `guard-agent.mjs`. Outputs a concise `Verdict / Blocking / Convention Candidates` report (≤30 lines). Only blocks on `AGENTS.md` domain invariants, `openspec/` EARS criteria, or failing builds/tests; style preferences go under non-blocking `Convention Candidates`.
   - `.claude/agents/test-writer.md`: Writes JUnit 5 / Mockito / Testcontainers / Vitest tests **strictly from `openspec/` EARS requirements** (never mirroring implementation bugs), with **one test explicitly named after each domain invariant**. Enforced to test paths only via `guard-agent.mjs`.
2. **Add 3 lightweight shared skills in `.agents/skills/` (symlinked into `.claude/skills/`):**
   - `record-decision/SKILL.md`: Moves a resolved `O1`–`O7` open question into `D15+` in `docs/decisions.md` and updates `openspec/config.yaml`, `CLAUDE.md`, and affected skills **in the same PR**.
   - `question-round/SKILL.md`: Grouped numbered questions with bold recommended defaults (`reply "default" or override`), **scoped strictly** to resolving `O1`–`O7` TBDs or genuine spec ambiguities (never a blanket gate on routine tasks).
   - `pr-memo/SKILL.md`: Generates a ≤10-line plain-language PR summary (`What changed / Why / How to test / Invariants to check / Suggested reviewer`) targeting `dev`.
3. **Add `## Handoff Log` standard to `openspec/config.yaml` & `spec-implementer/SKILL.md`:**
   - Every completed milestone appends a compact block to `openspec/changes/<change>/tasks.md`: `Commits`, `Verification` (command + exit code), `Contracts/Migrations added`, `Decisions`, `Open Questions`.

---

### Workstream 3 — Architectural Guardrails & Cross-Layer Contract Lock-In
*Eliminates cross-layer drift between Java backend, Vue frontend, and LLM prompts.*

1. **Extend `.agents/rules/backend-java.md`:**
   - **Spring AI Provider Portability:** Use Spring AI 2.0 abstractions (`ChatClient` / `ChatModel`) cleanly so switching or adding providers later (from `ki:connect` NRW / FH AI today to Gemini, Claude, or OpenAI APIs later) stays config-driven — keep tests deterministic by mocking/replaying `ChatClient` responses rather than adding an artificial offline demo stub layer.
   - **Strict Controller DTO & Error Boundary:** Controllers validate input, call one service, and return DTOs only (never JPA entities or repositories). Use a single `@RestControllerAdvice` ProblemDetail/JSON error envelope across all endpoints.
2. **Extend `.agents/rules/frontend-vue.md`:**
   - Every TanStack Query view MUST explicitly handle `loading`, `empty`, `success`, and `error` states.
   - Never recompute or duplicate fit, coverage, confidence, or ranking math in TypeScript/Vue — render backend-computed values only.
3. **Add Cross-Layer Enum & State Lock-In Table to `docs/architecture.md`:**
   - One compact 4-column table (`Entity / Field | Exact Allowed Literals | Transitions / Computed-vs-Stored | Owner`) locking: `Role` (`PROFESSOR`, `REVIEWER`, `CANDIDATE`), `question.origin` (`core`, `follow_up`, `invented`), `inferredTag.status` (`pending`, `approved`, `rejected`), `score` wire representation for `N/A` (`null` / explicit `"N/A"` discriminator — never `0`), and `reviewStatus` (`pending`, `flagged`, `approved`, `overridden`).
4. **Tighten `docs/workflow.md` & `AGENTS.md`:**
   - Add a 15-line **"Day-to-Day Ticket Loop"** (Jira ticket → `feat/<KEY>-slug` branch from `dev` → `openspec/changes/<change>/tasks.md` → `spec-builder` → fresh-session `reviewer` → PR into `dev`) + a 5-row **Module Ownership Table** (for PR reviewer routing) + the **"Blocking vs. Convention Candidate"** review rule + **"Link, Never Copy"** rule for Confluence ↔ GitHub.
   - Add a 6-row **"Cross-Tool Rule & Hook Parity"** table to `AGENTS.md` so Codex, Antigravity, and Kiro CLI know the exact checks that `.claude/hooks/` automate in Claude Code.

---

### Workstream 4 — Kiro Crew Integration (Utilizing Existing Subscriptions)
*Uses **Kiro Crew** (AWS open-source multi-agent orchestrator, Apache 2.0) as an optional background orchestration layer without locking out teammates who use direct CLIs or T3 Code.*

1. **Add thin Kiro steering bridge (`.kiro/steering/nifty-core.md`):**
   - YAML frontmatter: `inclusion: always`
   - Body (≤25 lines): Instructs Kiro IDE / Kiro Crew to treat `AGENTS.md` as universal rules, `openspec/specs/` + `openspec/changes/` as the authoritative spec/task source (never duplicating specs into `.kiro/specs/`), and `.githooks/` as active commit/push guards.
2. **Configure Kiro Crew Backends to Reuse Your Subscriptions:**
   - Kiro Crew supports multiple underlying CLI harnesses (Kiro CLI, **Claude Code**, **OpenAI Codex**, OpenCode, custom harnesses). Connect your existing **Claude Code** and/or **Codex** CLI logins as Kiro Crew worker backends (and/or BYOK proxy if routing Gemini/ki:connect) so you don't pay twice or maintain separate prompt packs.
3. **Recommended Kiro Crew Multi-Agent Workflow for Block Week:**
   - **Conductor (interactive, driven by you):** Picks an approved `openspec/changes/<module>/` change, resolves any `O1`–`O7` open decisions via `question-round`, and dispatches independent workers onto separate git branches/worktrees (`feat/evidence-pipeline`, `feat/team-builder`, etc.).
   - **Parallel Builder Workers (background Claude Code / Codex sessions):** Each worker owns one `openspec/changes/<module>/` slice, runs local tests, and writes a 5-line entry into `tasks.md`'s `## Handoff Log`.
   - **Cold Reviewer Worker (separate background session):** Triggered on each completed branch with *only* `git diff dev...HEAD` + `openspec/` specs using the `reviewer.md` prompt — posts blocking invariant findings before you open the GitHub PR.
   - **High-Value Background Check (optional):** On-demand or scheduled Kiro Crew hygiene task running `OPENSPEC_TELEMETRY=0 openspec validate --all`, `node --test .claude/hooks/hooks.test.mjs`, and scanning `backend/src/` for domain invariant regressions (e.g., LLM output used in numeric totals or missing `N/A` handling).
4. **Document Kiro Crew setup in `README.md` (1 row in the "Working with AI Agents" table + short note in `docs/workflow.md`).**

---

### Workstream 5 — Create `docs/setup.md` (AI / SDD / ADD Harness Guide & Blueprint)
*Create a standalone, **tech-stack-agnostic** reference document at `docs/setup.md` explaining ONLY our SDD (Spec-Driven Development) and ADD (Agent-Driven Development) harness — what it is, why we built it this way, and where every piece lives.*

Structure `docs/setup.md` into exactly **two parts**:

1. **Part 1 — `## For Teammates` (Concise, Human-Friendly):**
   - Respect human attention span: short paragraphs, scannable bullets, zero jargon walls (readable in ~2 minutes).
   - Explain **what** the AI setup is (plain files in Git so Claude Code, Antigravity `agy`, Codex, Kiro Crew, T3 Code, and IDEs all share one brain).
   - Explain **why** we made each major choice (why OpenSpec instead of vibe-coding, why a single `openspec/changes/` instead of a second `plans/` folder, why cold separate-session review, why portable `.githooks/` + Node hooks).
   - Give the **minimal daily loop** a teammate actually needs to remember (clone → enable `.githooks` → pick a spec task → build → cold review → PR).
2. **Part 2 — `## For Agents` (Canonical Source of Truth & Replication Blueprint):**
   - Written for any AI agent tasked with **understanding, modifying, or replicating** this SDD/ADD setup in another repository (keep tech-stack details out; treat stack rules as pluggable slots).
   - Include a complete **Inventory & Ownership Table** mapping every harness path (`openspec/`, `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.agents/rules/`, `.agents/skills/`, `.claude/agents/`, `.claude/hooks/`, `.claude/skills/`, `.claude/settings.json`, `.kiro/steering/`, `.githooks/`, `.github/rulesets/`, `.github/workflows/ci.yml`) to: its purpose, which tools read it, and whether it is **canonical** or a **thin pointer** (to prevent rule duplication).
   - Include **Modification Rules** (where to add a new invariant, a new skill, a new hook, or a new tool bridge without causing drift) and a **Replication Checklist** for bootstrapping this exact SDD/ADD harness into a brand-new project.

---

## Explicitly Rejected from AWE (Do NOT Implement)

- `CloudProvider`/`FakeCloudProvider`-style offline demo stub gateway (`StubLlmEvaluatorGateway`) — AI connectivity is a core feature of `nifty`, and Spring AI 2.0 already provides the abstraction we need to swap between `ki:connect` NRW (FH AI) today and Gemini, Claude, or OpenAI APIs later. Automated tests mock/replay `ChatClient` directly.
- `.claude/plans/` + `.claude/plans/finished/` + `master-plan` / `run-plan` ceremony (redundant with `openspec/changes/` + `openspec/changes/archive/`).
- Duplicate `.claude/rules/*.md` files (keep `.agents/rules/*.md` + `AGENTS.md` canonical).
- Per-area agents (`*-dev.md`), `area-*` knowledge packs, `lab-auditor`, `lab-check`, `docs-keeper`, `start-ticket`, `commit`, and `explain` skills.
- "Write core by hand" rule, `AI-assisted:` source comments, and mandatory beginner walkthroughs on every PR.
- Blanket 10–25 question `question-round` gate on every new task.
- 12-subfolder `docs/*/` tree, `PROJECT_CONTEXT.md` duplication, separate `docs/decisions/NNNN-*.md` ADR files, and Track A/B AWS hosting splits.
- Slow PostgreSQL Testcontainers inside `pre-commit`, H2 `@DataJpaTest` assumptions, Thymeleaf/XML/GraphQL/Sass/BEM rules, and "no Spring Security" rules.

---

## Acceptance Criteria & Verification (Before Merging `chore/harness-v2` → `dev`)

- [ ] `OPENSPEC_TELEMETRY=0 openspec validate --all` exits `0` (including `change/harness-v2-upgrade`).
- [ ] `node --test .claude/hooks/hooks.test.mjs` exits `0` (testing `.env` read/grep/edit/stage blocking, `.env.example` allow, secret diff scanning, protected branches, and `guard-agent.mjs` modes).
- [ ] `.claude/settings.json` parses cleanly as valid JSON.
- [ ] `.githooks/pre-commit`, `.githooks/commit-msg`, and `.githooks/pre-push` are executable and verified on sample inputs.
- [ ] `docs/setup.md` exists with both **For Teammates** (concise, human-scannable) and **For Agents** (canonical inventory + modification & replication blueprint), strictly focused on SDD/ADD harness design without coupling to app tech stack.
- [ ] `cd backend && ./mvnw test` and `cd frontend && npm run typecheck && npm run lint && npm run build` exit `0`.
- [ ] Cold `reviewer` pass confirms zero rule drift between `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.agents/rules/`, `.kiro/steering/nifty-core.md`, and `docs/setup.md`.
