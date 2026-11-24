# Tasks: Bash Guard Hardening

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `fix/bash-guard-hardening` (not created)
- **Worktree:** —
- **Current:** not started
- **Next action:** Task 1, first step
- **Uncommitted changes:** no
- **PR / MR:** —
- **Open review findings:** —

## Tasks

Owned files: `.claude/hooks/lib.mjs`, `.claude/hooks/guard-bash.mjs`, the `lib`/`guard-bash` describe blocks in `.claude/hooks/hooks.test.mjs`, the README "Optional: RTK" section, and the RTK paragraphs + "Guard Layers" in `docs/setup.md`. Read `design.md` first.

- [ ] **1. Must-block and must-allow tests (RED)** — REQ-BG-01..05
  - [ ] In the `guard-bash` describe of `hooks.test.mjs`, add `it('blocks guarded commands hidden behind wrappers and command strings')`. Use `cleanOpts` (`currentBranch: 'feat/x'`) and assert `blocked === true` for every row of the proposal table, plus `bash -lc "git push origin master"`, `sudo -u x git push origin master`, `nice -n 5 git push origin master`, `env -S "git push origin master"`, and four nested `sh -c` levels around `echo hi`.
  - [ ] Add `it('allows harmless commands behind wrappers')`: assert `blocked === false` for `rtk git status`, `rtk err npm run build`, `rtk test ./mvnw -B test`, `sh -c "npm run lint"`, `time ./mvnw -B test`, `git push origin feat/x`, `git push --no-verify origin feat/x`, `git push -n origin dev`, and `git commit -m "docs: mention sh -c and .env"`.
  - [ ] Run `node --test .claude/hooks/hooks.test.mjs`. Expect: the new must-block test FAILS; all existing tests pass.
  - [ ] Commit `test(hooks): add wrapper and command-string guard probes`.
- [ ] **2. Wrapper peeling (GREEN part 1)** — REQ-BG-01
  - [ ] Extend `stripLeadingEnvAssignments` per the design §1 table.
  - [ ] Run `node --test .claude/hooks/hooks.test.mjs`. Expect: the wrapper rows pass; only the command-string and `--no-verify` rows still fail; the `guard-agent` tests stay green.
  - [ ] Commit `fix(hooks): peel nice, time, sudo, xargs, env -u and rtk run wrappers`.
- [ ] **3. Command-string recursion with a cap** — REQ-BG-02, REQ-BG-03
  - [ ] Implement design §2 in `guard-bash.mjs`.
  - [ ] Run the tests. Expect: the `-c`, `eval` and depth rows pass.
  - [ ] Commit `fix(hooks): evaluate sh -c, rtk run -c, env -S and eval strings`.
- [ ] **4. Block `--no-verify` pushes to protected targets** — REQ-BG-04
  - [ ] Implement design §3.
  - [ ] Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
  - [ ] Commit `fix(hooks): block git push --no-verify to protected branches`.
- [ ] **5. Comment and docs**
  - [ ] Replace the wrong `shortcut:` comment in `lib.mjs` with design §4.
  - [ ] Update `README.md` (Optional: RTK) and `docs/setup.md` per design §5, including the "Guard Layers" subsection.
  - [ ] Add a `CHANGELOG.md` `[Unreleased]` entry.
  - [ ] Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
  - [ ] Commit `docs(setup): document guard layers and RTK wrapper limits`.

## Delivery

- [ ] `git fetch origin && git rebase origin/dev`. On conflict in `CHANGELOG.md`, keep both entries; in shared test files, keep both describe blocks.
- [ ] Re-run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive bash-guard-hardening -y --skip-specs` (harness change; continue in `openspec/changes/archive/*-bash-guard-hardening/tasks.md`). Commit `chore(specs): archive bash-guard-hardening`.
- [ ] If `docs/feature_history.md` exists after the rebase, add this change's entry (format in its header; the harness test enforces it) and amend it into the archive commit.
- [ ] `node scripts/forge.mjs push`
- [ ] PR/MR body via the `pr-memo` skill, then `node scripts/forge.mjs pr --title "fix(hooks): harden bash guard against wrappers and command strings" --body-file <file>`. Record the links in Status.
- [ ] Cold review session (board review prompt): verdict has no Blocking items.
- [ ] GitLab MR pipeline green; `gh pr checks <n>` green.
- [ ] Merge on GitHub only: `gh pr merge <n> --merge`.
- [ ] `node scripts/forge.mjs sync dev`, then `node scripts/forge.mjs status` prints 0/0 for every branch.
- [ ] After `git merge-base --is-ancestor fix/bash-guard-hardening origin/dev`: delete the branch on both remotes and locally; `git worktree remove ../nifty-bash-guard-hardening`.

## Handoff Log
