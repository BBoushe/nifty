# Spec Delta: Skills Matrix Review

## MODIFIED Requirements

### Requirement: REQ-SM-01 Confidence-Bound Flagging
**WHEN** a (question, skill) score's confidence is below the confidence threshold of its question, its two judge runs differ by 1 level or more, its quote fails verification on an answer of 8 characters or more, or its entry has `pasteSuspected = true` or `injectionSuspected = true`, **THE SYSTEM SHALL** mark the score as flagged and place the attempt in the "Needs attention" queue (Decisions 2.11, 2.24). An `N/A` score does not fail a confidence threshold.

#### Scenario: Low-confidence score enters priority review queue
- **WHEN** a (question 2, `spring_boot`) score has calculated confidence `0.42` and question 2 has threshold `0.60`
- **THEN** the system marks the cell as flagged (red border) and places the attempt in the "Needs attention" queue.

#### Scenario: Judges disagree
- **WHEN** judge run 1 gives level 2 and judge run 2 gives level 4 for (question 3, `testing`)
- **THEN** the score is flagged, whatever its confidence.

### Requirement: REQ-SM-02 Auditable Score Overrides
**WHEN** the professor overrides a score **THE SYSTEM SHALL** require a reason, keep the original AI level, store the new effective level, recompute the formula, and write an audit log entry with user, timestamp, old value, new value and reason. Only `AuditService` writes the audit log (Decision 1.18).

#### Scenario: Professor overrides a flagged skill level
- **WHEN** a professor changes a candidate's skill level from `2` to `3` with reason `"Follow-up explanation showed practical mastery"`
- **THEN** the system persists the new score and inserts an immutable `audit_log` row containing actor, timestamp, old value, new value, and reason.

### Requirement: REQ-SM-08 Full Conversation Log for the Professor
**WHEN** the professor opens a candidate's attempt **THE SYSTEM SHALL** show the full ledger with timestamps and the `timedOut`, `pasteSuspected` and `injectionSuspected` markers, and offer its `assessment-transcript/v1` export (`GET /api/v1/ranking/transcripts/{attemptId}`) (Decisions 2.15, 2.24).

#### Scenario: Professor checks a paste-suspected answer
- **WHEN** the professor opens an attempt with one paste-suspected answer
- **THEN** the log shows that answer with its time to answer and a paste-suspected marker.
