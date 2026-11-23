# Prompt & Execution Plan: Decision Traceability, Living Coding Rules & Parallel GitLab Support

> **Purpose:** Execute this prompt with our agentic harness (Claude Code, Antigravity `agy`, Codex, or Kiro Crew) **on the current branch `feat/new-harness-tools`** so these course-grading upgrades land in the same PR into `dev` (`PR #2`).
>
> **Core Rule:** Follow our spec-first workflow (`openspec/`). First create the OpenSpec change `openspec/changes/decisions-coding-rules-gitlab/` (`proposal.md`, `specs/decisions-coding-rules-gitlab/spec.md`, `design.md`, `tasks.md`, `.openspec.yaml`), implement incrementally in atomic Conventional Commits (never chaining `git add && git commit` or using `git commit -a`), verify every claim with real exit codes, run a cold read-only `reviewer` audit, push to `feat/new-harness-tools`, and update the PR description via `pr-memo`.

---

## Context & Confirmed Architectural Decisions

1. **Hierarchical Traceable Decisions (`docs/decisions/`):**
   - Organize decisions under `docs/decisions/` using category files (`1-architecture.md`, `2-design.md`, `3-team-process.md`, `4-direction.md`) plus `docs/decisions/index.md` using hierarchical IDs (`1.1`, `1.2`, `2.1`, ...) so code, specs, and PRs can cite exact pointers like `// Decision 1.2`.
   - Migrate existing `D1`–`D14` and `O1`–`O7` from `docs/decisions.md` cleanly into `docs/decisions/` (keeping `D1`–`D14` legacy IDs in a column of `index.md` for traceability) and convert `docs/decisions.md` into a short redirect stub so zero existing links break.
2. **Single Canonical Living Coding Rules File (Cross-Tool Guaranteed):**
   - Store all team coding standards in **one canonical file**: `.agents/rules/coding-standards.md` (`trigger: always_on`).
   - Guarantee every harness loads it with **zero duplication**:
     - **Antigravity (`agy`):** Auto-loads `.agents/rules/coding-standards.md` via `trigger: always_on`.
     - **Claude Code (`claude`):** Imports `@.agents/rules/coding-standards.md` in `CLAUDE.md` AND explicitly references it in `.claude/agents/spec-builder.md` and `.claude/agents/reviewer.md`.
     - **Codex / Gemini CLI / Kiro Crew / T3 Code:** Explicitly linked as mandatory reading in `AGENTS.md` (and `.kiro/steering/nifty-core.md` already points to `AGENTS.md` + `.agents/rules/`).
   - **Cold Reviewer Enforcement Policy:** Strictly **Blocking** on clear DRY/duplicate classes, hotspot Java Stream misuse, missing `docs/api.md` updates on endpoint changes, or un-grouped bespoke Vue views; **Convention Candidate** on subjective function-splitting or HOF taste.
3. **Parallel GitLab (`glab`) Support Without Breaking `.env` Secret Isolation:**
   - Keep GitHub (`gh`, `.github/workflows/ci.yml`, `.github/rulesets/`) intact and add parallel GitLab (`glab`, `.gitlab-ci.yml`) controlled by `GIT_FORGE=github|gitlab` (+ optional `GITLAB_HOST`) in `.env` / `.env.example` (default `github` in `.env.example`).
   - Because AI agents are strictly forbidden from reading `.env` (which holds `KICONNECT_API_KEY`), provide a zero-dependency Node script (`scripts/check-setup.mjs`) that reads **only** the non-secret `GIT_FORGE` and `GITLAB_HOST` keys from `.env` (never reading/echoing secrets) and runs full forge-aware project health checks.
4. **Living API Documentation (`docs/api.md` + `springdoc-openapi`):**
   - Maintain `docs/api.md` (endpoint table, auth roles, DTO shapes, error envelope, SSE events) kept in sync with live `springdoc-openapi` (`/v3/api-docs`, `/swagger-ui.html`) whenever a controller or contract changes.

---

## Implementation Plan (Execute via `openspec/changes/decisions-coding-rules-gitlab/`)

### Workstream 1 — Traceable Decision Recording (`docs/decisions/`, Script, Linked Skills & Code Pointers)

1. **Create `docs/decisions/` hierarchy and migrate existing decisions:**
   - `docs/decisions/index.md`:
     - Master decision registry table: `ID | Legacy ID | Category | Topic | Status | Date | Trigger | File Link`.
     - Section for **Open Team Decisions (`O1`–`O7`)** migrated from `docs/decisions.md`.
     - Short **Code Pointer Convention** box (`// Decision 1.2: <short why>` in Java/TS, `<!-- Decision 2.1 -->` in Vue/Markdown).
   - Category files (each decision uses a consistent structured template: `## <X.Y> — <Topic>`, `Date`, `Triggered By` (`question-round` | `prompter-change` | `team` | `baseline`), `Context`, `Decision`, `Alternatives Considered`, `Rationale / Justification`, `Affected Files / Pointers`):
     - `docs/decisions/1-architecture.md` (migrate architectural `D7`, `D8`, `D11`, `D12`, `D13`, `D14` as `1.1`–`1.6`, plus record `1.7` for parallel GitHub/GitLab forge switch via `.env`).
     - `docs/decisions/2-design.md` (migrate product/domain design `D1`, `D2`, `D3`, `D4`, `D5`, `D6`, `D9`, `D10` as `2.1`–`2.8`, plus `2.9` for living `docs/api.md` + OpenAPI).
     - `docs/decisions/3-team-process.md` (record `3.1` for OpenSpec single-plan + cold reviewer harness, `3.2` for centralized `.agents/rules/coding-standards.md`, `3.3` for mandatory decision recording & code pointers).
     - `docs/decisions/4-direction.md` (seeded template ready for lecturer feedback, MVP scope shifts, and prompter direction changes).
   - Convert `docs/decisions.md` into a concise 15-line pointer redirecting to `docs/decisions/index.md` so no existing link breaks.
2. **Create zero-dependency Node script `scripts/record-decision.mjs`:**
   - CLI interface (works identically on macOS, Windows, Linux):
     ```sh
     node scripts/record-decision.mjs \
       --category 1|2|3|4 \
       --topic "Short title" \
       --trigger "question-round|prompter-change|team" \
       --context "Why this came up" \
       --decision "What we decided" \
       --alternatives "What else was considered" \
       --rationale "Why this option won" \
       --pointers "path/a, path/b" \
       [--resolves O1] [--dry-run] [--root <dir>]
     ```
   - Automatically calculates the next `<category>.<next>` ID (e.g., `1.8`), appends the formatted Markdown entry to `docs/decisions/<category>-*.md`, inserts a new row in `docs/decisions/index.md`, optionally marks an `O1`–`O7` open decision as resolved with a link to `<X.Y>`, and prints the assigned ID (e.g., `Recorded Decision 1.8 in docs/decisions/1-architecture.md`).
   - Export pure functions so `scripts/harness-scripts.test.mjs` can test ID calculation, entry formatting, and index updates in an isolated `fs.mkdtempSync` temp directory without mutating real repo files.
3. **Upgrade `record-decision` & link `question-round` + prompter-change trigger:**
   - Update `.agents/skills/record-decision/SKILL.md`:
     - Trigger conditions: **(a)** immediately after any `question-round` answer, **(b)** **anytime the prompter changes, redirects, or overrides** a design, architecture, coding rule, team process, or product direction, and **(c)** whenever an `O1`–`O7` TBD is resolved.
     - Instruct agents to run `node scripts/record-decision.mjs ...` so IDs and `docs/decisions/index.md` never drift, and synchronize `openspec/config.yaml`, `AGENTS.md`, `CLAUDE.md`, and affected skills in the same commit/PR.
   - Update `.agents/skills/question-round/SKILL.md`:
     - Explicitly mandate calling `record-decision` (via `scripts/record-decision.mjs`) for **every** answered decision round — not just `O1`–`O7` — so all prompter answers become permanent, traceable `X.Y` decisions.
   - Update `AGENTS.md`, `.claude/agents/spec-builder.md`, and `.claude/agents/reviewer.md`:
     - **Builder rule:** Whenever implementing non-obvious logic or acting on a recorded decision, leave a concise inline pointer comment (`// Decision X.Y: <one-line why>`) in code/SQL/Vue and cite `Decision X.Y` in `openspec/changes/<change>/design.md` and `## Handoff Log`.
     - **Prompter-change rule (in `AGENTS.md` + `spec-builder.md`):** Whenever the user/prompter changes direction, rejects an approach, or introduces a constraint mid-conversation, invoke `record-decision` (`--trigger prompter-change`) before or alongside implementing the change.
     - **Cold Reviewer rule:** Check that architectural/design changes cite a valid `docs/decisions/` ID and flag undocumented direction shifts.

---

### Workstream 2 — Centralized Living Coding Rules (`.agents/rules/coding-standards.md`) & `docs/api.md`

1. **Create canonical `.agents/rules/coding-standards.md` (`trigger: always_on`):**
   - Header explaining this is the **living, centralized team coding standards file** — anytime the team or reviewer agrees on a new coding rule during the sprint, append it here (referencing a `Decision 3.x` entry if non-trivial).
   - Include these 6 core rules clearly with concrete pass/fail examples and enforcement levels:
     1. **ETC & DRY (Easy to Change, Don't Repeat Yourself):** Structure code so future changes touch one place. Extract shared validation, mapping, formatting, and domain helpers instead of duplicating logic across classes or components.
     2. **Small, Abstractable, Testable Functions:** Keep functions compact, single-responsibility, pure where possible, and directly unit-testable without heavy setup.
     3. **Hotspot Performance — Avoid Streams in Hot Paths:** Do **not** use Java Streams (`stream().map().filter()...`) inside repeatedly called, hot-path, or centralized shared utility functions (e.g., quote verification loops, scoring/confidence calculators, Timefold constraint helpers). Write direct, optimized, low-allocation loops for hotspots; reserve Streams strictly for infrequent or one-off in-place calculations.
     4. **Living API Documentation (`docs/api.md`):** Keep `docs/api.md` synchronized on every backend controller, DTO, auth role, error envelope, or SSE contract change alongside `springdoc-openapi` (`/v3/api-docs` & `/swagger-ui.html`).
     5. **Minimal Code Footprint & Simple Reusable Classes:** Maximize reuse of existing simple classes and utilities. **Never** create a second class, DTO, mapper, or interface that re-implements existing logic under a different name. Prefer minimal, readable code over speculative abstractions.
     6. **Frontend Component Reuse, HOFs & Feature-Grouped Views:**
        - Avoid granular bespoke one-off components; centralize and reuse shared UI primitives, layouts, and composables.
        - Use Higher-Order Functions (HOFs) (e.g., query/handler wrappers, table/chart formatters, validator factories) wherever they cleanly eliminate repeated boilerplate.
        - Organize Vue views logically into domain/feature folders (`frontend/src/views/<feature>/...`, e.g., `views/assessment/`, `views/ranking/`, `views/studio/`, `views/teams/`) for fast, predictable navigation.
2. **Create living `docs/api.md`:**
   - Document base URLs (`http://localhost:8080`, Nginx proxy `/api`), OpenAPI links (`/v3/api-docs`, `/swagger-ui.html`), Role Matrix (`PROFESSOR`/`REVIEWER`, `CANDIDATE`), standard `@RestControllerAdvice` `ProblemDetail` JSON error envelope, currently implemented endpoints (`GET /api/v1/health`, `GET /actuator/health`), and stubbed sections for the 7 MVP modules (with explicit reminder that every controller PR must update `docs/api.md`).
3. **Wire Cross-Tool Discovery & Unbiased Cold Reviewer Enforcement:**
   - `AGENTS.md`: Add `.agents/rules/coding-standards.md` and `docs/api.md` to Monorepo Map and add a short **"Living Coding Standards"** rule section pointing to `.agents/rules/coding-standards.md` (guaranteed visibility for Codex, Gemini CLI, Kiro Crew, T3 Code, Claude, Antigravity).
   - `CLAUDE.md`: Import `@.agents/rules/coding-standards.md` in the Source of Truth section so Claude Code auto-loads the exact canonical file without copying it into `.claude/`.
   - `.agents/rules/backend-java.md` & `.agents/rules/frontend-vue.md`: Add a 1-line cross-reference to `.agents/rules/coding-standards.md` and `docs/api.md` (zero rule duplication).
   - `.claude/agents/spec-builder.md`: Require following `.agents/rules/coding-standards.md`, keeping `docs/api.md` updated on endpoint edits, and leaving `// Decision X.Y` pointers.
   - `.claude/agents/reviewer.md`: Explicitly reinforce **sandboxed, zero-outside-context cold review** (receives *only* `git diff dev...HEAD`, `AGENTS.md`, `.agents/rules/coding-standards.md`, `docs/decisions/index.md`, and relevant `openspec/` specs) and specify enforcement:
     - **Blocking:** Clear DRY/duplicate classes re-implementing existing logic, Java Streams in hot-path/centralized repeated functions, controller/API changes missing `docs/api.md` updates, un-grouped bespoke Vue views violating feature folders, or undocumented architectural/direction shifts.
     - **Convention Candidates (non-blocking):** Subjective function-splitting granularity or optional HOF refactors.

---

### Workstream 3 — Parallel GitLab (`glab`) Support via `.env` Switch & Preflight Check

1. **Add forge switch to `.env.example`:**
   - Add a clearly documented section:
     ```dotenv
     # --- Git Forge Switch (parallel GitHub / GitLab support for AI harness & scripts) ---
     # Values: "github" (uses `gh`) or "gitlab" (uses `glab`)
     GIT_FORGE=github
     # GitLab host used when GIT_FORGE=gitlab (e.g., gitlab.com or university self-hosted GitLab)
     GITLAB_HOST=gitlab.com
     ```
2. **Create zero-dependency `scripts/check-setup.mjs` (safe forge reader + full setup preflight):**
   - **Security invariant:** Never print, log, or return secret values (`KICONNECT_API_KEY`, `POSTGRES_PASSWORD`, tokens). When reading `.env`, parse **only** allowlisted non-secret keys (`GIT_FORGE`, `GITLAB_HOST`, `AI_EVALUATOR_PROVIDER`, `AI_EVALUATOR_MODEL`) and check boolean presence (`configured` vs `placeholder`) for `KICONNECT_API_KEY`.
   - Modes:
     - `node scripts/check-setup.mjs --forge`: Prints compact non-secret output:
       ```text
       GIT_FORGE=github
       GITLAB_HOST=gitlab.com
       CLI=gh
       ```
       (or `GIT_FORGE=gitlab`, `CLI=glab` when `.env` sets `GIT_FORGE=gitlab`). Agents call this command instead of ever touching `.env`!
     - `node scripts/check-setup.mjs` (full preflight check): Verifies and prints clear PASS/WARN/FAIL status for:
       1. Active forge (`github` → `gh --version` + auth check; `gitlab` → `glab --version` + auth check for `GITLAB_HOST`)
       2. Git hooks active (`git config --get core.hooksPath` === `.githooks`) and executable (`pre-commit`, `commit-msg`, `pre-push`)
       3. `.env` exists (without exposing secrets) and reports `KICONNECT_API_KEY` status (`configured` or `placeholder mode`)
       4. `.claude/settings.json` valid JSON, `.claude/hooks/*.mjs` present, `.agents/rules/coding-standards.md` present, `docs/decisions/index.md` present
       5. Local runtimes: Node 22+, Java 21, Docker (warn if offline/optional), OpenSpec CLI (optional)
     - Supports `--root <dir>` and injectable checks/env for pure unit testing in `scripts/harness-scripts.test.mjs`.
3. **Create parallel `.gitlab-ci.yml` at repository root:**
   - Mirror `.github/workflows/ci.yml` cleanly for GitLab CI/CD pipelines on merge requests and pushes to `dev`, `alpha`, `master`/`main`:
     - `backend` (`eclipse-temurin:21-jdk`, `./mvnw -B test`)
     - `frontend` (`node:22-alpine`, `npm ci`, `npm run typecheck`, `npm run lint`, `npm run build`)
     - `harness-tests` (`node:22-alpine`, `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`)
     - `conventional-commits` (validates MR/push commits against the exact Conventional Commits regex)
     - `branch-policy` (enforces `dev -> alpha -> master/main` MR promotion rules and `docs:/hotfix:`-only direct pushes to `dev`)
4. **Update permissions, skills, CI & docs for forge parity:**
   - `.claude/settings.json`: Add `Bash(node scripts/check-setup.mjs:*)`, `Bash(node scripts/record-decision.mjs:*)`, `Bash(glab status:*)`, `Bash(glab mr view:*)`, `Bash(glab mr list:*)`, `Bash(gh pr view:*)`, `Bash(gh pr list:*)` to `permissions.allow`.
   - `.claude/hooks/hooks.test.mjs` & new `scripts/harness-scripts.test.mjs`: Add comprehensive `node:test` unit tests for `scripts/record-decision.mjs` (hierarchical ID increment, category routing, `index.md` update, `O1`–`O7` resolution) and `scripts/check-setup.mjs` (`github` vs `gitlab` switch parsing, strict non-leak of `KICONNECT_API_KEY` and `POSTGRES_PASSWORD`).
   - `.github/workflows/ci.yml`: Update `hook-tests` step to run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`.
   - `.agents/skills/pr-memo/SKILL.md`: Make forge-aware — first run `node scripts/check-setup.mjs --forge`; generate a PR summary for GitHub (`gh pr create --base dev ...`) or MR summary for GitLab (`glab mr create --target-branch dev ...`) accordingly.
   - `CLAUDE.md`: Update First-Run Bootstrap to run `node scripts/check-setup.mjs` (so GitLab vs GitHub CLI + `.env` forge switch + hooks + coding standards + decisions folder are verified automatically).
   - `docs/workflow.md`, `docs/setup.md`, and `README.md`: Document:
     - `docs/decisions/` + `scripts/record-decision.mjs` + `// Decision X.Y` pointers
     - `.agents/rules/coding-standards.md` + `docs/api.md`
     - Parallel GitHub (`gh`) / GitLab (`glab`) `.env` switch (`GIT_FORGE`, `GITLAB_HOST`), `.gitlab-ci.yml`, and `node scripts/check-setup.mjs`
     - Keep `docs/setup.md` strictly stack-agnostic (treat `docs/api.md` and coding standards as pluggable harness slots in `## For Agents`, and keep `## For Teammates` concise and ~2-min scannable).

---

## Explicitly Out of Scope

- Do **not** remove GitHub workflows, rulesets, or `gh` support — GitHub and GitLab run cleanly in parallel.
- Do **not** weaken `.env` blocking in `guard-files.mjs`, `guard-bash.mjs`, or `.claude/settings.json` — agents MUST STILL NEVER read `.env` directly; always go through `node scripts/check-setup.mjs --forge`.
- Do **not** duplicate `.agents/rules/coding-standards.md` into `.claude/rules/` or `.kiro/steering/`.
- Do **not** modify Java/Vue production application code (this PR is harness, docs, CI, and scripts only).

---

## Acceptance Criteria & Verification (Before Pushing to `feat/new-harness-tools` & Updating PR #2)

- [ ] `OPENSPEC_TELEMETRY=0 openspec validate --all` exits `0` (0 warnings, including `change/decisions-coding-rules-gitlab`).
- [ ] `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` exits `0` (including tests proving `check-setup.mjs` NEVER echoes `.env` secrets and `record-decision.mjs` assigns sequential `X.Y` IDs cleanly).
- [ ] `node scripts/check-setup.mjs --forge` outputs ONLY `GIT_FORGE`, `GITLAB_HOST`, and `CLI` (tested with both `github` and `gitlab`).
- [ ] `docs/decisions/index.md` + `1-architecture.md`, `2-design.md`, `3-team-process.md`, `4-direction.md` contain all migrated `D1`–`D14` and `O1`–`O7` items with hierarchical IDs and `docs/decisions.md` redirects cleanly.
- [ ] `.agents/rules/coding-standards.md` contains all 6 coding rules and is explicitly discoverable from `AGENTS.md`, `CLAUDE.md` (`@` import), `.claude/agents/spec-builder.md`, and `.claude/agents/reviewer.md` without duplication.
- [ ] `docs/api.md` exists and documents current endpoints, roles, and error envelope.
- [ ] `.gitlab-ci.yml` parses cleanly as valid YAML and mirrors all 5 CI jobs.
- [ ] `.claude/settings.json` parses cleanly as valid JSON.
- [ ] `cd backend && ./mvnw test` and `cd frontend && npm run typecheck && npm run lint && npm run build` exit `0`.
- [ ] Cold, read-only `reviewer` subagent pass on `git diff dev...HEAD` returns `Verdict: APPROVE` with zero rule drift across all canonical and pointer files.
- [ ] Push atomic commits to `feat/new-harness-tools` and update PR #2 description via `pr-memo`.
