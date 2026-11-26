## ADDED Requirements

### Requirement: REQ-CR-07 Readable Conversation and Live Release Status
**WHEN** the candidate opens the conversation on the released results page **THE SYSTEM SHALL** show it in the shared transcript dialog without anomaly markers. **WHILE** the results are not released **THE SYSTEM SHALL** refresh the release status every 5 seconds.

#### Scenario: Results released while the page is open
- **WHEN** the professor releases the attempt while the candidate's results page shows "pending review"
- **THEN** the page shows the results within 5 seconds without a reload.

#### Scenario: Candidate opens the conversation
- **WHEN** the candidate selects "Open conversation"
- **THEN** a dialog shows the questions and answers as a chat, with no "Paste suspected" or "Injection suspected" marker.
