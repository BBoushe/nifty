# Spec Delta: Team Builder (`team-builder`)

## ADDED Requirements

### Requirement: REQ-TB-08 Validation of Run-Time Grouping Parameters
**IF** a team generation request specifies `numTeams < 1`, `numTeams > candidateCount`, `alpha` or `combinedHardWeight` outside `0..1`, negative or all-zero custom weights, or `SOFT_SKILLS` or `COMBINED` mode when `bfi10Enabled = false` (Decision 2.27), **THEN THE SYSTEM SHALL** reject it with `400` RFC 9457 `ProblemDetail` naming each invalid field and persist no proposal (Decisions 1.19, 1.20).

#### Scenario: Invalid team count rejected
- **WHEN** a professor requests `numTeams = 20` for a cohort of `12` candidates
- **THEN** the backend answers `400` ProblemDetail naming `numTeams` and creates no proposal.

## MODIFIED Requirements

### Requirement: REQ-TB-06 BFI-10 Balance Soft Constraint
**WHERE** the grouping mode is `SOFT_SKILLS` or `COMBINED` **THE SYSTEM SHALL** include the trait means in the inter-team imbalance term. The optimizer thus keeps the mean trait profile of each team close to the cohort mean. Both modes always have BFI-10 on, because studio rejects them without it (REQ-IS-13). The soft preset sets the trait weights (Decisions 1.20, 2.27, 4.3).

#### Scenario: Introverts spread across teams
- **WHEN** 4 of 20 candidates have Extraversion below 2.0, BFI-10 is on and the grouping mode is `COMBINED`
- **THEN** the optimizer prefers assignments that do not place all 4 in the same team, and the team diagnostics show the trait balance.

#### Scenario: BFI-10 off
- **WHEN** the assessment has BFI-10 off, which only grouping mode `HARD_SKILLS` allows (REQ-IS-13)
- **THEN** the optimizer uses no trait term.
