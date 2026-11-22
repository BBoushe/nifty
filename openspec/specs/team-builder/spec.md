# Domain Spec: Team Builder (`team-builder`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-08, §9 step 8–9, §12 D7 & §14)
> **Primary View:** Ranking View (`PROFESSOR`) + Backend `Timefold Solver`

## Purpose

Form balanced, complementary, and defensible student teams using deterministic constraint solving (Timefold Solver), with live drag-and-drop balance recalculation, explainable constraint reasoning, and mandatory professor approval.

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
