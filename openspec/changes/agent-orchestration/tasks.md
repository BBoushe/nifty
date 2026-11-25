# Tasks: Orchestrated Multi-Agent Delivery

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see .agents/skills/orchestrate-changes/SKILL.md). -->
- **Branch:** `feat/agent-orchestration`
- **Worktree:** main checkout `/Users/alek/Developer/Programming/nifty`
- **Current:** change written; Task 1 next
- **Next action:** Task 1, first step
- **Uncommitted changes:** yes
- **PR / MR:** —
- **Open review findings:** —

## Tasks

Owned files: `scripts/check-setup.mjs`, `scripts/forge.mjs`, a new `describe` at the end of `scripts/harness-scripts.test.mjs`, `.agents/skills/orchestrate-changes/` + `.claude/skills/orchestrate-changes` symlink, one `AGENTS.md` dispatch row, `docs/workflow.md` §4, `docs/setup.md` inventory rows, `CHANGELOG.md`, `docs/feature_backlog.md`, `docs/prompts/harness-upgrade-board.md` (pointer only). Decision 3.11 is recorded.

- [x] **1. Worktree `.env` lookup (RED → GREEN)** — REQ-OR-01
  - [x] Test: a temp repo with a main checkout `.env` (`GIT_FORGE=both`) and a linked worktree; `resolveForgeConfig(worktree, {})` is `both`; with a worktree `.env` (`GIT_FORGE=github`) it is `github`.
  - [x] Run `node --test scripts/harness-scripts.test.mjs`. Expect: FAIL.
  - [x] Implement `findEnvPath` (design §1).
  - [x] Run the tests. Expect: exit 0.
  - [x] Commit `feat(forge): read the main checkout's .env from a worktree`.
- [x] **2. `forge.mjs prs` (RED → GREEN)** — REQ-OR-02
  - [x] Tests for `checksSummary` and `prRows` (design §2 cases).
  - [x] Run the tests. Expect: FAIL.
  - [x] Implement the helpers and the `prs` command; run it against the live forges.
  - [x] Run the tests. Expect: exit 0.
  - [x] Commit `feat(forge): show PRs and MRs side by side by branch`.
- [ ] **3. Skill and docs** — REQ-OR-03, REQ-OR-04
  - [ ] Write `.agents/skills/orchestrate-changes/SKILL.md` (design §3) and the `.claude/skills/` symlink.
  - [ ] `AGENTS.md` dispatch row, `docs/workflow.md` §4 subsection, `docs/setup.md` inventory rows, board pointer, CHANGELOG.
  - [ ] Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` and `OPENSPEC_TELEMETRY=0 openspec validate --all --strict`. Expect: exit 0.
  - [ ] Commit `docs(agents): add the orchestrate-changes skill`.

## Delivery

- [ ] `git fetch origin && git merge origin/dev` (no rebase). Re-run the tests.
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive agent-orchestration -y --skip-specs`; history entry; commit `chore(specs): archive agent-orchestration`.
- [ ] `node scripts/forge.mjs push`; PR/MR via `pr-memo` + `node scripts/forge.mjs pr`. Record links in Status.
- [ ] Cold review (fresh `reviewer` agent): no Blocking items.
- [ ] Both forges green (`node scripts/forge.mjs prs`).
- [ ] Ask the user with the architecture summary (REQ-OR-04); merge on GitHub only; `node scripts/forge.mjs sync dev`; `status` 0/0.
- [ ] Delete the branch on both remotes and locally.

## Handoff Log
