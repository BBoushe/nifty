## ADDED Requirements

### Requirement: REQ-OR-01 Forge config in worktrees
**WHEN** `resolveForgeConfig` runs in a linked git worktree that has no `.env` of its own, **THE SYSTEM SHALL** read the forge settings from the `.env` of the main checkout of the same repository.

#### Scenario: Worktree without .env
- **WHEN** the main checkout's `.env` sets `GIT_FORGE=both` and `forge.mjs` runs in `../nifty-<change>` with no `.env`
- **THEN** the resolved forge is `both`.

#### Scenario: Worktree with its own .env
- **WHEN** the worktree has its own `.env` with `GIT_FORGE=github`
- **THEN** the resolved forge is `github`.

### Requirement: REQ-OR-02 One view of PRs and MRs
**WHEN** the user or an agent runs `node scripts/forge.mjs prs` with `GIT_FORGE=both`, **THE SYSTEM SHALL** print one row per open branch with the GitHub PR number, its check summary, the GitLab MR number and its pipeline status.

#### Scenario: Branch open on both forges
- **WHEN** branch `fix/x` has PR #13 with 7 passing checks and MR !12 with a successful pipeline
- **THEN** the row for `fix/x` shows `#13`, `7/7 pass`, `!12` and `success`.

#### Scenario: Branch open on one forge only
- **WHEN** a branch has a GitHub PR but no GitLab MR
- **THEN** its GitLab columns show `—`.

### Requirement: REQ-OR-03 Orchestrator procedure
**WHERE** several changes run in parallel, **THE SYSTEM SHALL** provide the `orchestrate-changes` skill. It tells the orchestrator to run one builder agent per change in `../nifty-<change>` (every command starts with `cd <worktree> && pwd`), one fresh cold reviewer per PR, PR/MR handling by branch name, and the merge order.

#### Scenario: Agents never merge
- **WHEN** a builder or reviewer agent finishes
- **THEN** it reports to the orchestrator and does not merge, push to `dev`, or ask the user directly.

### Requirement: REQ-OR-04 Merge approval with an architecture summary
**WHEN** a change is ready to merge, **THE SYSTEM SHALL** ask the user first, and the question **SHALL** start with a short summary of the change's high-level architecture decisions, the review verdict and the CI state on both forges.

#### Scenario: Ready change
- **WHEN** the cold review has no Blocking items and both forges are green
- **THEN** the orchestrator shows the summary and waits for the user's answer before `gh pr merge`.
