# Spec Delta: Assessment Chat

## MODIFIED Requirements

### Requirement: REQ-AC-08 Timeout Handling
**IF** an answer arrives after `expiresAt` but within the grace period **THEN THE SYSTEM SHALL** record it with `timedOut = true` and evaluate it like any other answer (Decision 2.20). **IF** an answer arrives after `expiresAt` plus the grace period, or no answer arrives, **THEN THE SYSTEM SHALL** reject a late answer with `409`, record the question as timed out without answer text, and move to the next question.

#### Scenario: Browser auto-submits at zero
- **WHEN** the countdown reaches zero and the browser submits the text typed so far 0.4 s after `expiresAt`
- **THEN** the ledger stores the text with `timedOut = true`, the evaluator receives the entry, and the reply stream asks the next question.

#### Scenario: Script blocks the auto-submit
- **WHEN** an answer arrives 2 minutes after `expiresAt`
- **THEN** the system answers `409` ProblemDetail and the ledger records the question as timed out with no answer text.
