## MODIFIED Requirements

### Requirement: REQ-IS-05 Assessment Configuration Form
**THE SYSTEM SHALL** let the professor set per assessment: title and vision; skills with 5 rubric levels (`0..4`) and no weight; ordered core questions with weight, target skills, confidence threshold, minimum and maximum time, and follow-up limit (`0..3`); minimum coverage; maximum attempts; BFI-10 on or off; team size; and the grouping settings of REQ-TB-10. **THE SYSTEM SHALL NOT** fix a skill count or team size in code (Decisions 2.17, 2.22, 2.23, 2.32).

#### Scenario: Professor creates an assessment
- **WHEN** the professor submits the form with 5 skills, 4 core questions, team size 4, grouping mode `COMBINED` and BFI-10 on
- **THEN** the configuration stores exactly these values, and the interviewer, scorer and team builder read them from the configuration.

## ADDED Requirements

### Requirement: REQ-IS-14 Concurrent Idempotent Create
**WHEN** one professor sends two creates with the same `Idempotency-Key` at the same time **THE SYSTEM SHALL** save one configuration (REQ-IS-01). Both responses carry it. **THE SYSTEM SHALL NOT** answer `500`.

#### Scenario: Double submit
- **WHEN** the professor's browser sends the same create request twice within 10 ms
- **THEN** the database holds one configuration, and both responses carry its `configId`.
