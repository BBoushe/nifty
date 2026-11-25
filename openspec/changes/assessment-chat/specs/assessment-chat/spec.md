# Spec Delta: Assessment Chat (`assessment-chat`)

## ADDED Requirements

### Requirement: REQ-AC-17 AssessmentQueryService Read Contracts for Downstream Slices
**THE SYSTEM SHALL** expose a read-only `AssessmentQueryService` returning `ImmutableTranscriptView` (ledger entries only, zero BFI-10 data) to `evaluation` and `StudentSoftSkillProfile` (already reverse-scored `1.0..5.0` trait means) to `teams` and `evaluation` candidate-results, keeping `assessment/repository` strictly private (Decisions 1.10, 1.18, 4.3).

#### Scenario: Evaluator reads immutable ledger without BFI-10 data
- **WHEN** `evaluation` calls `AssessmentQueryService.getTranscriptForAttempt(attemptId)`
- **THEN** it receives ordered ledger entries with `entryId`, `coreQuestionId`, `origin`, `questionText`, `answerText`, `targetSkillIds`, `timedOut`, `pasteSuspected`, and `injectionSuspected`, and zero BFI-10 fields.
