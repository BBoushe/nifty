## ADDED Requirements

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
