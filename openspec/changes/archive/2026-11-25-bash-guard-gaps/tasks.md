# Tasks: Bash Guard Gaps

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `fix/bash-guard-gaps` merged into `dev` (a1ac54e); deleted on origin, gitlab and locally
- **Worktree:** main checkout (no separate worktree)
- **Current:** done: PR #13 merged on GitHub after 6 cold reviews, `forge.mjs sync dev` ran, MR !12 shows merged, `forge.mjs status` 0/0
- **Next action:** none. Later findings go to the backlog item `harness-review-followups`
- **Uncommitted changes:** no
- **PR / MR:** https://github.com/BBoushe/nifty/pull/13 (merged) · https://git.inf.fh-dortmund.de/01/alpac001/nifty/-/merge_requests/12 (merged)
- **Open review findings:** none Blocking (round 6 fixed); Convention Candidates and older gaps are in `docs/feature_backlog.md` Notes

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
- [x] **6. Second review fixes (GREEN)** — REQ-BG-13, regression
  - [x] Make the 3 failing tests from `2a2fa81` pass (Status lists the four code fixes).
  - [x] Docs limits list, stranded JSDoc, proposal note on guard-agent.
  - [x] Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: only the repo-invariant test may fail until the archive step.
  - [x] Commit `fix(hooks): close the second review's guard gaps`.

## Delivery

- [x] After PR #12 merges: `git fetch origin && git merge origin/dev` (no rebase). Re-run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive bash-guard-gaps -y --skip-specs`. Remove backlog row 9, add the `docs/feature_history.md` entry, tick the cold-review box in the archived `bash-guard-hardening` tasks.md if this review passes. Commit `chore(specs): archive bash-guard-gaps`.
- [x] `node scripts/forge.mjs push`, then PR/MR via `pr-memo` and `node scripts/forge.mjs pr`. Record links in Status.
- [x] Cold review (fresh `reviewer` agent): no Blocking items.
- [x] GitLab MR pipeline green; `gh pr checks <n>` green.
- [x] Merge on GitHub only (ask the user first), then `node scripts/forge.mjs sync dev` and `node scripts/forge.mjs status` 0/0.
- [x] Delete the branch on both remotes and locally.

## Handoff Log

### 2026-11-24 — Tasks 1-5 (main session)
- **Did:** 42 must-block and 10 must-allow probes (REQ-BG-06..13); `commandStringFlag` defines the env/rtk command-string flags once; shell option scan with STDIN/SCRIPT markers; push parsing for abbreviations, `-fu`, `+` refspecs, `HEAD`/`@`, `core.hooksPath`; single `&` split, group/keyword/wrapper peeling, quote-aware substitution evaluation, shared chaining state; unquoted glob check; docs.
- **Verification:** RED: `node --test .claude/hooks/hooks.test.mjs` → FAIL (all 42 probes allowed, new block test fails); GREEN: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 (37/37). Also 18 everyday commands probed: all allowed.
- **Decisions:** none new; guard stays a token-level check (proposal Out of Scope).
- **Note:** the live guard blocked a heredoc edit command whose text named the secret env file; edits with such text now go through a script file.
- **Next:** Delivery.

### 2026-11-25 — Task 6 (main session)
- **Did:** second cold review fixes: tokenizer records a glob character outside quotes; `<` redirection targets get the glob check; `[!x]` and POSIX classes in globs; `stripQuotedHeredocBodies` drops quoted heredoc bodies (still scanned for the secret env file); limits listed in README and setup; proposal note on guard-agent; `-n` comment.
- **Verification:** RED: `node --test .claude/hooks/hooks.test.mjs` → FAIL (3 new tests, commit 2a2fa81); GREEN: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 (39/39). Everyday commands re-probed: allowed.
- **Next:** Delivery.

### 2026-11-25 — Third review fixes (main session)
- **Did:** heredoc scan now tracks quotes, comments and `$( … )`; heredoc bodies are checked as commands when a shell or `eval` is the command word or a segment starts with a substitution; `[^x]` globs; design §6; limits list; older gaps moved to the backlog.
- **Verification:** RED: `node --test .claude/hooks/hooks.test.mjs` → FAIL (8 new probes); GREEN: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 (40/40). Probe file: commit/PR heredocs with `sh` in the title still allowed.
- **Next:** push, new cold review, merge (ask the user).

### 2026-11-25 — Fourth review fixes (main session)
- **Did:** replaced the heredoc deny-list scanner with an allow-list (`stripTextHeredocs`, design §6); removed the `mayRunText` heuristic; `splitShellSegments` ends backticks at the next unescaped backtick.
- **Verification:** RED: `node --test .claude/hooks/hooks.test.mjs` → FAIL (12 new probes, fake heredocs and shared commands); GREEN: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 (40/40). Probe file: commit/PR heredoc forms still allowed (9/9).
- **Next:** push, cold review round 5, merge (ask the user).

### 2026-11-25 — Fifth review fixes (main session)
- **Did:** first-line form needs a stdin message flag (`-F -`, `--file -`, `--body-file -`) and a plain prefix before `<<` (no open quote, no trailing backslash); `<<-` dropped from both forms; design §6 notes the over-blocking cost.
- **Verification:** RED: `node --test .claude/hooks/hooks.test.mjs` → FAIL (3 new probes); GREEN: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 (40/40). Probe file 9/9.
- **Next:** push, cold review round 6, merge (ask the user).

### 2026-11-25 — Sixth review fixes (main session)
- **Did:** `isUnambiguousBody`: a `-m "$(cat <<'X' …)"` body counts as text only with no line starting with the delimiter, balanced parentheses and even quote/backtick counts (bash 3.2 ends the substitution at `X)` or an unbalanced `)`). Otherwise its lines are checked as commands.
- **Verification:** RED: `node --test .claude/hooks/hooks.test.mjs` → FAIL (3 new probes); GREEN: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0. Probe file 9/9.
- **Next:** push, ask the user about merging.
