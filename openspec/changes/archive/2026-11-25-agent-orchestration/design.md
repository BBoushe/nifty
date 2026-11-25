# Design: Orchestrated Multi-Agent Delivery

## 1. `.env` lookup in worktrees (REQ-OR-01)

`scripts/check-setup.mjs` gets `findEnvPath(absRoot) → string`:

1. If `<absRoot>/.env` exists, return it.
2. Else run `git -C <absRoot> rev-parse --path-format=absolute --git-common-dir`. For a linked worktree this is the main checkout's `.git`. If `<dirname of it>/.env` exists, return that.
3. Else return `<absRoot>/.env` (missing, as today).

`resolveForgeConfig` uses it. The `.env` content still goes only through `parseSafeEnvContent`, so no secret is exposed. Agents never read `.env` themselves.

## 2. `forge.mjs prs` (REQ-OR-02)

Pure, tested helpers in `scripts/forge.mjs`:

- `checksSummary(states: string[]) → string`: `"7/7 pass"` when all pass, `"<n> pending"` when any is pending, `"<n> failed"` when any failed (failed wins). Empty → `"no checks"`.
- `prRows(github: {branch, number, checks}[], gitlab: {branch, iid, pipeline}[]) → {branch, pr, checks, mr, pipeline}[]`: one row per branch from either side, sorted by branch, `—` for a missing side.

The command reads `gh pr list --state open --json number,headRefName,statusCheckRollup`. For GitLab it runs `glab mr list -R <gitlab url> -F json` and one `glab mr view <iid> -R <gitlab url> -F json` per MR for `head_pipeline.status`. `-R` lets glab resolve the project, because the FH instance serves from a sub-path (`/01/…`) and a hand-built API path returns 404. It prints a fixed-width table.

## 3. `orchestrate-changes` skill (REQ-OR-03, REQ-OR-04)

`.agents/skills/orchestrate-changes/SKILL.md` with a `.claude/skills/` symlink (house pattern). Sections:

- **Setup:** per change, `git worktree add ../nifty-<change> -b <branch> origin/dev` from the main checkout. No `.env` copy needed (§1).
- **Builder agents:** one background agent per change. The prompt names the worktree, starts every command with `cd <worktree> && pwd`, follows `spec-implementer` and the change's `tasks.md`, forbids merge/push to `dev`, and asks for a ≤25-line report. Edits that contain secret-file names or heredocs go through Read/Edit/Write or a script file.
- **Orchestrator duties:** check each report against the diff, commit when the builder did not, update `## Status`, push with `forge.mjs push` from the worktree, open PR/MR with `pr-memo` + `forge.mjs pr`, track with `forge.mjs prs`.
- **Review:** a fresh `reviewer` agent per PR. Blocking items go back to a builder. Repeat until clean.
- **Merge:** follow the merge order. Merge `origin/dev` into the next branch (never rebase). Before every merge, ask the user with the summary from REQ-OR-04. Then `gh pr merge --merge`, `forge.mjs sync dev`, `forge.mjs status`, branch and worktree cleanup.
- **Other tools:** the same steps by hand, one session per change (the 2026-11-24 board).
