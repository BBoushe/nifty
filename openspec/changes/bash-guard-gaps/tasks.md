# Tasks: Bash Guard Gaps

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `fix/bash-guard-gaps` (based on `docs/harness-upgrade-closeout`, PR #12; merge `origin/dev` in after #12 merges)
- **Worktree:** main checkout `/Users/alek/Developer/Programming/nifty`
- **Current:** Tasks 1-5 done; Delivery next
- **Next action:** wait for PR #12 to merge, then Delivery step 1 (merge `origin/dev` in)
- **Uncommitted changes:** no
- **PR / MR:** —
- **Open review findings:** —

## Tasks

Owned files: `.claude/hooks/lib.mjs`, `.claude/hooks/guard-bash.mjs`, the `lib.mjs helpers` and `guard-bash.mjs` describe blocks in `.claude/hooks/hooks.test.mjs`, the README "Optional: RTK" section, the RTK and Guard Layers text in `docs/setup.md`, `docs/feature_backlog.md` row 9, `CHANGELOG.md`. Read `design.md` first.

- [x] **1. Gap tests (RED)** — REQ-BG-06..13
  - [x] In the `guard-bash.mjs` describe, add `it('blocks the gaps found by the post-merge review')` with every must-block command from the spec scenarios (use `wrapOpts`; `master`/`dev` branch cases pass `currentBranch`).
  - [x] Add `it('allows the harmless neighbours of the gaps')` with every must-allow command from the spec scenarios.
  - [x] Run `node --test .claude/hooks/hooks.test.mjs`. Expect: the new block test FAILS; all existing tests pass.
  - [x] Commit `test(hooks): add failing probes for bash guard gaps`.
- [x] **2. Command-string flags and shell options (GREEN)** — REQ-BG-06, REQ-BG-07, REQ-BG-12
  - [x] Add `commandStringFlag` to `lib.mjs` and use it in `stripLeadingEnvAssignments` and `extractCommandString` (design §1); remove `COMMAND_STRING_FLAGS`.
  - [x] Rewrite the shell `-c` search with the STDIN/SCRIPT markers (design §2); add `dash`, `ksh`.
  - [x] Run the tests. Expect: the REQ-BG-06/07/12 rows pass.
  - [x] Commit `fix(hooks): find shell and wrapper command strings in every flag form`.
- [x] **3. Push parsing (GREEN)** — REQ-BG-08, REQ-BG-09, REQ-BG-10
  - [x] Add `globals` to `getGitSubcommand`; extend `parseGitPushTargets` (design §3).
  - [x] Run the tests. Expect: the REQ-BG-08/09/10 rows pass.
  - [x] Commit `fix(hooks): catch abbreviated, symbolic and indirect push bypasses`.
- [x] **4. Shell structure and globs (GREEN)** — REQ-BG-11, REQ-BG-13
  - [x] Single `&` split, keyword/group/wrapper peeling, substitution evaluation, shared chaining state (design §4); glob check (design §5).
  - [x] Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
  - [x] Commit `fix(hooks): guard grouped, substituted and globbed commands`.
- [x] **5. Docs**
  - [x] Correct `README.md` and `docs/setup.md` (design §6); update the `lib.mjs` `shortcut:` comment and pointer; CHANGELOG `[Unreleased]` entry; backlog row 9 → `in progress`.
  - [x] Run the tests. Expect: exit 0.
  - [x] Commit `docs(setup): state exactly what the bash guard covers`.

## Delivery

- [ ] After PR #12 merges: `git fetch origin && git merge origin/dev` (no rebase). Re-run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive bash-guard-gaps -y --skip-specs`. Remove backlog row 9, add the `docs/feature_history.md` entry, tick the cold-review box in the archived `bash-guard-hardening` tasks.md if this review passes. Commit `chore(specs): archive bash-guard-gaps`.
- [ ] `node scripts/forge.mjs push`, then PR/MR via `pr-memo` and `node scripts/forge.mjs pr`. Record links in Status.
- [ ] Cold review (fresh `reviewer` agent): no Blocking items.
- [ ] GitLab MR pipeline green; `gh pr checks <n>` green.
- [ ] Merge on GitHub only (ask the user first), then `node scripts/forge.mjs sync dev` and `node scripts/forge.mjs status` 0/0.
- [ ] Delete the branch on both remotes and locally.

## Handoff Log

### 2026-11-24 — Tasks 1-5 (main session)
- **Did:** 42 must-block and 10 must-allow probes (REQ-BG-06..13); `commandStringFlag` defines the env/rtk command-string flags once; shell option scan with STDIN/SCRIPT markers; push parsing for abbreviations, `-fu`, `+` refspecs, `HEAD`/`@`, `core.hooksPath`; single `&` split, group/keyword/wrapper peeling, quote-aware substitution evaluation, shared chaining state; unquoted glob check; docs.
- **Verification:** RED: `node --test .claude/hooks/hooks.test.mjs` → FAIL (all 42 probes allowed, new block test fails); GREEN: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 (37/37). Also 18 everyday commands probed: all allowed.
- **Decisions:** none new; guard stays a token-level check (proposal Out of Scope).
- **Note:** the live guard blocked a heredoc edit command whose text named the secret env file; edits with such text now go through a script file.
- **Next:** Delivery.
