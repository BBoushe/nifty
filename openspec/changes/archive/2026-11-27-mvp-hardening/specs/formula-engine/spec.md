## ADDED Requirements

### Requirement: REQ-FE-07 Scores Use the Stored Formula Version
**WHEN** the system computes the scores of an attempt **THE SYSTEM SHALL** read the weights from the stored formula version. This includes the first computation. **THE SYSTEM SHALL** record that version on the scores.

#### Scenario: First evaluation
- **WHEN** the first attempt of an assessment is scored
- **THEN** formula version 1 exists, the scores use its stored weights, and the scores reference version 1.
