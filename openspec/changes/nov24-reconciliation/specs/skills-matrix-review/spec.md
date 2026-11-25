# Spec Delta: Skills Matrix, Review Queue & Audit Log

## MODIFIED Requirements

### Requirement: REQ-SM-01 Confidence-Bound Flagging
**WHEN** a (question, skill) score's confidence is below the confidence threshold of its question **THE SYSTEM SHALL** mark the score as flagged and place the attempt in the "Needs attention" queue (Decision 2.11).

#### Scenario: Low-confidence score enters priority review queue
- **WHEN** a (question 2, `spring_boot`) score has calculated confidence `0.42` and question 2 has threshold `0.60`
- **THEN** the system marks the cell as flagged (red border) and places the attempt in the "Needs attention" queue.

### Requirement: REQ-SM-03 No Bulk Approval for Flagged Scores
**WHILE** a score is marked as flagged **THE SYSTEM SHALL** prevent bulk approval and require individual human review.

#### Scenario: Bulk approve skips flagged items
- **WHEN** the professor triggers bulk approval across the attempts in the "Needs attention" queue
- **THEN** only unflagged scores are approved, and every flagged score stays pending individual review.

### Requirement: REQ-SM-04 SQL-Only Cohort Gap Overview
**THE SYSTEM SHALL** compute all Cohort Gap Overview statistics in deterministic SQL from the released scores (`approved`, `overridden` or `auto_released`) of each candidate's counted attempt only, and **SHALL NOT** generate cohort numbers via an LLM.

#### Scenario: Lecturer opens Cohort Gap Overview
- **WHEN** the professor loads the Cohort Gap Overview dashboard
- **THEN** skill level distributions, `N/A` shares, and weakest domains are aggregated exclusively in SQL over released scores of counted attempts and link directly to underlying candidate evidence.

## ADDED Requirements

### Requirement: REQ-SM-05 Threshold-Gated Auto-Release
**WHEN** the evaluation of an attempt completes, every (question, skill) score meets its question's threshold, coverage meets the minimum, and no score is flagged, **THE SYSTEM SHALL** set every score of the attempt to `auto_released` and release the results to the candidate (Decision 2.10).

#### Scenario: Confident, complete attempt
- **WHEN** all scores of an attempt are at or above their thresholds, coverage is 0.90 with minimum 0.70, and no entry is paste-suspected
- **THEN** every score becomes `auto_released`, and the candidate's results page opens without professor action.

### Requirement: REQ-SM-06 Professor Review Below Thresholds
**IF** any score of an attempt is flagged or coverage is below the minimum **THEN THE SYSTEM SHALL** keep the attempt's results unreleased until the professor has approved or overridden every flagged score and approved the attempt.

#### Scenario: One flagged score blocks release
- **WHEN** an attempt has one flagged score and all other scores meet their thresholds
- **THEN** the candidate sees a pending-review status until the professor resolves the flagged score and approves the attempt.

### Requirement: REQ-SM-07 Latest Attempt Counts
**WHEN** a candidate completes a newer attempt **THE SYSTEM SHALL** use only that attempt's scores in the matrix, cohort statistics and team building, and keep older attempts readable in the ledger for audit (Decision 2.13).

#### Scenario: Retake replaces the matrix row
- **WHEN** a candidate completes attempt 2 after attempt 1 was released
- **THEN** the matrix shows attempt 2's scores, and the professor can still open the ledger of attempt 1.

### Requirement: REQ-SM-08 Full Conversation Log for the Professor
**WHEN** the professor opens a candidate's attempt **THE SYSTEM SHALL** show the full ledger with timestamps and the `timedOut` and `pasteSuspected` markers (Decision 2.15).

#### Scenario: Professor checks a paste-suspected answer
- **WHEN** the professor opens an attempt with one paste-suspected answer
- **THEN** the log shows that answer with its time to answer and a paste-suspected marker.
