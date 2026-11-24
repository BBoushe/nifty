## ADDED Requirements

### Requirement: REQ-DF-01 Dual forge switch
**THE SYSTEM SHALL** accept `GIT_FORGE=github|gitlab|both`, treat remote `origin` as GitHub (primary) and remote `gitlab` as GitLab (secondary), and fall back to `github` for unknown values.

#### Scenario: Both forges are resolved and preflighted
- **WHEN** `GIT_FORGE=both` and `node scripts/check-setup.mjs` runs
- **THEN** `--forge` prints `CLI=gh,glab`
- **AND** the preflight checks `gh`, `glab`, and the `origin` and `gitlab` remotes without printing any `.env` secret.

### Requirement: REQ-DF-02 Push and PR/MR on both forges
**WHEN** `GIT_FORGE=both`, **THE SYSTEM SHALL** push to GitHub first and to GitLab only if GitHub succeeded, and open a PR and an MR with identical title and body, each with an explicit repository so `gh`/`glab` never guess the host.

#### Scenario: GitHub push fails
- **WHEN** `node scripts/forge.mjs push` cannot push to `origin`
- **THEN** nothing is pushed to `gitlab` and the exit code is non-zero.

#### Scenario: One forge fails to open its PR/MR
- **WHEN** `node scripts/forge.mjs pr` fails on one forge
- **THEN** the other forge is still attempted and the exit code is non-zero.

### Requirement: REQ-DF-03 Drift prevention
**THE SYSTEM SHALL** provide `sync` to fast-forward GitLab `dev`, `alpha`, and `master` to `origin` without `--force`, and `status` to report ahead/behind per branch.

#### Scenario: GitLab diverged
- **WHEN** GitLab has commits that GitHub lacks
- **THEN** `sync` is rejected by git instead of overwriting, and `status` exits non-zero.

### Requirement: REQ-DF-04 GitLab branch protection parity
**THE SYSTEM SHALL** provide `scripts/apply-gitlab-protections.sh` that protects `master`, `main`, `alpha`, and `dev` (Maintainer push/merge, no force push), requires passing pipelines, and sets approval rules (4 for `master`/`main`, 2 for `alpha`) where the GitLab tier supports them.

#### Scenario: Dry run
- **WHEN** the script runs with `--dry-run`
- **THEN** it prints every `glab api` call and changes nothing.
