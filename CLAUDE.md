# CLAUDE.md — Claude Code Entry Point

Follow [`AGENTS.md`](AGENTS.md) for all project invariants, mandatory tech stack versions, Git branching rules, and Conventional Commits. Never duplicate or contradict `AGENTS.md` or `openspec/`.

## Source of Truth Hierarchy

1. [`AGENTS.md`](AGENTS.md) — Universal non-negotiable rules & stack constraints.
2. [`openspec/config.yaml`](openspec/config.yaml) & `openspec/specs/` — Domain requirements in EARS notation.
3. `openspec/changes/<active-change>/` — Active `proposal.md`, `requirements.md`, `design.md`, and `tasks.md`.
4. [`docs/discovery-canvas.md`](docs/discovery-canvas.md) & [`docs/decisions.md`](docs/decisions.md) — Product vision & open decisions.

## Working Agreement for Claude Code

- Implement one logical task at a time from `openspec/changes/<change>/tasks.md`.
- Run real verification commands (`cd backend && ./mvnw test`, `cd frontend && npm run typecheck && npm run lint && npm run build`) before claiming completion.
- Keep diffs small, atomic, and strictly scoped to the active spec.

---

## First-Run Bootstrap & Self-Configuration

> **Instruction for Claude Code when a developer opens this repository for the first time:**
> Run this lightweight self-check once before starting feature work (do not modify tracked files without telling the developer what changed):

1. **Inspect Harness Configuration:**
   - Verify `.claude/skills/` contains the OpenSpec skills, shared skills (`record-decision`, `question-round`, `pr-memo`), and project skills (`spec-implementer`, `backend-spring-check`, `frontend-vue-check`, `evidence-pipeline-guard`).
   - Verify `.claude/agents/` contains `spec-builder.md`, `reviewer.md`, and `test-writer.md`.
   - Inspect `.claude/settings.json` to confirm valid JSON, `permissions.allow`/`deny`, and active `PreToolUse` `.mjs` hooks (`guard-files.mjs`, `guard-bash.mjs`).
2. **Check Team `[TBD]` Decisions (`docs/decisions.md`):**
   - Check whether the team has resolved open decisions **O1** (UI component library: PrimeVue vs. shadcn-vue) and **O2** (Interviewer LLM provider; evaluator is already resolved in **D14** — ki:connect NRW).
   - If resolved in `docs/decisions.md` or `package.json`/`pom.xml`, run the `record-decision` skill to update any `[TBD]` placeholders in `.claude/skills/frontend-vue-check/SKILL.md`, `.claude/skills/backend-spring-check/SKILL.md`, and `openspec/config.yaml` (and note developer-specific preferences in untracked `.claude/settings.local.json`).
3. **Verify Local Hook Executables & OS Compatibility:**
   - Confirm `.githooks/` is enabled (`git config --get core.hooksPath` should return `.githooks`; if unset, prompt the developer to run `git config core.hooksPath .githooks`).
   - Run `node --test .claude/hooks/hooks.test.mjs` and confirm `.githooks/pre-commit`, `.githooks/commit-msg`, and `.githooks/pre-push` are executable on the developer's OS (macOS, Linux, or Windows Git Bash / WSL). Report any path or permission issues immediately.
4. **Verify Local Tooling:**
   - Confirm Java 21 (`java -version`), Maven (`./backend/mvnw -v`), Node 22+ (`node -v`), and Docker (`docker --version`) are available before running builds.
