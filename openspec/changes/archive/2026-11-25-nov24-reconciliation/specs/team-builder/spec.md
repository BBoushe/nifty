# Spec Delta: Team Builder

## ADDED Requirements

### Requirement: REQ-TB-05 Configured Team Size
**THE SYSTEM SHALL** form teams with the team size from the assessment configuration, place every candidate in exactly one team, and keep team sizes within one of each other (Decision 2.18).

#### Scenario: 22 candidates, team size 5
- **WHEN** the professor runs the solver for 22 candidates with team size 5
- **THEN** every candidate is in exactly one team, and every team has 4, 5 or 6 members with sizes within one of each other.

### Requirement: REQ-TB-06 BFI-10 Balance Soft Constraint
**WHERE** the assessment has BFI-10 on **THE SYSTEM SHALL** apply a soft constraint that keeps each team's mean trait profile close to the cohort mean trait profile, with the weight from the assessment configuration (Decision 4.3).

#### Scenario: Introverts spread across teams
- **WHEN** 4 of 20 candidates have Extraversion below 2.0 and the BFI-10 weight is above 0
- **THEN** the solver prefers assignments that do not place all 4 in the same team, and the constraint explanation names the trait balance.

#### Scenario: BFI-10 off
- **WHEN** the assessment has BFI-10 off
- **THEN** the solver uses no trait constraint.

### Requirement: REQ-TB-07 Trait Scores Visible Only to the Professor
**THE SYSTEM SHALL** show individual trait scores in the team builder only to the `PROFESSOR`, and **SHALL NOT** use trait scores to change any skill score, fit or ranking.

#### Scenario: Professor inspects a team
- **WHEN** the professor opens a team card in the team builder
- **THEN** the card shows each member's five trait scores next to the skill balance.
