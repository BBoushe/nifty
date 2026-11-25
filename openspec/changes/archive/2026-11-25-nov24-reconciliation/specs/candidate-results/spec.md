# Spec Delta: Candidate Results

## RENAMED Requirements

- FROM: `### Requirement: REQ-CR-01 Post-Approval Candidate Transparency`
- TO: `### Requirement: REQ-CR-01 Post-Release Candidate Transparency`
- FROM: `### Requirement: REQ-CR-02 Pre-Approval Results Access Gate`
- TO: `### Requirement: REQ-CR-02 Pre-Release Results Access Gate`

## MODIFIED Requirements

### Requirement: REQ-CR-01 Post-Release Candidate Transparency
**WHEN** a candidate's results are released, either automatically (REQ-SM-05) or by professor approval (REQ-SM-06), **THE SYSTEM SHALL** make the candidate's own scores, evidence, weights and formula visible on their results page (Decision 2.10).

#### Scenario: Candidate views approved skill profile
- **WHEN** the results of candidate `cand_042` are released and `cand_042` opens the results page
- **THEN** the page displays released levels (or explicit `N/A`), confidence values, verified transcript quotes, question/skill weights, and the formula version used.

### Requirement: REQ-CR-02 Pre-Release Results Access Gate
**WHILE** a candidate's results are not released **THE SYSTEM SHALL** deny access to scores, weights, formula details and trait scores on the candidate results page.

#### Scenario: Candidate requests results before approval
- **WHEN** a candidate loads the results page while their attempt waits for professor review
- **THEN** the backend withholds all provisional scores, weights, formulas and trait scores and shows a pending-review status.

## ADDED Requirements

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
