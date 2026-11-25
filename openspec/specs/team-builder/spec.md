# Domain Spec: Team Builder (`team-builder`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-08, §9 step 8–9, §12 D7 & §14), updated 2026-11-25 by `nov24-reconciliation`
> **Primary View:** Ranking View (`PROFESSOR`) + Backend `Timefold Solver`

## Purpose

Form balanced, complementary, and defensible student teams. The system uses deterministic constraint solving (Timefold Solver). It gives live drag-and-drop balance recalculation and explainable constraint reasoning. Professor approval is mandatory.

## Requirements

### Requirement: REQ-TB-01 Deterministic Validation of LLM Team Proposals
**WHEN** an LLM-proposed team assignment is received **THE SYSTEM SHALL** validate that every candidate is assigned exactly once and that team sizes match the constraints before displaying it.

#### Scenario: Duplicate or missing candidate rejected
- **WHEN** a Mode C LLM proposal omits a candidate or assigns the same candidate to two teams
- **THEN** the deterministic validator rejects the proposal and refuses to display invalid team assignments.

### Requirement: REQ-TB-02 Fresh Context and Human Confirmation for LLM Constraints
**THE SYSTEM SHALL** execute Mode B constraint parsing and Mode C team proposals only in a fresh, empty LLM context containing no prior chat history, and **SHALL** require professor confirmation of parsed constraints before running Timefold Solver.

#### Scenario: Mode B plain-text criteria parsed in fresh context
- **WHEN** a professor enters natural-language team criteria in Mode B
- **THEN** the backend invokes the LLM with an empty context window to produce structured solver constraints and waits for professor confirmation before running Timefold.

### Requirement: REQ-TB-03 Live Deterministic Drag-and-Drop Recalculation
**WHEN** a professor drags a candidate to another team **THE SYSTEM SHALL** deterministically recalculate all team balance and constraint scores immediately without invoking an LLM.

#### Scenario: Manual team adjustment updates balance indicators
- **WHEN** a candidate is moved from Team A to Team B in the Team Builder UI
- **THEN** skill coverage and balance indicators for both teams recalculate deterministically in Java/Timefold with zero LLM calls.

### Requirement: REQ-TB-04 Mandatory Human Approval Before Publishing Teams
**THE SYSTEM SHALL** require explicit professor approval before marking team assignments as published.

#### Scenario: Solver finishes computing candidate teams
- **WHEN** Timefold Solver returns an optimal team assignment
- **THEN** the assignment remains in draft state until the professor explicitly approves and publishes it.

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
