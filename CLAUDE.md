# CLAUDE.md — Claude Code Entry Point

Follow [`AGENTS.md`](AGENTS.md) for all project invariants, mandatory tech stack versions, Git branching rules, and Conventional Commits. Never duplicate or contradict `AGENTS.md` or `openspec/`.

## Source of Truth Hierarchy

1. [`AGENTS.md`](AGENTS.md) — Universal non-negotiable rules & stack constraints.
2. @.agents/rules/coding-standards.md — Canonical living coding standards (auto-imported directly by Claude Code; never duplicated).
3. [`openspec/config.yaml`](openspec/config.yaml) & `openspec/specs/` — Domain requirements in EARS notation.
4. `openspec/changes/<active-change>/` — Active `proposal.md`, `specs/`, `design.md`, and `tasks.md`.
5. [`docs/decisions/index.md`](docs/decisions/index.md), [`docs/api.md`](docs/api.md), & [`docs/discovery-canvas.md`](docs/discovery-canvas.md) — Traceable decisions, living API contract, & product vision.

## Working Agreement for Claude Code

- Implement one logical task at a time from `openspec/changes/<change>/tasks.md`, following `.agents/rules/coding-standards.md` and leaving `// Decision X.Y` pointers where non-obvious.
- Whenever the user/prompter changes direction or answers a `question-round`, record the decision via `node scripts/record-decision.mjs` (never skip recording).
- Check the active Git forge via `node scripts/check-setup.mjs --forge` (`gh` for GitHub vs. `glab` for GitLab) — never read `.env` directly.
- Run real verification commands (`cd backend && ./mvnw test`, `cd frontend && npm run typecheck && npm run lint && npm run build`) before claiming completion.
- Keep diffs small, atomic, and strictly scoped to the active spec.

---

## First-Run Bootstrap & Self-Configuration

> **Instruction for Claude Code when a developer opens this repository for the first time:**
> Run this lightweight self-check once before starting feature work (do not modify tracked files without telling the developer what changed):

1. **Run Forge-Aware Setup Preflight:**
   - Run `node scripts/check-setup.mjs` (checks active `GIT_FORGE` `gh` vs. `glab` CLI/auth, `.githooks/` activation, `.env` status without printing secrets, `.claude/settings.json` JSON validity, `.agents/rules/coding-standards.md`, `docs/decisions/index.md`, `docs/api.md`, `.gitlab-ci.yml`, Node 22+, Java 21, Docker).
   - Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`.
2. **Inspect Harness Configuration:**
   - Verify `.claude/skills/` contains the OpenSpec skills, shared skills (`record-decision`, `question-round`, `pr-memo`), and project skills (`spec-implementer`, `backend-spring-check`, `frontend-vue-check`, `evidence-pipeline-guard`).
   - Verify `.claude/agents/` contains `spec-builder.md`, `reviewer.md`, and `test-writer.md`.
3. **Check Team `[TBD]` Decisions (`docs/decisions/index.md`):**
   - Check whether the team has resolved open decisions **O1** (UI component library: PrimeVue vs. shadcn-vue) and **O2** (Interviewer LLM provider; evaluator is already resolved in **1.6 / D14** — ki:connect NRW).
   - If resolved in `docs/decisions/index.md` or `package.json`/`pom.xml`, run the `record-decision` skill (`node scripts/record-decision.mjs ... --resolves O<k>`) to synchronize `openspec/config.yaml`, `CLAUDE.md`, and affected skills (note developer-specific preferences in untracked `.claude/settings.local.json`).
