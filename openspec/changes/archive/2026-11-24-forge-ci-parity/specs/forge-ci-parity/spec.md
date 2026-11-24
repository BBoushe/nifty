## ADDED Requirements

### Requirement: REQ-CP-01 GitHub builds the same images as GitLab
**WHEN** a pull request targets `dev`, `alpha`, `main` or `master` on GitHub, **THE SYSTEM SHALL** build the backend image and the production frontend image with the same `docker build` commands used by `.gitlab-ci.yml`.

#### Scenario: Broken Dockerfile
- **WHEN** a pull request breaks `frontend/Dockerfile`
- **THEN** the GitHub check `Frontend Image (Docker)` fails before merge.

### Requirement: REQ-CP-02 Required checks
**WHERE** a GitHub ruleset protects `dev`, `alpha` or `main`/`master`, **THE SYSTEM SHALL** require the `Backend Image (Docker)`, `Frontend Image (Docker)` and `Hook Tests (Node)` checks to pass before merge.

#### Scenario: Red hook tests
- **WHEN** `Hook Tests (Node)` fails on a pull request into `dev`
- **THEN** GitHub does not allow the merge.

### Requirement: REQ-CP-03 Drift test
**IF** the set of build, test and image commands in `.github/workflows/ci.yml` differs from `.gitlab-ci.yml`, **THEN** `node --test scripts/harness-scripts.test.mjs` **SHALL** fail and name the missing command.

#### Scenario: Job added to one forge only
- **WHEN** a `docker build` line exists only in `.gitlab-ci.yml`
- **THEN** the drift test fails and its message contains that normalised command.

### Requirement: REQ-GP-01 GitLab branch protection
**WHERE** the GitLab mirror is in use, **THE SYSTEM SHALL** protect `dev`, `alpha`, `master` and `main` against force pushes and non-Maintainer pushes.

#### Scenario: Developer force-pushes dev
- **WHEN** a user with Developer role force-pushes GitLab `dev`
- **THEN** GitLab rejects the push.

### Requirement: REQ-GP-02 GitLab merges need a green pipeline
**WHEN** a merge request is merged on GitLab, **THE SYSTEM SHALL** require a successful pipeline.

#### Scenario: Project setting
- **WHEN** `glab api projects/6748` is read
- **THEN** `only_allow_merge_if_pipeline_succeeds` is `true`.
