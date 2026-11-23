# Proposal: Dual Forge (`GIT_FORGE=both`)

## Context & Problem

The project started on GitHub (`gh`, primary) but the course requires the same work on the university GitLab (`glab`, `git.inf.fh-dortmund.de/01/alpac001/nifty`). Pushes, PRs/MRs, and branch protections must exist on both, without the two repositories drifting apart (Decision 1.8).

## Scope

- `GIT_FORGE=both` in `scripts/check-setup.mjs` (both CLIs + `origin`/`gitlab` remotes checked).
- `scripts/forge.mjs`: `push`, `pr`, `sync`, `status` (all `--dry-run`-able; GitHub first; no force pushes).
- `scripts/apply-gitlab-protections.sh`: GitLab equivalent of `.github/rulesets/`.
- `pr-memo` skill, `AGENTS.md`, `README.md`, `docs/setup.md`, `docs/workflow.md`, `.env.example`, `CHANGELOG.md`.

## Out of Scope

- Automatic mirroring (GitLab push mirror / GitHub Action): depends on GitLab tier and network access; `sync` is manual.
- Changing `.gitlab-ci.yml` or `.github/workflows/ci.yml`.
- Granting new tool permissions in `.claude/settings.json` (left to the user).
