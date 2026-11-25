# Domain Spec: Skills Matrix, Review Queue & Audit Log (`skills-matrix-review`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-04, UX-05, UX-06, UX-07, UX-12 & §14)
> **Primary View:** Ranking View (`PROFESSOR`)

## Purpose

Provide an evidence-first visual review workspace. Professors use it to inspect candidate skill matrices and to open verified quotes in one click. They also prioritize uncertain or high-impact scores, record auditable overrides, and inspect SQL-calculated cohort gaps.

## Requirements

### Requirement: REQ-SM-01 Confidence-Bound Flagging
**WHEN** a score's confidence is below the configured lower bound **THE SYSTEM SHALL** mark it as flagged and place it in the "Needs attention" queue.

#### Scenario: Low-confidence score enters priority review queue
- **WHEN** a skill score has calculated confidence `0.42` and the configured lower bound is `0.60`
- **THEN** the system marks the cell as flagged (red border) and places it at the top of the "Needs attention" queue.

### Requirement: REQ-SM-02 Auditable Score Overrides
**WHEN** the professor overrides a score **THE SYSTEM SHALL** require a reason and write an audit log entry with user, timestamp, old value and new value.

#### Scenario: Professor overrides a flagged skill level
- **WHEN** a professor changes a candidate's skill level from `2` to `3` with reason `"Follow-up explanation showed practical mastery"`
- **THEN** the system persists the new score and inserts an immutable `audit_log` row containing actor, timestamp, old value, new value, and reason.

### Requirement: REQ-SM-03 No Bulk Approval for Flagged Scores
**WHILE** a score is marked as flagged **THE SYSTEM SHALL** prevent bulk approval and require individual human review.

#### Scenario: Bulk approve skips flagged items
- **WHEN** a reviewer triggers bulk approval across a candidate cohort
- **THEN** only unflagged scores above the upper confidence bound are bulk-approved while all flagged scores remain pending individual review.

### Requirement: REQ-SM-04 SQL-Only Cohort Gap Overview
**THE SYSTEM SHALL** compute all Cohort Gap Overview statistics in deterministic SQL from approved scores only and **SHALL NOT** generate cohort numbers via an LLM.

#### Scenario: Lecturer opens Cohort Gap Overview
- **WHEN** the professor loads the Cohort Gap Overview dashboard
- **THEN** skill level distributions, `N/A` shares, and weakest domains are aggregated exclusively in SQL over approved scores and link directly to underlying candidate evidence.
