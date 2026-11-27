# Domain Spec: Team Builder (`team-builder`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-08, §9 step 8–9, §12 D7 & §14), updated 2026-11-25 by `nov24-reconciliation` and `five-service-convergence`
> **Primary View:** Ranking View (`PROFESSOR`), backend slice `teams`

## Purpose

Form balanced, complementary, and defensible student teams. A seeded pure-Java Simulated Annealing optimizer forms the teams (Decision 1.20). The team builder gives live drag-and-drop recalculation and team diagnostics. Professor approval is mandatory.

## Requirements

### Requirement: REQ-TB-03 Live Deterministic Drag-and-Drop Recalculation
**WHEN** a professor drags a candidate to another team (`POST /api/v1/ranking/teams/{proposalId}/recalculate`) **THE SYSTEM SHALL** recalculate inter-team imbalance, intra-team diversity, the skill means and the trait means of every team in Java immediately, without invoking an LLM. The request holds the complete placement of every candidate of the proposal. The team sizes may differ from the optimizer result.

#### Scenario: Manual team adjustment updates balance indicators
- **WHEN** a candidate is moved from Team A to Team B in the Team Builder UI
- **THEN** skill coverage and balance indicators for both teams recalculate deterministically in Java with zero LLM calls.

### Requirement: REQ-TB-04 Mandatory Human Approval Before Publishing Teams
**THE SYSTEM SHALL** keep a generated team proposal in status `DRAFT` until the professor publishes it (`POST /api/v1/ranking/teams/{proposalId}/publish`). Publishing sets status `PUBLISHED` and writes one `audit_log` row through `AuditService`.

#### Scenario: Solver finishes computing candidate teams
- **WHEN** the optimizer returns a team assignment
- **THEN** the proposal stays `DRAFT` until the professor explicitly publishes it.

### Requirement: REQ-TB-05 Configured Team Size
**THE SYSTEM SHALL** form `G = ceil(N / teamSize)` teams from the team size in the assessment configuration, unless the professor sets another team count for the run. **THE SYSTEM SHALL** place every candidate in exactly one team. **WHEN** the optimizer forms the teams, **THE SYSTEM SHALL** give every team `floor(N / G)` or `ceil(N / G)` members (Decisions 1.20, 2.23). A manual recalculation (REQ-TB-03) accepts any complete placement, including uneven or empty teams.

#### Scenario: 22 candidates, team size 5
- **WHEN** the professor runs the optimizer for 22 candidates with team size 5
- **THEN** the system forms 5 teams with sizes 5, 5, 4, 4 and 4, and every candidate is in exactly one team.

### Requirement: REQ-TB-06 BFI-10 Balance Soft Constraint
**WHERE** the grouping mode is `SOFT_SKILLS` or `COMBINED` **THE SYSTEM SHALL** include the trait means in the inter-team imbalance term. The optimizer thus keeps the mean trait profile of each team close to the cohort mean. Both modes always have BFI-10 on, because studio rejects them without it (REQ-IS-13). The soft preset sets the trait weights (Decisions 1.20, 2.27, 4.3).

#### Scenario: Introverts spread across teams
- **WHEN** 4 of 20 candidates have Extraversion below 2.0, BFI-10 is on and the grouping mode is `COMBINED`
- **THEN** the optimizer prefers assignments that do not place all 4 in the same team, and the team diagnostics show the trait balance.

#### Scenario: BFI-10 off
- **WHEN** the assessment has BFI-10 off, which only grouping mode `HARD_SKILLS` allows (REQ-IS-13)
- **THEN** the optimizer uses no trait term.

### Requirement: REQ-TB-07 Trait Scores Visible Only to the Professor
**THE SYSTEM SHALL** show individual trait scores in the team builder only to the `PROFESSOR`, and **SHALL NOT** use trait scores to change any skill score, fit or ranking. The team builder reads trait scores through `AssessmentQueryService` and **SHALL NOT** reverse any BFI-10 item again (REQ-AC-15).

#### Scenario: Professor inspects a team
- **WHEN** the professor opens a team card in the team builder
- **THEN** the card shows each member's five trait scores next to the skill balance.

### Requirement: REQ-TB-09 Seeded Simulated Annealing Optimizer
**WHEN** the professor runs team generation **THE SYSTEM SHALL** assign the candidates to teams with a pure-Java Simulated Annealing optimizer that uses 2-opt swaps and minimizes `alpha × inter_team_imbalance − (1 − alpha) × intra_team_diversity`, with zero LLM calls. The run uses a stored `seed`, so the same input and seed give the same teams (Decision 1.20).

#### Scenario: Same seed, same teams
- **WHEN** the optimizer runs twice on the same 22 candidates with team size 5 and `seed = 42`
- **THEN** both runs return the same teams and the same diagnostics.

### Requirement: REQ-TB-10 Grouping Modes
**THE SYSTEM SHALL** support the grouping modes `HARD_SKILLS`, `SOFT_SKILLS` and `COMBINED` (Decision 2.23). Code scales skill scores as `x / 4.0` and trait means as `(y − 1.0) / 4.0` to `0..1`. The soft presets are `ENGINEERING_DEFAULT` (`0.30, 0.25, 0.20, 0.15, 0.10`), `EQUAL_WEIGHTS` and `CUSTOM`. `COMBINED` mixes the hard and soft terms with `combinedHardWeight` and `1 − combinedHardWeight`.

#### Scenario: COMBINED mode
- **WHEN** the grouping mode is `COMBINED`, `combinedHardWeight = 0.6`, `alpha = 0.6` and the soft preset is `ENGINEERING_DEFAULT`
- **THEN** the optimizer scales both vectors to `0..1` and minimizes the mixed cost.

### Requirement: REQ-TB-11 Released Scores and Explicit N/A
**THE SYSTEM SHALL** form teams only from released scores (`auto_released`, `approved` or `overridden`) of each candidate's latest attempt (Decisions 2.10, 2.13). **IF** a candidate has `N/A` for a skill **THEN THE SYSTEM SHALL** leave that candidate out of the team mean and diversity of that skill only, **SHALL NOT** change `N/A` to `0` or `1`, and **SHALL NOT** reject the run. The team diagnostics show the `N/A` count per skill.

#### Scenario: Candidate with one N/A skill placed cleanly
- **WHEN** candidate `cand_011` has `git = 3.5` and `docker = N/A`
- **THEN** the optimizer places `cand_011`, uses `cand_011` in the `git` metrics, leaves `cand_011` out of the `docker` means, and never treats `docker` as `0`.

#### Scenario: Unreleased attempt
- **WHEN** the latest attempt of a candidate waits for professor review
- **THEN** the optimizer uses none of its scores, and the team builder shows that candidate as waiting for review.

### Requirement: REQ-TB-12 Team Data Deletion
**WHEN** `AssessmentDeletedEvent` fires **THE SYSTEM SHALL** delete all team proposals and team members of that assessment in the same transaction (Decision 4.4).

#### Scenario: Assessment deletion removes team proposals
- **WHEN** `AssessmentDeletedEvent` fires for `configId`
- **THEN** no `team_proposal` or `team_member` row of `configId` remains.

### Requirement: REQ-TB-08 Validation of Run-Time Grouping Parameters
**IF** a team generation request specifies `numTeams < 1`, `numTeams > candidateCount`, `alpha` or `combinedHardWeight` outside `0..1`, negative or all-zero custom weights, or `SOFT_SKILLS` or `COMBINED` mode when `bfi10Enabled = false` (Decision 2.27), **THEN THE SYSTEM SHALL** reject it with `400` RFC 9457 `ProblemDetail` naming each invalid field and persist no proposal (Decisions 1.19, 1.20).

#### Scenario: Invalid team count rejected
- **WHEN** a professor requests `numTeams = 20` for a cohort of `12` candidates
- **THEN** the backend answers `400` ProblemDetail naming `numTeams` and creates no proposal.

### Requirement: REQ-TB-13 Diagnostics Precision
**THE SYSTEM SHALL** store team diagnostics at full precision after both generate and recalculate. The UI rounds for display only.

#### Scenario: Recalculate after a drag
- **WHEN** the professor moves a student and the proposal is recalculated
- **THEN** the stored diagnostics equal those of a fresh computation on the same teams, with no rounding.
