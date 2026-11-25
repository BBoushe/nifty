# Spec Delta: Team Builder (`team-builder`)

## ADDED Requirements

### Requirement: REQ-TB-08 Validation of Run-Time Grouping Parameters
**IF** a team generation request specifies `numTeams < 1`, `numTeams > candidateCount`, `alpha` or `combinedHardWeight` outside `0..1`, negative or all-zero custom weights, or `SOFT_SKILLS` mode when `bfi10Enabled = false`, **THEN THE SYSTEM SHALL** reject it with `400` RFC 9457 `ProblemDetail` naming each invalid field and persist no proposal (Decisions 1.19, 1.20).

#### Scenario: Invalid team count rejected
- **WHEN** a professor requests `numTeams = 20` for a cohort of `12` candidates
- **THEN** the backend answers `400` ProblemDetail naming `numTeams` and creates no proposal.
