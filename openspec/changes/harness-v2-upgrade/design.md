# Design: Harness v2 Upgrade

## 1. Architecture Principles

- **Single Canonical Source:** `AGENTS.md` + `.agents/rules/*.md` hold engineering rules; `openspec/` holds specs and execution plans (`tasks.md` + `## Handoff Log`); every other tool file (`CLAUDE.md`, `GEMINI.md`, `.kiro/steering/nifty-core.md`) is a thin pointer or tool-specific hook/agent adapter.
- **Zero-Dependency Portability:** All automated guard scripts use built-in Node.js (`>=22`, already required for `frontend/`) ESM (`.mjs`) with only standard library imports (`node:fs`, `node:path`, `node:child_process`, `node:test`, `node:assert/strict`) so macOS, Linux, and Windows behave identically without `bash`/`grep` dialect drift.
- **Fast Local Loops, Strict Safety:** Pre-commit runs only instant (<200 ms) secret and branch checks; pre-push runs branch policy plus path-scoped compile/typecheck/build (`test-gate.mjs`), never slow Testcontainers.

## 2. Node `.mjs` Hook Topology (`.claude/hooks/`)

```text
.claude/hooks/
├── lib.mjs           # Pure helpers: stdin JSON parse, path normalization, shell segment splitter, secret patterns, env-file matcher
├── guard-files.mjs   # PreToolUse (Read|Edit|Write|MultiEdit|Grep|Glob|NotebookEdit): block .env* except .env.example
├── guard-bash.mjs    # PreToolUse (Bash) + CLI `--pre-commit`: block protected branches, .env shell access, `git add && git commit` chaining, and staged secret leaks
├── guard-agent.mjs   # CLI `--mode readonly|tests-only` (+ exported evaluator): enforces cold reviewer & test-writer file boundaries
├── test-gate.mjs     # Fast path-scoped build gate (frontend typecheck+build, backend test-compile; NIFTY_SKIP_GATE=1 escape hatch)
└── hooks.test.mjs    # `node --test` unit test suite (0 deps, runs in CI)
```

### Key Design Details for Hook Accuracy
- **Shell segment splitting (`splitShellSegments`):** Splits on unquoted `&&`, `||`, `;`, `|` (and newlines) so safe commands like `git push origin feat/x && git checkout master` are not falsely flagged while `git add .env && git commit -m "x"` and `git push -f origin master` are reliably caught.
- **Env file detection (`isBlockedEnvPath`):** Normalizes Windows backslashes `\` to `/`, inspects basename and glob patterns, blocks `.env`, `.env.local`, `.env.production`, `**/.env`, `.env*` globs, while explicitly allowing `.env.example` (and `*.env.example`).
- **Secret detection (`findSecretsInText`):** Inspects only added lines (`+` prefix, excluding `+++` diff headers) in `git diff --cached -U0` for:
  - OpenAI / generic `sk-[A-Za-z0-9_-]{20,}` and Anthropic `sk-ant-[A-Za-z0-9_-]{16,}`
  - Google API keys `AIza[0-9A-Za-z_-]{30,}`
  - GitHub tokens `ghp_[A-Za-z0-9]{20,}`, `github_pat_[A-Za-z0-9_]{20,}`
  - PEM private keys `-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----`
  - Non-placeholder `KICONNECT_API_KEY=<real-value>` (ignoring empty, `not-configured`, `changeme`, `your-*`, `<...>`, `${...}`)
  - Non-placeholder JDBC / Postgres passwords in hardcoded URLs or `SPRING_DATASOURCE_PASSWORD` outside known local dev defaults (`nifty`, `postgres`, `changeme`, `${...}`).
- **`.claude/settings.json` Permissions & Hooks:**
  - `permissions.allow`: routine read/build/inspect commands (`./mvnw:*`, `mvnw.cmd:*`, `npm run:*`, `node --test:*`, `git status:*`, `git diff:*`, `git log:*`, `git branch:*`, `git show:*`, `git rev-parse:*`, `openspec:*`).
  - `permissions.deny`: `Read(./.env)`, `Read(./**/.env)`, `Read(./**/.env.local)`, `Bash(git push --force:*)`, `Bash(git push -f:*)`.
  - `PreToolUse`: `Read|Edit|Write|MultiEdit|Grep|Glob|NotebookEdit` → `node .claude/hooks/guard-files.mjs`; `Bash` → `node .claude/hooks/guard-bash.mjs`.
- **`.githooks/` Bridge:**
  - `.githooks/pre-commit`: Runs `node .claude/hooks/guard-bash.mjs --pre-commit` (graceful error if `node` missing).
  - `.githooks/pre-push`: Retains branch protection logic (master/main/alpha block + dev docs/hotfix-only check) and invokes `node .claude/hooks/test-gate.mjs` before allowing push (supports `NIFTY_SKIP_GATE=1`).

## 3. Agents, Shared Skills & Handoff Log

- **`.claude/agents/`:**
  - `spec-builder.md` — Scoped task implementer; escalates `O1`–`O7` instead of guessing; appends 5-line `## Handoff Log` entry in `tasks.md`.
  - `reviewer.md` — Cold read-only auditor (enforced via `guard-agent.mjs --mode readonly` in frontmatter hooks + instructions); inputs strictly `git diff dev...HEAD` + `openspec/` specs; outputs ≤30-line `Verdict / Blocking / Convention Candidates`.
  - `test-writer.md` — Spec-first test author (enforced via `guard-agent.mjs --mode tests-only`); writes JUnit 5 / Mockito / Testcontainers / Vitest tests strictly from EARS criteria with invariant-named tests.
- **Shared Skills (`.agents/skills/` canonical + relative symlinks in `.claude/skills/`):**
  - `record-decision/SKILL.md`
  - `question-round/SKILL.md`
  - `pr-memo/SKILL.md`
- **Handoff Log Standard:** Added to `openspec/config.yaml` (`rules.tasks` & `operations.apply.guidance`) and `.claude/skills/spec-implementer/SKILL.md`.

## 4. Guardrails, Kiro Crew & `docs/setup.md`

- **Rules & Docs:** Update `.agents/rules/backend-java.md`, `.agents/rules/frontend-vue.md`, `docs/architecture.md` (Cross-Layer Enum & State Lock-In Table), `docs/workflow.md` (Day-to-Day Ticket Loop, Module Ownership Table, Blocking vs. Convention Candidate, Link Never Copy, Kiro Crew workflow), `AGENTS.md` (Adopting Agent Roles + Cross-Tool Rule & Hook Parity), `CLAUDE.md` (updated hook/agent/skill bootstrap check), `README.md` (Kiro Crew row + link to `docs/setup.md`).
- **Kiro Crew Bridge:** `.kiro/steering/nifty-core.md` (`inclusion: always`, ≤25 lines) pointing to `AGENTS.md`, `openspec/`, and `.githooks/`.
- **Standalone Harness Guide:** `docs/setup.md` structured cleanly into `## For Teammates` (~2-min human overview & daily loop) and `## For Agents` (canonical-vs-pointer inventory table, modification rules, and stack-agnostic replication checklist).
