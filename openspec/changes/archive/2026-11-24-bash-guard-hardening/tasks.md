# Tasks: Bash Guard Hardening

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `fix/bash-guard-hardening`
- **Worktree:** `../nifty-bash-guard-hardening`
- **Current:** Tasks 1-5 done; rebased (no-op, dev at 579e3d2), tests exit 0, archived
- **Next action:** Delivery: `node scripts/forge.mjs push`, then PR/MR via `pr-memo` (ask the user first)
- **Uncommitted changes:** no
- **PR / MR:** —
- **Open review findings:** —

## Tasks

Owned files: `.claude/hooks/lib.mjs`, `.claude/hooks/guard-bash.mjs`, the `lib`/`guard-bash` describe blocks in `.claude/hooks/hooks.test.mjs`, the README "Optional: RTK" section, and the RTK paragraphs + "Guard Layers" in `docs/setup.md`. Read `design.md` first.

- [x] **1. Must-block and must-allow tests (RED)** — REQ-BG-01..05
  - [x] In the `guard-bash` describe of `hooks.test.mjs`, add `it('blocks guarded commands hidden behind wrappers and command strings')`. Use `cleanOpts` (`currentBranch: 'feat/x'`) and assert `blocked === true` for every row of the proposal table, plus `bash -lc "git push origin master"`, `sudo -u x git push origin master`, `nice -n 5 git push origin master`, `env -S "git push origin master"`, and four nested `sh -c` levels around `echo hi`.
  - [x] Add `it('allows harmless commands behind wrappers')`: assert `blocked === false` for `rtk git status`, `rtk err npm run build`, `rtk test ./mvnw -B test`, `sh -c "npm run lint"`, `time ./mvnw -B test`, `git push origin feat/x`, `git push --no-verify origin feat/x`, `git push -n origin dev`, and `git commit -m "docs: mention sh -c and .env"`.
  - [x] Run `node --test .claude/hooks/hooks.test.mjs`. Expect: the new must-block test FAILS; all existing tests pass.
  - [x] Commit `test(hooks): add wrapper and command-string guard probes`.
- [x] **2. Wrapper peeling (GREEN part 1)** — REQ-BG-01
  - [x] Extend `stripLeadingEnvAssignments` per the design §1 table.
  - [x] Run `node --test .claude/hooks/hooks.test.mjs`. Expect: the wrapper rows pass; only the command-string and `--no-verify` rows still fail; the `guard-agent` tests stay green.
  - [x] Commit `fix(hooks): peel nice, time, sudo, xargs, env -u and rtk run wrappers`.
- [x] **3. Command-string recursion with a cap** — REQ-BG-02, REQ-BG-03
  - [x] Implement design §2 in `guard-bash.mjs`.
  - [x] Run the tests. Expect: the `-c`, `eval` and depth rows pass.
  - [x] Commit `fix(hooks): evaluate sh -c, rtk run -c, env -S and eval strings`.
- [x] **4. Block `--no-verify` pushes to protected targets** — REQ-BG-04
  - [x] Implement design §3.
  - [x] Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
  - [x] Commit `fix(hooks): block git push --no-verify to protected branches`.
- [x] **5. Comment and docs**
  - [x] Replace the wrong `shortcut:` comment in `lib.mjs` with design §4.
  - [x] Update `README.md` (Optional: RTK) and `docs/setup.md` per design §5, including the "Guard Layers" subsection.
  - [x] Add a `CHANGELOG.md` `[Unreleased]` entry.
  - [x] Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
  - [x] Commit `docs(setup): document guard layers and RTK wrapper limits`.

## Delivery

- [x] `git fetch origin && git rebase origin/dev`. On conflict in `CHANGELOG.md`, keep both entries; in shared test files, keep both describe blocks.
- [x] Re-run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive bash-guard-hardening -y --skip-specs` (harness change; continue in `openspec/changes/archive/*-bash-guard-hardening/tasks.md`). Commit `chore(specs): archive bash-guard-hardening`.
- [x] If `docs/feature_history.md` exists after the rebase, add this change's entry (format in its header; the harness test enforces it) and amend it into the archive commit. (n/a: file absent after rebase)
- [ ] `node scripts/forge.mjs push`
- [ ] PR/MR body via the `pr-memo` skill, then `node scripts/forge.mjs pr --title "fix(hooks): harden bash guard against wrappers and command strings" --body-file <file>`. Record the links in Status.
- [ ] Cold review session (board review prompt): verdict has no Blocking items.
- [ ] GitLab MR pipeline green; `gh pr checks <n>` green.
- [ ] Merge on GitHub only: `gh pr merge <n> --merge`.
- [ ] `node scripts/forge.mjs sync dev`, then `node scripts/forge.mjs status` prints 0/0 for every branch.
- [ ] After `git merge-base --is-ancestor fix/bash-guard-hardening origin/dev`: delete the branch on both remotes and locally; `git worktree remove ../nifty-bash-guard-hardening`.

## Handoff Log

### Task 1 — RED probes
- Commits: `test(hooks): add wrapper and command-string guard probes`
- Verification: RED `node --test .claude/hooks/hooks.test.mjs` → FAIL (16/17, only the must-block test); GREEN n/a (RED-only task)
- Contracts/Migrations added: none
- Decisions: `bash -c "git commit -m x"` is probed on `master`, because a plain commit on `feat/x` is allowed when run directly (REQ-BG-01 equivalence); probes pass `pushCommitSubjects: ['docs: x']` so dev pushes are deterministic.
- Open Questions: none

### Task 2 — Wrapper peeling
- Commits: `fix(hooks): peel nice, time, sudo, xargs, env -u and rtk run wrappers`
- Verification: RED `node --test .claude/hooks/hooks.test.mjs` → FAIL (must-block test; only `-c`/`eval`/`env -S`/`--no-verify` rows still allowed, probed per row); GREEN n/a until Task 3/4; guard-agent tests stay green
- Contracts/Migrations added: none
- Decisions: peeling stops on `env -S|--split-string` and `rtk run -c|--command`, leaving the flag first for guard-bash (design §1-§2); unknown `-…` flags after `rtk run` are skipped like other wrappers.
- Open Questions: none

### Task 3 — Command-string recursion
- Commits: `fix(hooks): evaluate sh -c, rtk run -c, env -S and eval strings`
- Verification: RED `node --test .claude/hooks/hooks.test.mjs` → FAIL (only the `git push --no-verify origin dev` row left); `-c`/`eval`/`env -S`/depth rows probed → blocked, 3 levels allowed, 4 levels blocked with `depth 3` reason
- Contracts/Migrations added: none
- Decisions: inner evaluation reuses the outer `currentBranch` so nested strings never re-query git.
- Open Questions: none

### Task 4 — `--no-verify` push block
- Commits: `fix(hooks): block git push --no-verify to protected branches`
- Verification: RED `node --test .claude/hooks/hooks.test.mjs` → FAIL (`git push --no-verify origin dev` row); GREEN `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 (29/29)
- Contracts/Migrations added: none
- Decisions: `-n` stays allowed (`--dry-run`), per proposal deviation.
- Open Questions: none

### Task 5 — Comment and docs
- Commits: `docs(setup): document guard layers and RTK wrapper limits`
- Verification: RED n/a (docs/comment only); GREEN `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 (29/29)
- Contracts/Migrations added: none
- Decisions: "Guard Layers" sits under "For Teammates" in `docs/setup.md`; RTK 1.2.1 confirmed locally (`rtk --version`).
- Open Questions: none
