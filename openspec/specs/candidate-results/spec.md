# Domain Spec: Candidate Results (`candidate-results`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-10, §12 D10 & §14)
> **Primary View:** Assessment View (`CANDIDATE`)

## Purpose

Provide candidates with full post-approval transparency into their skill profile—showing approved scores, `N/A` explanations, confidence indicators, verbatim evidence quotes, question/skill weights, and the exact formula version used—alongside a dispute entry point.

## Requirements

### Requirement: REQ-CR-01 Post-Approval Candidate Transparency
**WHEN** the professor approves a candidate's scores **THE SYSTEM SHALL** make the candidate's own scores, evidence, weights and formula visible on their results page.

#### Scenario: Candidate views approved skill profile
- **WHEN** the professor approves candidate `cand_042`'s scores and `cand_042` opens the results page
- **THEN** the page displays approved levels (or explicit `N/A`), confidence values, verified transcript quotes, question/skill weights, and the formula version used.

### Requirement: REQ-CR-02 Pre-Approval Results Access Gate
**WHILE** a candidate's scores are not yet approved by the professor **THE SYSTEM SHALL** deny access to scores, weights, and formula details on the candidate results page.

#### Scenario: Candidate requests results before approval
- **WHEN** a candidate loads the results page while their scores remain pending human review
- **THEN** the backend withholds all provisional scores, weights, and formulas and shows a pending-review status.

### Requirement: REQ-CR-03 Strict Candidate Isolation
**THE SYSTEM SHALL** restrict candidate result queries so a candidate can view only their own approved profile and **SHALL NOT** access another candidate's transcript or scores.

#### Scenario: Candidate requests another candidate's ID
- **WHEN** an authenticated `CANDIDATE` requests `/api/v1/assessment/results/{otherCandidateId}`
- **THEN** the backend returns `403 Forbidden`.
