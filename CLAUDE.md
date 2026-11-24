# CLAUDE.md — Claude Code Entry Point

Follow [`AGENTS.md`](AGENTS.md) for all project invariants, mandatory tech stack versions, Git branching rules and Conventional Commits. Never duplicate or contradict `AGENTS.md` or `openspec/`.

## Source of Truth Hierarchy

1. [`AGENTS.md`](AGENTS.md) — Universal non-negotiable rules & stack constraints.
2. @.agents/rules/coding-standards.md — Canonical living coding standards. Claude Code imports this file directly. Never duplicate it.
3. @.agents/rules/spec-driven.md — Canonical spec-first & domain invariant rules. Claude Code imports this file directly (Decision 3.4).
4. @.agents/rules/git-workflow.md — Canonical branching, Conventional Commits, & mandatory PR/MR template rules. Claude Code imports this file directly (Decision 3.4).
5. [`openspec/config.yaml`](openspec/config.yaml) & `openspec/specs/` — Domain requirements in EARS notation.
6. `openspec/changes/<active-change>/` — Active `proposal.md`, `specs/`, `design.md` and `tasks.md`.
7. [`docs/decisions/index.md`](docs/decisions/index.md), [`docs/api.md`](docs/api.md), & [`docs/discovery-canvas.md`](docs/discovery-canvas.md) — Traceable decisions, living API contract & product vision.

## Working Agreement for Claude Code

- Implement one logical task at a time from `openspec/changes/<change>/tasks.md`. Follow `.agents/rules/coding-standards.md`. Leave `// Decision X.Y` pointers where the reason is not obvious.
- Before you edit `backend/**`, read `.agents/rules/backend-java.md` (+ `docs/api.md`). Before you edit `frontend/**`, read `.agents/rules/frontend-vue.md`.
- Whenever the user/prompter changes direction or answers a `question-round`, record the decision with `node scripts/record-decision.mjs`. Never skip recording.
- Check the active Git forge with `node scripts/check-setup.mjs --forge` (`gh`, `glab`, or `node scripts/forge.mjs` for `both`). Never read `.env` directly.
- When you open or update a PR/MR, **always** fill `.github/PULL_REQUEST_TEMPLATE.md` (mirrored in `.gitlab/merge_request_templates/Default.md`) with the `pr-memo` skill. Never write an ad-hoc summary that drops the Spec/Issue or Verification checklists.
- Run real verification commands (`cd backend && ./mvnw test`, `cd frontend && npm run typecheck && npm run lint && npm run build`) before you claim completion.
- Keep diffs small, atomic and scoped to the active spec.

---

## First-Run Bootstrap & Self-Configuration

> **Instruction for Claude Code when a developer opens this repository for the first time:**
> Run this self-check once before you start feature work. Do not change tracked files without telling the developer what you changed.

1. **Run Forge-Aware Setup Preflight:**
   - Run `node scripts/check-setup.mjs`. It checks these items:
     - The active `GIT_FORGE` `gh` vs. `glab` CLI/auth.
     - `.githooks/` activation.
     - `.env` status, without printing secrets.
     - `.claude/settings.json` JSON validity.
     - `.agents/rules/coding-standards.md`, `docs/decisions/index.md`, `docs/api.md` and `.gitlab-ci.yml`.
     - Node 22+, Java 21 and Docker.
   - Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`.
2. **Inspect Harness Configuration:**
   - Check that `.claude/skills/` contains the OpenSpec skills, shared skills (`record-decision`, `question-round`, `pr-memo`) and project skills (`spec-implementer`, `backend-spring-check`, `frontend-vue-check`, `evidence-pipeline-guard`).
   - Check that `.claude/agents/` contains `spec-builder.md`, `reviewer.md` and `test-writer.md`.
3. **Check Team `[TBD]` Decisions (`docs/decisions/index.md`):**
   - Check if the team resolved open decisions **O1** (UI component library: PrimeVue vs. shadcn-vue) and **O2** (Interviewer LLM provider). The team already resolved the evaluator in **1.6 / D14** — ki:connect NRW.
   - If the team resolved a decision in `docs/decisions/index.md` or `package.json`/`pom.xml`, run the `record-decision` skill (`node scripts/record-decision.mjs ... --resolves O<k>`). This synchronizes `openspec/config.yaml`, `CLAUDE.md` and affected skills. Note developer-specific preferences in untracked `.claude/settings.local.json`.
