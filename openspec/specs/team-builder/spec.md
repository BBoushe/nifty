# Domain Spec: Team Builder (`team-builder`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-08, §9 step 8–9, §12 D7 & §14)
> **Primary View:** Ranking View (`PROFESSOR`) + Backend `Timefold Solver`

## Purpose

Form balanced, complementary, and defensible student teams using deterministic constraint solving (Timefold Solver), with live drag-and-drop balance recalculation, explainable constraint reasoning, and mandatory professor approval.

## Scope

- Visual team cards with live skill coverage and balance charts (ECharts).
- **Mode A (Solver Presets):** Team size, minimum strong member per key domain, balanced cohort averages solved by Timefold Solver.
- **Mode B (Natural Language → Constraints):** Professor enters criteria in plain text; a fresh, empty-context LLM translates text into structured solver constraints; professor reviews and confirms; Timefold Solver computes teams.
- **Mode C (Optional Stretch — Validated LLM Proposal):** Fresh-context LLM proposes teams from computed approved scores only; deterministic validator checks every invariant before display alongside solver output.
- Interactive drag-and-drop manual adjustments with live deterministic balance recalculation.
- Click any team or member to inspect constraint scores and driving skill evidence.
- Final human approval gate before team publication.

## Seed Requirements (EARS)

- **REQ-TB-01:** **WHEN** an LLM-proposed team assignment is received **THE SYSTEM SHALL** validate that every candidate is assigned exactly once and that team sizes match the constraints before displaying it.
- **REQ-TB-02:** **THE SYSTEM SHALL** execute Mode B constraint parsing and Mode C team proposals only in a fresh, empty LLM context containing no prior chat history, and **SHALL** require professor confirmation of parsed constraints before running Timefold Solver.
- **REQ-TB-03:** **WHEN** a professor drags a candidate to another team **THE SYSTEM SHALL** deterministically recalculate all team balance and constraint scores immediately without invoking an LLM.
- **REQ-TB-04:** **THE SYSTEM SHALL** require explicit professor approval before marking team assignments as published.
