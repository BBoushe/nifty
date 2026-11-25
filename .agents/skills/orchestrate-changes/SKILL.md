---
name: orchestrate-changes
description: Run one or more OpenSpec changes from a single orchestrator session. A builder agent per change works in its own ../nifty-<change> worktree. A fresh cold reviewer checks each PR. The orchestrator handles PRs/MRs by branch name and the merge order. It asks the user before every merge, with a short architecture summary (Decision 3.11).
---

# Skill: Orchestrate Changes (one session, many agents)

Use this skill when the user wants several changes (or one large change) delivered without opening one terminal per change. The user talks only to you. Agents never talk to the user, never merge and never push to `dev`.

## 1. Plan

1. Read each change's `tasks.md` (`## Status`, owned files) and the board or backlog that lists them.
2. Check file ownership. Two changes that edit the same file merge one after the other. Plan that order now. Shared files (`CHANGELOG.md`, `docs/feature_backlog.md`, test files) get "append, keep both sides" on merge.
3. Tell the user the plan in a few lines: changes, worktrees, merge order. Ask only if a choice is genuinely theirs.

## 2. Worktrees

From the main checkout, for each change:

```sh
git fetch origin
git worktree add ../nifty-<change> -b <branch> origin/dev
```

No `.env` copy is needed. `forge.mjs` reads the main checkout's `.env` from a worktree (REQ-OR-01). Worktrees live only at `/Users/<you>/…/nifty-<change>` next to the main checkout. Never use the Agent tool's own worktree isolation, because it creates folders outside that pattern.

## 3. Builder agents

Start one background agent per change. Pick the model from the user's subagent policy (Sonnet for builders). Run agents in parallel only when their files do not overlap. The prompt must contain:

- **Working directory:** "Every Bash command starts with `cd <absolute worktree> && pwd && …`. If pwd differs, stop and report. Never touch the main checkout."
- **Task:** the change path, which tasks to do, `spec-implementer` + the Build Loop in `.claude/agents/spec-builder.md` (RED → GREEN, Handoff line).
- **Limits:** do not merge, do not push to `dev`, do not ask the user. Commit per task is allowed. Pushing the feature branch is the orchestrator's job unless you say otherwise.
- **Guard notes:** change files with Read/Edit/Write, not shell heredocs. A command whose text names the secret env file is blocked. Use a script file. If a call is blocked after an `rtk` rewrite, re-issue the rewritten command exactly.
- **Report:** ≤ 25 lines: what changed, verification commands with exit codes, anything uncertain (quote old and new for doc changes).

## 4. After each report

1. Read the diff yourself (`git -C <worktree> diff`). Check the report against it. Fix small problems or send them back.
2. Commit if the builder did not, tick the task, write the Handoff entry, overwrite `## Status`.
3. When all tasks are done, merge `origin/dev` into the branch. Never rebase: a rebase needs a force push, which the guard blocks. Re-run the tests, archive the change and add the `docs/feature_history.md` entry.
4. Push and open the PR/MR from the worktree: `node scripts/forge.mjs push`, then the `pr-memo` skill and `node scripts/forge.mjs pr --title … --body-file …`.

## 5. Review

Start a fresh `reviewer` agent per PR. Give it the worktree path and pwd rule, the diff range (`origin/dev...HEAD`) and the change folder. Also give it what earlier rounds found, what counts as Blocking, and the accepted limits. Send Blocking items to a builder (or fix them yourself), push, and review again. When rounds only find narrower edge cases, say so to the user instead of looping.

## 6. Track

`node scripts/forge.mjs prs` shows every open branch with its GitHub PR, checks, GitLab MR and pipeline. Refer to changes by name, not by `#n` / `!m`.

## 7. Merge (ask every time)

Merge in the planned order. Before each merge, ask the user (AskUserQuestion when available) and start with:

```text
<change name> is ready.
Architecture: <3–5 lines: the structural decisions this change makes and why — layers, boundaries,
  data flow, new dependencies, rules added or relaxed. Not file-level details.>
Review: <verdict, rounds, open Convention Candidates>
CI: GitHub <n/n pass>, GitLab <pipeline status> on <short sha>
Merge into dev now?
```

After a yes, run `gh pr merge <branch> --merge`, `node scripts/forge.mjs sync dev` and `node scripts/forge.mjs status` (expect 0/0). Then merge `origin/dev` into the next branch in the order. Clean up only after `git merge-base --is-ancestor <branch> origin/dev`: delete the branch on both remotes and locally, `git worktree remove ../nifty-<change>`.

## 8. Resume

If the session stops, every change resumes from its `tasks.md` `## Status` (active or archived path) and `git log origin/dev..<branch>`. Other tools follow the same steps by hand, one session per change (`docs/workflow.md` §4).
