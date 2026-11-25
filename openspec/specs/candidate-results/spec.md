# Domain Spec: Candidate Results (`candidate-results`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-10, §12 D10 & §14), updated 2026-11-25 by `nov24-reconciliation` and `five-service-convergence`
> **Primary View:** Assessment View (`CANDIDATE`)

## Purpose

Give candidates full transparency into their skill profile after release. The profile includes approved scores, `N/A` explanations, confidence indicators, verbatim evidence quotes, question weights, and the exact formula version that the system used. The page also has a dispute entry point.

## Requirements

### Requirement: REQ-CR-01 Post-Release Candidate Transparency
**WHEN** a candidate's results are released, either automatically (REQ-SM-05) or by professor approval (REQ-SM-06), **THE SYSTEM SHALL** make the candidate's own scores, evidence, question weights and formula visible on their results page (`GET /api/v1/assessment/results/{configId}`) (Decisions 2.10, 2.22).

#### Scenario: Candidate views approved skill profile
- **WHEN** the results of candidate `cand_042` are released and `cand_042` opens the results page
- **THEN** the page displays released levels (or explicit `N/A`), confidence values, verified transcript quotes, question weights, and the formula version used.

### Requirement: REQ-CR-02 Pre-Release Results Access Gate
**WHILE** a candidate's results are not released **THE SYSTEM SHALL** deny access to scores, weights, formula details and trait scores on the candidate results page.

#### Scenario: Candidate requests results before approval
- **WHEN** a candidate loads the results page while their attempt waits for professor review
- **THEN** the backend withholds all provisional scores, weights, formulas and trait scores and shows a pending-review status.

### Requirement: REQ-CR-03 Strict Candidate Isolation
**THE SYSTEM SHALL** restrict candidate result queries so a candidate can view only their own released profile and **SHALL NOT** access another candidate's transcript or scores. The results endpoint takes the candidate from the signed-in invite, never from a path or query parameter.

#### Scenario: Candidate requests another candidate's ID
- **WHEN** an authenticated `CANDIDATE` requests `/api/v1/assessment/results/{configId}` for an assessment that their invite does not belong to
- **THEN** the backend returns `403 Forbidden`.

### Requirement: REQ-CR-04 Own Conversation Log After Release
**WHEN** a candidate's results are released **THE SYSTEM SHALL** show the candidate the full conversation log of their counted attempt, with timestamps (Decision 2.15).

#### Scenario: Candidate rereads the interview
- **WHEN** a candidate with released results opens the conversation tab
- **THEN** every question and answer of the counted attempt is shown in order, with timestamps.

### Requirement: REQ-CR-05 Deterministic Advice
**THE SYSTEM SHALL** select the candidate's strongest skills and improvement areas in code from released scores. An optional AI sentence (at most 2 sentences, labelled as AI) may phrase only those selected values (Decision 2.16).

#### Scenario: Advice ignores N/A skills
- **WHEN** a candidate has `testing = 4`, `sql = 1` and `docker = N/A`
- **THEN** the page names `testing` as a strength and `sql` as an improvement area, and lists `docker` as not assessed rather than weak.

### Requirement: REQ-CR-06 Own BFI-10 Trait Scores
**WHERE** the assessment had BFI-10 on, **WHEN** the candidate's results are released, **THE SYSTEM SHALL** show the candidate their own five trait scores in neutral words, and **SHALL NOT** show them to any other candidate (Decision 4.3).

#### Scenario: Trait scores on the results page
- **WHEN** a candidate with released results opens the results page of a BFI-10 assessment
- **THEN** the page shows five trait scores on the 1–5 scale with neutral descriptions and no ranking words.
