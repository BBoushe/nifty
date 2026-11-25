# Spec Delta: Evaluation Service (`evaluation-service`)

## ADDED Requirements

### Requirement: REQ-EV-01 Unified Evaluation Slice Query Contract for Team Builder
**THE SYSTEM SHALL** expose a read-only `EvaluationQueryService` that returns a `CandidateHardSkillProfile` (fit, coverage, and each skill as `SCORED` `0.00..4.00` or `N/A`) for each candidate whose latest completed attempt is released (`AUTO_RELEASED`, `APPROVED` or `OVERRIDDEN`). It lists the other candidates in `pendingInviteIds` with no scores. `teams` never reads `evaluation/repository` (Decisions 1.18, 2.10, 2.13, REQ-TB-11).

#### Scenario: Team builder reads released profiles with explicit N/A
- **WHEN** `teams` calls `EvaluationQueryService.getCandidateProfilesForConfig(configId)`
- **THEN** each released candidate appears once (latest completed attempt) with `status = "SCORED"` + `0.00..4.00` or `status = "N/A"` + `null`, never `0` for untested skills.

#### Scenario: Flagged attempt stays out of team building
- **WHEN** the latest attempt of candidate `cand_007` has one `flagged` evaluation
- **THEN** no profile of `cand_007` is returned, and `cand_007`'s invite ID is in `pendingInviteIds`.

### Requirement: REQ-EV-02 GDPR Purge Listener for Evaluation Tables
**WHEN** `AssessmentDeletedEvent(configId, actorId)` fires **THE SYSTEM SHALL** delete all `skill_score`, `response_evaluation`, `candidate_evaluation_summary`, `evaluation_run`, and `formula_version` rows for `configId` inside the same transaction (Decision 4.4).

#### Scenario: Deleting an assessment removes all evaluation rows
- **WHEN** `AssessmentDeletedEvent` fires for `configId`
- **THEN** zero evaluation rows remain for `configId`.
