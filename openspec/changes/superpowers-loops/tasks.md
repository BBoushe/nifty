# Tasks: Superpowers Loops, Resumable Tasks & Archive Gate

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `chore/superpowers-loops` (not created)
- **Worktree:** —
- **Current:** not started
- **Next action:** Task 1, first step
- **Uncommitted changes:** no
- **PR / MR:** —
- **Open review findings:** —

## Tasks

Owned files: `.claude/settings.json`, `.claude/agents/{spec-builder,reviewer,test-writer}.md`, `.claude/skills/spec-implementer/SKILL.md`, `openspec/config.yaml`, `.claude/hooks/archive-gate.mjs` (new), `.githooks/pre-push`, the `archive gate` describe at the end of `.claude/hooks/hooks.test.mjs`, one new row in the AGENTS.md §6 parity table, new rows at the end of the `docs/setup.md` inventory table. Decisions 3.7, 3.8 and 3.9 are already recorded. Read `design.md` first.

- [ ] **1. Archive gate core (RED → GREEN)** — REQ-SL-04
  - [ ] Add `describe('archive gate')` with the fixture cases from design §5.
  - [ ] Run `node --test .claude/hooks/hooks.test.mjs`. Expect: FAIL (module missing).
  - [ ] Implement `findCompletedUnarchivedChanges` (design §4).
  - [ ] Run the tests. Expect: exit 0, including the repo-invariant test.
  - [ ] Commit `feat(hooks): add archive gate for completed openspec changes`.
- [ ] **2. Stop hook and pre-push wiring** — REQ-SL-04
  - [ ] Add the `--stop-hook` tests (design §5). Run them. Expect: FAIL.
  - [ ] Implement `main()` with `--stop-hook` and `--pre-push`, then wire `.githooks/pre-push` (design §4).
  - [ ] Run `node --test .claude/hooks/hooks.test.mjs`. Expect: exit 0.
  - [ ] Manual check: push a scratch branch with a fixture change whose tasks are all `[x]`. Expect: blocked; `NIFTY_SKIP_GATE=1` passes. Remove the fixture afterwards.
  - [ ] Commit `feat(hooks): run archive gate on Claude stop and feature-branch pre-push`.
- [ ] **3. Settings** — REQ-SL-01
  - [ ] Settle the plugin key and semantics (design §1).
  - [ ] Add `enabledPlugins` and `hooks.Stop` to `.claude/settings.json`.
  - [ ] Run `node -e "JSON.parse(require('fs').readFileSync('.claude/settings.json','utf8'))"`. Expect: exit 0.
  - [ ] Commit `chore(claude): declare superpowers plugin and archive-gate stop hook`.
- [ ] **4. Loop in role prompts** — REQ-SL-02, REQ-SL-03, REQ-SL-05, REQ-SL-06
  - [ ] Update `spec-builder.md` (loop table + Verification format + Status/commit rule), `reviewer.md` (two checks), `test-writer.md` and `spec-implementer/SKILL.md` (point to the loop) per design §2.
  - [ ] Commit `docs(agents): wire superpowers loop and RED evidence into roles`.
- [ ] **5. OpenSpec rules** — REQ-SL-05
  - [ ] Extend `openspec/config.yaml` per design §3.
  - [ ] Run `OPENSPEC_TELEMETRY=0 openspec validate --all`. Expect: exit 0.
  - [ ] Commit `chore(specs): require resumable tasks.md layout and RED/GREEN evidence`.
- [ ] **6. Parity docs**
  - [ ] AGENTS.md §6: add a row "Archive completed OpenSpec changes" → Claude: `archive-gate.mjs` Stop hook; others: `.githooks/pre-push` + CI test.
  - [ ] Add rows to the end of the `docs/setup.md` inventory table for `archive-gate.mjs` and the Stop hook.
  - [ ] Add a `CHANGELOG.md` `[Unreleased]` entry.
  - [ ] Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
  - [ ] Commit `docs(setup): document archive gate across tools`.

## Delivery

- [ ] `git fetch origin && git rebase origin/dev`. On conflict in `CHANGELOG.md`, keep both entries; in `hooks.test.mjs`, `AGENTS.md` and `docs/setup.md`, keep both sides.
- [ ] Re-run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0. The repo-invariant test now also guards `dev`.
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive superpowers-loops -y --skip-specs`. Continue in `openspec/changes/archive/*-superpowers-loops/tasks.md`. If `docs/feature_history.md` exists, add this change's entry. Commit `chore(specs): archive superpowers-loops`.
- [ ] `node scripts/forge.mjs push` (the new pre-push gate runs here for the first time).
- [ ] PR/MR body via `pr-memo`, then `node scripts/forge.mjs pr --title "chore: superpowers loops, resumable tasks and archive gate" --body-file <file>`. Record the links in Status.
- [ ] Cold review session: verdict has no Blocking items.
- [ ] GitLab MR pipeline green; `gh pr checks <n>` green.
- [ ] Merge on GitHub only: `gh pr merge <n> --merge`.
- [ ] `node scripts/forge.mjs sync dev`, then `node scripts/forge.mjs status` prints 0/0 for every branch.
- [ ] After `git merge-base --is-ancestor chore/superpowers-loops origin/dev`: delete the branch on both remotes and locally; `git worktree remove ../nifty-superpowers-loops`.

## Handoff Log
