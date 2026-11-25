# Tasks: Orchestrated Multi-Agent Delivery

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see .agents/skills/orchestrate-changes/SKILL.md). -->
- **Branch:** `feat/agent-orchestration`
- **Worktree:** main checkout `/Users/alek/Developer/Programming/nifty`
- **Current:** Tasks 1-3 done; Delivery next
- **Next action:** push, PR/MR, cold review, ask the user with the architecture summary (archived at openspec/changes/archive/2026-11-25-agent-orchestration/)
- **Uncommitted changes:** no
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
- [x] **3. Skill and docs** — REQ-OR-03, REQ-OR-04
  - [x] Write `.agents/skills/orchestrate-changes/SKILL.md` (design §3) and the `.claude/skills/` symlink.
  - [x] `AGENTS.md` dispatch row, `docs/workflow.md` §4 subsection, `docs/setup.md` inventory rows, board pointer, CHANGELOG.
  - [x] Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` and `OPENSPEC_TELEMETRY=0 openspec validate --all --strict`. Expect: exit 0.
  - [x] Commit `docs(agents): add the orchestrate-changes skill`.

## Delivery

- [x] `git fetch origin && git merge origin/dev` (no rebase). Re-run the tests.
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive agent-orchestration -y --skip-specs`; history entry; commit `chore(specs): archive agent-orchestration`.
- [ ] `node scripts/forge.mjs push`; PR/MR via `pr-memo` + `node scripts/forge.mjs pr`. Record links in Status.
- [ ] Cold review (fresh `reviewer` agent): no Blocking items.
- [ ] Both forges green (`node scripts/forge.mjs prs`).
- [ ] Ask the user with the architecture summary (REQ-OR-04); merge on GitHub only; `node scripts/forge.mjs sync dev`; `status` 0/0.
- [ ] Delete the branch on both remotes and locally.

## Handoff Log

### 2026-11-25 — Tasks 1-2 (main session)
- **Did:** `findEnvPath` in `check-setup.mjs` (worktree falls back to the main checkout's `.env`); `checksSummary`, `prRows` and the `prs` command in `forge.mjs`. GitLab lookup uses `glab mr list/view -R <url> -F json`, because the FH instance serves from a sub-path and the hand-built API path returned 404. One commit for both tasks: the new tests share one `describe`.
- **Verification:** RED: `node --test scripts/harness-scripts.test.mjs` → FAIL (module has no `checksSummary` export); GREEN: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 (43/43). `node scripts/forge.mjs prs` → exit 0 against both live forges.
- **Decisions:** 3.11 (recorded in this change).

### 2026-11-25 — Task 3 (main session)
- **Did:** `orchestrate-changes` skill (+ `.claude/skills` symlink); `AGENTS.md` dispatch row; `docs/workflow.md` §4 subsection and forge-command row; two `docs/setup.md` inventory rows; board pointer; CHANGELOG.
- **Verification:** RED: n/a (no behaviour). ste-lint: 0 hard findings on the skill, proposal and design; harness tests 43/43; `openspec validate --all --strict` 8/8.
- **Next:** Delivery.
