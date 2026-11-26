# Spec Delta: Assessment Chat (`assessment-chat`)

## ADDED Requirements

### Requirement: REQ-AC-17 AssessmentQueryService Read Contracts for Downstream Slices
**THE SYSTEM SHALL** expose a read-only `AssessmentQueryService` returning `ImmutableTranscriptView` (ledger entries only, zero BFI-10 data) to `evaluation` and `StudentSoftSkillProfile` (already reverse-scored `1.0..5.0` trait means) to `teams` and `evaluation` candidate-results, keeping `assessment/repository` strictly private (Decisions 1.10, 1.18, 4.3).

#### Scenario: Evaluator reads immutable ledger without BFI-10 data
- **WHEN** `evaluation` calls `AssessmentQueryService.getTranscriptForAttempt(attemptId)`
- **THEN** it receives ordered ledger entries with `entryId`, `coreQuestionId`, `origin`, `questionText`, `answerText`, `targetSkillIds`, `timedOut`, `pasteSuspected`, and `injectionSuspected`, and zero BFI-10 fields.

### Requirement: REQ-AC-20 Answer Length Limit
**IF** a candidate submits an answer `content` longer than 4000 characters, **THEN THE SYSTEM SHALL** reject it with `400` RFC 9457 `ProblemDetail` (`errors[].field = content`). The system stores no ledger entry and keeps the question deadline (Decision 2.26). The limit equals the prompt ceiling of `context`. Thus the ledger holds only text that the models see.

#### Scenario: The system rejects an oversized answer and stores nothing
- **WHEN** a candidate posts an answer of 4001 characters to `POST /api/v1/assessment/sessions/{id}/answers`
- **THEN** the system answers `400` with `errors[0].field = content`, writes no `conversation_entry`, and calls no LLM

#### Scenario: The system accepts an answer at the limit
- **WHEN** a candidate posts an answer of exactly 4000 characters
- **THEN** the system stores it and streams the interviewer reply as usual
