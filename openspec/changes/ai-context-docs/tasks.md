# Tasks: AI Context Docs & ASD-STE100

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `chore/ai-context-docs` (not created)
- **Worktree:** —
- **Current:** not started
- **Next action:** Task 1, first step
- **Uncommitted changes:** no
- **PR / MR:** —
- **Open review findings:** —

## Tasks

Owned files: `AGENTS.md` §3 dispatch table, `.agents/rules/spec-driven.md`, `docs/feature_history.md`, `docs/feature_backlog.md`, `docs/ai_docs/**`, `.agents/skills/asd-ste100/` + `.claude/skills/asd-ste100` symlink, new rows at the end of the `docs/setup.md` inventory table, the `feature history` describe in `scripts/harness-scripts.test.mjs`. Decisions 3.5 and 3.6 are already recorded. Read `design.md` first.

- [x] **1. History test (RED)** — REQ-CD-02
  - [x] Add `describe('feature history')` per design §2.
  - [x] Run `node --test scripts/harness-scripts.test.mjs`. Expect: FAIL naming the 4 archived folders (no history file yet).
  - [x] Commit `test(harness): require a history entry for every archived change`.
- [ ] **2. Backfill the history (GREEN)** — REQ-CD-01
  - [ ] Write `docs/feature_history.md` per design §1 for the 4 changes archived on 2026-11-24.
  - [ ] Run `node --test scripts/harness-scripts.test.mjs`. Expect: exit 0.
  - [ ] Commit `docs(history): add feature history backfilled from archived changes`.
- [ ] **3. Backlog** — REQ-CD-03
  - [ ] Write `docs/feature_backlog.md` per design §3.
  - [ ] Commit `docs(backlog): add feature backlog with blast radius and blockers`.
- [ ] **4. Library notes** — REQ-CD-04
  - [ ] Write `docs/ai_docs/README.md` and the three notes per design §4. Each note has `Version:`, `Sources:` and `Retrieved:`, and is ≤150 lines.
  - [ ] Check: `wc -l docs/ai_docs/*.md` (each ≤150); every version matches `backend/pom.xml`.
  - [ ] Commit `docs(ai-docs): add curated Spring Boot 4, Spring AI 2 and Timefold notes`.
- [ ] **5. ASD-STE100 skill (vet first)** — REQ-CD-06
  - [ ] Vet the source per design §5 step 1. Record the result (accepted, or rejected with the reason) in the Handoff Log.
  - [ ] If accepted: install, move, symlink (design §5 step 2). Run the linter on one doc (step 3). Expect: the linter runs and prints findings.
  - [ ] Commit `chore(skills): add vetted asd-ste100 skill`.
- [ ] **6. Rules and dispatch** — REQ-CD-05, REQ-CD-06
  - [ ] Add rule 6 to `.agents/rules/spec-driven.md` and the three AGENTS.md §3 rows (design §6).
  - [ ] Add inventory rows to the end of the `docs/setup.md` table for `docs/feature_history.md`, `docs/feature_backlog.md`, `docs/ai_docs/` and `.agents/skills/asd-ste100/`.
  - [ ] Add a `CHANGELOG.md` `[Unreleased]` entry.
  - [ ] Run `OPENSPEC_TELEMETRY=0 openspec validate --all` and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: both exit 0.
  - [ ] Commit `docs(agents): dispatch ai_docs, backlog/history lifecycle and STE rule`.

## Delivery

- [ ] `git fetch origin && git rebase origin/dev`. On conflict in `CHANGELOG.md`, keep both entries; in `scripts/harness-scripts.test.mjs`, `docs/setup.md` and `AGENTS.md`, keep both sides.
- [ ] Backfill a history entry for every change archived on `dev` since task 2 (the test tells you which).
- [ ] Re-run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive ai-context-docs -y --skip-specs`, then add its own history entry (the test requires it). Continue in `openspec/changes/archive/*-ai-context-docs/tasks.md`. Commit `chore(specs): archive ai-context-docs`.
- [ ] `node scripts/forge.mjs push`
- [ ] PR/MR body via `pr-memo`, then `node scripts/forge.mjs pr --title "docs: add feature history, backlog, ai_docs and STE rule" --body-file <file>`. Record the links in Status.
- [ ] Cold review session: verdict has no Blocking items.
- [ ] GitLab MR pipeline green; `gh pr checks <n>` green.
- [ ] Merge on GitHub only: `gh pr merge <n> --merge`.
- [ ] `node scripts/forge.mjs sync dev`, then `node scripts/forge.mjs status` prints 0/0 for every branch.
- [ ] After `git merge-base --is-ancestor chore/ai-context-docs origin/dev`: delete the branch on both remotes and locally; `git worktree remove ../nifty-ai-context-docs`.

## Handoff Log

### Task 1 — History test (RED)
- **Commits:** `test(harness): require a history entry for every archived change`
- **Verification:** RED `node --test scripts/harness-scripts.test.mjs` → FAIL (names the 4 archived folders); GREEN n/a (task 2)
- **Contracts/Migrations added:** none
- **Decisions:** none new (3.5, 3.6 already recorded)
- **Open Questions:** none
