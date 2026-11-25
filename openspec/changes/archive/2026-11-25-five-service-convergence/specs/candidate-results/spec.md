# Spec Delta: Candidate Results

## MODIFIED Requirements

### Requirement: REQ-CR-01 Post-Release Candidate Transparency
**WHEN** a candidate's results are released, either automatically (REQ-SM-05) or by professor approval (REQ-SM-06), **THE SYSTEM SHALL** make the candidate's own scores, evidence, question weights and formula visible on their results page (`GET /api/v1/assessment/results/{configId}`) (Decisions 2.10, 2.22).

#### Scenario: Candidate views approved skill profile
- **WHEN** the results of candidate `cand_042` are released and `cand_042` opens the results page
- **THEN** the page displays released levels (or explicit `N/A`), confidence values, verified transcript quotes, question weights, and the formula version used.

### Requirement: REQ-CR-03 Strict Candidate Isolation
**THE SYSTEM SHALL** restrict candidate result queries so a candidate can view only their own released profile and **SHALL NOT** access another candidate's transcript or scores. The results endpoint takes the candidate from the signed-in invite, never from a path or query parameter.

#### Scenario: Candidate requests another candidate's ID
- **WHEN** an authenticated `CANDIDATE` requests `/api/v1/assessment/results/{configId}` for an assessment that their invite does not belong to
- **THEN** the backend returns `403 Forbidden`.
