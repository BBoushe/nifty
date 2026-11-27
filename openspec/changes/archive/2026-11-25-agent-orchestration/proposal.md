# Proposal: Orchestrated Multi-Agent Delivery

## Context & Problem

The 2026-11-24 harness upgrade ran five changes in five terminal sessions. The user had to answer each session and copy `.env` into each worktree. The user also had to match GitHub PR numbers to different GitLab MR numbers (`#9` is `!8`, `#13` is `!12`). The F6 rewrite on 2026-11-25 ran as subagents from one session instead, and that worked: the user answered questions in one place only.

Two tool gaps remain:

- `node scripts/forge.mjs` reads `GIT_FORGE` from the `.env` of the current folder. A worktree has no `.env`, so `forge.mjs` falls back to GitHub only, and the F6 push had to be done by hand.
- No command shows a branch's GitHub PR and GitLab MR side by side with their CI state.

Decision 3.11 sets the model. One orchestrator session runs a builder agent per change and a cold reviewer per PR. It handles PRs/MRs by branch name, and it asks the user before every merge, with an architecture summary.

## Scope

- `resolveForgeConfig` falls back to the main checkout's `.env` when the folder is a linked worktree without its own `.env`.
- `node scripts/forge.mjs prs`: one row per open branch with the GitHub PR, its checks, the GitLab MR and its pipeline.
- `orchestrate-changes` skill (`.agents/skills/`, linked from `.claude/skills/`): the orchestrator procedure.
- `AGENTS.md` dispatch row, `docs/workflow.md` section, `docs/setup.md` inventory rows, CHANGELOG.

## Out of Scope

- Automatic merges. The user approves every merge (Decision 3.11).
- Copying `.env` into worktrees, or any other `.env` access by agents.
- Changes to the guard hooks.
