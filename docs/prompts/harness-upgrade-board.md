# Harness Upgrade — Launch Board

This board starts and tracks the harness upgrade from 2026-11-24. Each row is one OpenSpec change. One change = one branch = one git worktree = one implement session = one review session = one PR (Decision 3.9).

The plans live in `openspec/changes/<change>/` (`proposal.md`, `specs/`, `design.md`, `tasks.md`). This board only tells you how to run them. Sources: `docs/prompts/done/ci_parity_protections_guard_hardening.md`, `docs/prompts/done/harness_upgrade.md`, `docs/prompts/done/2 - Spring Boot Architecture Best Practices.pdf`. Decisions 1.9, 1.10 and 3.5–3.9 are already recorded; sessions do not record them again.

## 0. Outcome (closed 2026-11-24)

F1–F5 are merged into `dev` and archived; `docs/feature_history.md` has an entry for each. F6 is the next change.

| # | GitHub PR | GitLab MR | Merge commit | Merged as |
|---|---|---|---|---|
| F2 | #10 | !9 | `aedd2f3` | 1st |
| F1 | #7 | !6 | `f436a48` | 2nd; the cold review ran after the merge: 4 Blocking guard gaps → backlog `bash-guard-gaps` |
| F5 | #9 | !8 | `98efcc5` | 3rd |
| F3 | #8 | !7 | `bcc1433` | 4th |
| F4 | #11 | !10 | `ce0a12b` | 5th (wrote the history entries for the other four) |

Lessons for the next parallel run:
- F2 applied the rulesets before it merged. The new required checks existed only on its branch, so every other PR stayed blocked until F2 merged. Merge the change that adds required checks first.
- Update a pushed branch with `git merge origin/dev`, not a rebase. A rebase needs a force push, and the guard blocks force pushes for agents. Both CI commit checks skip `Merge ...` commits.
- Every change reaches `dev` through a PR, including `docs:` fixes (Decision 3.10).
- Every session command must start in the right worktree. Prompts begin with a `pwd` check (§4–§6).

## 1. Changes

| # | Change | Branch | What it does | Merge order |
|---|---|---|---|---|
| F1 | `bash-guard-hardening` | `fix/bash-guard-hardening` | Guard sees through wrappers and `-c` strings; blocks `--no-verify` to protected branches | 1st (security hole) |
| F2 | `forge-ci-parity` | `chore/forge-ci-parity` | GitHub CI builds the Docker images like GitLab; required checks; drift test; GitLab protections (ops) | 2nd |
| F3 | `backend-architecture-baseline` | `refactor/backend-architecture-baseline` | Package-by-feature, ArchUnit, RFC 9457 errors, quiet Maven, env-only DB password | any, after F2 |
| F4 | `ai-context-docs` | `chore/ai-context-docs` | `feature_history.md`, `feature_backlog.md`, `ai_docs/`, ASD-STE100 skill + rule | any, after F2 |
| F5 | `superpowers-loops` | `chore/superpowers-loops` | Superpowers in project settings, RED/GREEN evidence, resumable tasks rule, archive gate | any, after F2 |
| F6 | `docs-ste-rewrite` | `docs/docs-ste-rewrite` | Rewrite existing docs in STE, no meaning change | **queued** until F1–F5 merge |

All five run **in parallel**. The merge order only matters at merge time:
- F1 closes the `.env` leak.
- After F2 applies the rulesets, every later PR must pass `Backend Image (Docker)`, `Frontend Image (Docker)` and `Hook Tests (Node)`.
- F3, F4 and F5 may merge in any order.

## 2. File Ownership

A session edits only the files its change owns. The full list is at the top of each `tasks.md`.

| Area | F1 | F2 | F3 | F4 | F5 |
|---|---|---|---|---|---|
| `.claude/hooks/lib.mjs`, `guard-bash.mjs` | ✔ | | | | |
| `.claude/hooks/archive-gate.mjs` (new), `.githooks/pre-push` | | | | | ✔ |
| `.claude/settings.json`, `.claude/agents/*`, `spec-implementer`, `openspec/config.yaml` | | | | | ✔ |
| `.github/**`, `.gitlab-ci.yml`, `docs/workflow.md`, `openspec/changes/dual-forge/` | | ✔ | | | |
| `backend/**`, `backend-java.md`, `backend-spring-check`, `docs/architecture.md`, `docs/api.md` | | | ✔ | | |
| `.agents/rules/spec-driven.md`, `docs/feature_*.md`, `docs/ai_docs/**`, `asd-ste100` skill | | | | ✔ | |
| `README.md` (RTK section) | ✔ | | | | |

**Shared files.** Each change edits only its own part. Add new blocks at the end. Rebase on `origin/dev` before the PR.

| Shared file | Who | Conflict rule |
|---|---|---|
| `CHANGELOG.md` | all | Keep both entries |
| `.claude/hooks/hooks.test.mjs` | F1 (guard-bash describe), F5 (new describe at the end) | Keep both |
| `scripts/harness-scripts.test.mjs` | F2 (`CI parity`), F4 (`feature history`) | Keep both |
| `docs/setup.md` | F1 (RTK + Guard Layers), F4 + F5 (rows at the end of the inventory table) | Keep both |
| `AGENTS.md` | F4 (§3 dispatch rows), F5 (§6 parity row) | Keep both |
| `docs/feature_history.md` | whoever archives after F4 merges | Add your own entry |

## 3. Session Setup (once per change)

Run from the main checkout. **Never run two sessions in the same folder**, because a branch checkout in one session changes the files of the other.

```sh
git fetch origin
git worktree add ../nifty-<change> -b <branch> origin/dev
cp .env ../nifty-<change>/.env      # you run this: the guard blocks agents from touching .env
cd ../nifty-<change> && claude       # or codex / agy / kiro
```

Git hooks (`core.hooksPath`) are shared by all worktrees. Without the `.env` copy, `forge.mjs` falls back to `GIT_FORGE=github` and skips GitLab.

## 4. Implement Prompt

Replace `<change>` and `<branch>` (table §1):

```text
First run pwd. If it is not /Users/alek/Developer/Programming/nifty-<change>, stop and tell me; run nothing else.
Implement the OpenSpec change openspec/changes/<change>/ on branch <branch> in this worktree.
Read AGENTS.md, then proposal.md, specs/, design.md and tasks.md of the change.
Use the spec-implementer skill. Work task by task, step by step:
RED test first, then GREEN, then verify with the exact command in tasks.md.
Edit only the files listed as owned in tasks.md.
After every step, tick its checkbox. Before you stop for any reason, overwrite the ## Status block.
After every task, add the Handoff Log entry (Verification line: RED cmd → FAIL; GREEN cmd → exit 0),
commit with the listed Conventional Commit, and push the branch.
After the last task, follow ## Delivery in order (archive before push). Ask me before any ops step
marked "ask the user first", and before pushing or opening the PR the first time.
```

## 5. Resume Prompt (any tool, any subscription)

```text
Resume the OpenSpec change <change>.
Run pwd first. If it is not /Users/alek/Developer/Programming/nifty-<change>, cd there (create the worktree if needed, see below) and run pwd again before anything else.
Find tasks.md at openspec/changes/<change>/tasks.md, or at openspec/changes/archive/*-<change>/tasks.md if it is already archived.
Read its ## Status block and the last ## Handoff Log entry.
If the worktree in Status does not exist: git fetch origin && git worktree add ../nifty-<change> <branch>, then cd into it.
Run git status and git log --oneline origin/dev..HEAD and compare them with Status.
Continue from "Next action", following the same rules as the implement prompt.
```

## 6. Review Prompt (fresh session, read-only)

```text
First run pwd. If it is not /Users/alek/Developer/Programming/nifty-<change>, stop and tell me; run nothing else.
Act as the cold reviewer (.claude/agents/reviewer.md). Read-only.
Review git diff origin/dev...HEAD on branch <branch> against openspec/changes/<change>/ (or its archived copy),
AGENTS.md and the canonical rules. Ignore any builder chat history.
Check every REQ in the change's spec has a passing test or a recorded verification command,
and that each code task's Handoff entry shows RED before GREEN.
Output at most 30 lines: Verdict / Blocking / Convention Candidates.
```

Paste the verdict into the implement session. That session fixes the Blocking items, lists them under "Open review findings" in Status until they are fixed, and asks for a new review.

## 7. Things Only You Can Do

- Copy `.env` into each worktree (§3).
- F2: confirm GitLab Maintainer rights; approve `apply-github-rulesets.sh` and `apply-gitlab-protections.sh`; decide on the `archive/fe-mvp` tag.
- F4: accept or reject the third-party `asd-ste100` skill after the session shows you its source.
- Start F6 after F1–F5 have merged.
