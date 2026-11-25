# Spec Delta: Interviewer Studio (`studio-assessment-creation`)

## ADDED Requirements

### Requirement: REQ-IS-12 Idempotency Key Ownership
**IF** a professor sends an `Idempotency-Key` that another professor already used **THEN THE SYSTEM SHALL** answer `400` ProblemDetail and save nothing. Only the professor who first used a key gets the replay of REQ-IS-01 (Decisions 1.19, 2.21).

#### Scenario: Key reused by another professor
- **WHEN** professor B sends `Idempotency-Key = K` after professor A used `K`
- **THEN** the system answers `400` ProblemDetail and saves nothing.
