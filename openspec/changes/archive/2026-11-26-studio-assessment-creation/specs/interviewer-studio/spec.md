# Spec Delta: Interviewer Studio (`studio-assessment-creation`)

## ADDED Requirements

### Requirement: REQ-IS-12 Idempotency Key Ownership
**IF** a professor sends an `Idempotency-Key` that another professor already used **THEN THE SYSTEM SHALL** answer `400` ProblemDetail and save nothing. Only the professor who first used a key gets the replay of REQ-IS-01 (Decisions 1.19, 2.21).

#### Scenario: Key reused by another professor
- **WHEN** professor B sends `Idempotency-Key = K` after professor A used `K`
- **THEN** the system answers `400` ProblemDetail and saves nothing.

### Requirement: REQ-IS-13 BFI-10 Required for Personality Grouping
**IF** a submitted configuration has grouping mode `SOFT_SKILLS` or `COMBINED` and BFI-10 off, **THEN THE SYSTEM SHALL** answer `400` ProblemDetail with `errors[].field = settings.bfi10Enabled` and code `BFI10_REQUIRED`, and store nothing. Grouping mode `HARD_SKILLS` works with BFI-10 on or off (Decision 2.27).

#### Scenario: COMBINED without BFI-10
- **WHEN** the professor submits grouping mode `COMBINED` with BFI-10 off
- **THEN** the system answers `400` naming `settings.bfi10Enabled` with code `BFI10_REQUIRED` and stores nothing.

#### Scenario: HARD_SKILLS without BFI-10
- **WHEN** the professor submits grouping mode `HARD_SKILLS` with BFI-10 off
- **THEN** validation passes.
