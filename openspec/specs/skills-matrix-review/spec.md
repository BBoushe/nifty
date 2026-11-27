# Domain Spec: Skills Matrix, Review Queue & Audit Log (`skills-matrix-review`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-04, UX-05, UX-06, UX-07, UX-12 & §14), updated 2026-11-25 by `nov24-reconciliation` and `five-service-convergence`
> **Primary View:** Ranking View (`PROFESSOR`)

## Purpose

Provide an evidence-first visual review workspace. Professors use it to inspect candidate skill matrices and to open verified quotes in one click. They also prioritize uncertain or high-impact scores, record auditable overrides, and inspect SQL-calculated cohort gaps.

## Requirements

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

### Requirement: REQ-SM-03 Individual Review Only (No Bulk Approval)
**WHERE** an attempt is not auto-released (REQ-SM-05), **THE SYSTEM SHALL** require the professor to approve or override each flagged evaluation of that attempt individually. **THE SYSTEM SHALL** also require the professor to release that attempt individually. **THE SYSTEM SHALL NOT** provide bulk approval (Decision 2.30).

#### Scenario: Professor reviews an attempt individually
- **WHEN** the professor opens an attempt that is not auto-released in the Evidence Drawer
- **THEN** the professor approves or overrides each flagged evaluation individually before releasing the attempt, with no bulk-approval action in the UI or API.

#### Scenario: Auto-released attempt needs no professor action
- **WHEN** every score of an attempt meets its threshold and no score has a flag (REQ-SM-05)
- **THEN** the attempt is auto-released without professor action, and REQ-SM-03 does not apply.

#### Scenario: Second release gets 409
- **WHEN** the professor releases an attempt that is already released
- **THEN** the system answers `409`, keeps the original release time, and writes no audit log entry.

#### Scenario: Two concurrent releases
- **WHEN** two release requests for one unreleased attempt arrive at the same time
- **THEN** exactly one request releases the attempt and writes one `ATTEMPT_RELEASED` audit log entry, and the other request gets `409`.

### Requirement: REQ-SM-04 SQL-Only Cohort Gap Overview
**THE SYSTEM SHALL** compute all Cohort Gap Overview statistics in deterministic SQL from the released scores (`approved`, `overridden` or `auto_released`) of each candidate's counted attempt only, and **SHALL NOT** generate cohort numbers via an LLM.

#### Scenario: Lecturer opens Cohort Gap Overview
- **WHEN** the professor loads the Cohort Gap Overview dashboard
- **THEN** skill level distributions, `N/A` shares, and weakest domains are aggregated exclusively in SQL over released scores of counted attempts and link directly to underlying candidate evidence.

### Requirement: REQ-SM-05 Threshold-Gated Auto-Release
**WHEN** the evaluation of an attempt completes, every (question, skill) score meets its question's threshold, coverage meets the minimum, and no score is flagged, **THE SYSTEM SHALL** set every score of the attempt to `auto_released` and release the results to the candidate (Decision 2.10). `N/A` scores are left out of the threshold check. Coverage covers them (REQ-FE-03).

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
**WHEN** the professor opens a candidate's attempt **THE SYSTEM SHALL** show the full ledger with timestamps and the `timedOut`, `pasteSuspected` and `injectionSuspected` markers, and offer its `assessment-transcript/v1` export (`GET /api/v1/ranking/transcripts/{attemptId}`) (Decisions 2.15, 2.24).

#### Scenario: Professor checks a paste-suspected answer
- **WHEN** the professor opens an attempt with one paste-suspected answer
- **THEN** the log shows that answer with its time to answer and a paste-suspected marker.

### Requirement: REQ-SM-09 Evidence Review in a Modal Dialog
**WHEN** the professor opens an attempt from the Skills Matrix **THE SYSTEM SHALL** show the evidence review in a modal dialog over the Ranking page. **THE SYSTEM SHALL** close the dialog on Esc, on Close and on a click on the mask, and keep the matrix state.

#### Scenario: Professor inspects a student
- **WHEN** the professor clicks Inspect on a student row
- **THEN** a modal dialog opens with the candidate name, the attempt number and the evidence, and the matrix stays visible behind the mask.

#### Scenario: Professor closes the dialog
- **WHEN** the professor presses Esc in the open dialog
- **THEN** the dialog closes, the focus returns to the Inspect button, and the filter and scroll position of the matrix stay the same.

### Requirement: REQ-SM-10 Readable Transcript Dialog
**WHEN** the professor selects View transcript or Show in transcript **THE SYSTEM SHALL** show the conversation as a chat in a modal dialog. Interviewer questions are on one side and candidate answers on the other. Each turn shows its local time and the time to answer. Timed-out, paste-suspected and injection-suspected answers have markers.

#### Scenario: Show in transcript
- **WHEN** the professor selects Show in transcript on an evidence item with a verified quote
- **THEN** the transcript dialog opens, scrolls to that turn, and highlights the quote inside the answer text.

#### Scenario: Unanswered question
- **WHEN** a turn has no answer text
- **THEN** the dialog shows a muted "No answer" bubble for that turn.

### Requirement: REQ-SM-11 Live Evaluation Status
**WHILE** an evaluation in view is `QUEUED` or `RUNNING` **THE SYSTEM SHALL** refresh the Skills Matrix and the evidence dialog every 5 seconds. **THE SYSTEM SHALL** enable Release only for an attempt with a `COMPLETED` evaluation, zero flagged evaluations and no earlier release.

#### Scenario: Evaluation finishes while the page is open
- **WHEN** an attempt's evaluation changes from `RUNNING` to `COMPLETED`
- **THEN** the matrix shows the new status within 5 seconds without a page reload.

#### Scenario: Release on a running evaluation
- **WHEN** the evaluation of an attempt is `RUNNING`
- **THEN** the Release button is disabled and its tooltip names the reason.

### Requirement: REQ-SM-12 Candidate Names in Ranking
**THE SYSTEM SHALL** show the invite's `candidateRef` for each attempt in the Skills Matrix, the heatmap, the formula preview and the evidence dialog. **THE SYSTEM SHALL NOT** show a UUID prefix in place of the name.

#### Scenario: Matrix row label
- **WHEN** the matrix lists an attempt of the invite for `anna.schmidt`
- **THEN** the row label is `anna.schmidt (#1)`.

### Requirement: REQ-SM-13 Soft-Skills-Only Auto-Release
**WHERE** an assessment has zero hard skills, **WHEN** an attempt with a complete BFI-10 profile is evaluated, **THE SYSTEM SHALL** auto-release it (Decision 2.31). **IF** the BFI-10 profile is missing **THEN THE SYSTEM SHALL** keep the attempt `pending`.

#### Scenario: BFI-10-only assessment
- **WHEN** a candidate completes the BFI-10 of an assessment with no hard skills
- **THEN** the attempt becomes `auto_released`, and Team Builder can place the candidate.

#### Scenario: Assessment with hard skills and no evidence
- **WHEN** an attempt of an assessment with 3 hard skills has no evaluated response
- **THEN** the attempt stays `pending` (Decision 2.10).

### Requirement: REQ-SM-14 One Lock Order per Attempt
**WHEN** an approve, override, release or assessment deletion writes data of an attempt **THE SYSTEM SHALL** lock that attempt's summary row first. **THE SYSTEM SHALL NOT** answer `500` because two of these writes run at the same time.

#### Scenario: Approve during a release
- **WHEN** an approve and a release of the same attempt arrive at the same time
- **THEN** one request waits for the other, and neither request fails with a database deadlock.
