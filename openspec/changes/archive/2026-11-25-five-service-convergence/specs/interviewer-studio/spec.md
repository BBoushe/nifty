# Spec Delta: Interviewer Studio

## MODIFIED Requirements

### Requirement: REQ-IS-01 Immutable Configuration Snapshots
**WHEN** the professor submits a valid assessment form (`POST /api/v1/ranking/studio/configs` with an `Idempotency-Key` header) **THE SYSTEM SHALL** save the whole form in one transaction as an immutable `PUBLISHED` snapshot `v1`, link all later interviews to it, and write one `CONFIG_CREATED` row through `AuditService`. A retry by the same professor with the same key returns `200` with the existing `configId` and writes no second audit row (Decision 2.21).

#### Scenario: Publish approved interviewer configuration
- **WHEN** the professor submits a valid form with vision, core questions, skills, and toggles
- **THEN** the system answers `201` with a new `configId`, stores the snapshot as `v1`, and links every later candidate interview to that snapshot.

#### Scenario: Double submit returns the same configId
- **WHEN** the professor submits the same form twice with the same `Idempotency-Key`
- **THEN** the second call answers `200` with the same `configId`, and one configuration and one audit row exist.

### Requirement: REQ-IS-05 Assessment Configuration Form
**THE SYSTEM SHALL** let the professor set per assessment: title and vision; skills with 5 rubric levels (`0..4`) and no weight; ordered core questions with weight, target skills, confidence threshold, minimum and maximum time, and follow-up limit (`0..3`); minimum coverage; maximum attempts; invented questions and BFI-10 on or off; team size; and the grouping settings of REQ-TB-10. **THE SYSTEM SHALL NOT** fix a skill count or team size in code (Decisions 2.17, 2.22, 2.23).

#### Scenario: Professor creates an assessment
- **WHEN** the professor submits the form with 5 skills, 4 core questions, team size 4, grouping mode `COMBINED` and BFI-10 on
- **THEN** the configuration stores exactly these values, and the interviewer, scorer and team builder read them from the configuration.

### Requirement: REQ-IS-06 Configuration Value Validation
**IF** a submitted configuration breaks a value bound (minimum time below maximum time, ratios in `0..1`, attempts ≥ 1, team size ≥ 2, follow-up limit `0..3`) or a structure rule (5 non-blank rubric levels, unique skills, gap-free positions, a target skill per question, no unused skill), **THEN THE SYSTEM SHALL** reject it with a `400` ProblemDetail that names each invalid field in `errors[]` and store nothing (Decision 1.19).

#### Scenario: Minimum time above maximum time
- **WHEN** the professor saves a core question with minimum time 120 s and maximum time 60 s
- **THEN** the system answers `400` with an `errors` entry for that question's minimum time and stores nothing.

#### Scenario: Three errors at once
- **WHEN** the form has minimum time 120 s with maximum time 60 s, follow-up limit 4, and a blank rubric level
- **THEN** the system answers `400` with 3 entries in `errors[]` and stores nothing.

### Requirement: REQ-IS-07 Assessment Deletion
**WHEN** the owning professor deletes an assessment (`DELETE /api/v1/ranking/studio/configs/{configId}`) **THE SYSTEM SHALL** publish `AssessmentDeletedEvent`, set `nifty.allow_purge`, and delete in one transaction all its data (configuration, ledger, evidence, scores, BFI-10 answers, results, teams, invite links). **THE SYSTEM SHALL** write an `audit_log` entry through `AuditService` without candidate content (Decision 4.4).

#### Scenario: Professor deletes a finished assessment
- **WHEN** the professor deletes assessment `cfg_7` with 30 completed attempts
- **THEN** no ledger row, score, trait score, team proposal or invite link of `cfg_7` remains, and one `audit_log` row records actor, time, `cfg_7` and the action `CONFIG_DELETED`.

## REMOVED Requirements

### Requirement: REQ-IS-02 Controlled Inferred Tag Approval
**Reason**: Decision 2.21 moves inferred tags to Post-MVP.
**Migration**: None in MVP. The Post-MVP backlog keeps the tag review feature.

### Requirement: REQ-IS-03 Post-Interview Structural Configuration Changes
**Reason**: Decision 2.21 makes every configuration immutable from its creation, with no versions.
**Migration**: The professor creates a new assessment instead of changing one.

### Requirement: REQ-IS-04 Draft Configuration Gate
**Reason**: Decision 2.21 keeps drafts in the browser only, so no draft configuration exists on the server.
**Migration**: REQ-IS-01 creates only `PUBLISHED` configurations.

## ADDED Requirements

### Requirement: REQ-IS-08 Database-Enforced Immutability
**THE SYSTEM SHALL NOT** offer `PUT` or `PATCH` on `/api/v1/ranking/studio/configs/{configId}` (`405`). PostgreSQL triggers on `assessment_config`, `skill`, `core_question` and `core_question_skill` **SHALL** reject every `UPDATE`, and reject every `DELETE` unless the transaction sets `SET LOCAL nifty.allow_purge = 'on'` (Decisions 2.21, 4.4).

#### Scenario: SQL update rejected by trigger
- **WHEN** any code path runs an SQL `UPDATE` on a saved `core_question` row
- **THEN** PostgreSQL rejects the statement and the row stays unchanged.

### Requirement: REQ-IS-09 Narrow Read-Only Views for Other Slices
**THE SYSTEM SHALL** give other slices read-only records through `StudioQueryService` (`exists`, `interviewerView`, `evaluatorView`, `scoringView`, `teamView`). `InterviewerView` **SHALL NOT** contain rubric levels, question weights, confidence thresholds or minimum coverage. `EvaluatorView` **SHALL** contain the 5 rubric levels (`0..4`) of each skill (Decisions 1.12, 1.19).

#### Scenario: Interviewer view hides rubric and weights
- **WHEN** a reflection test reads the record components of `InterviewerView` and `InterviewerQuestion`
- **THEN** no component name contains `rubric`, `weight`, `threshold` or `coverage`.

### Requirement: REQ-IS-10 Professor Scoping
**WHEN** a professor lists assessments (`GET /api/v1/ranking/studio/configs`) or reads one (`GET /api/v1/ranking/studio/configs/{configId}`) **THE SYSTEM SHALL** return only that professor's assessments, newest first, and answer `404` for an unknown ID or for another professor's ID (Decision 1.19).

#### Scenario: Other professor's configuration
- **WHEN** professor A requests the configuration of professor B
- **THEN** the system answers `404` ProblemDetail and does not show whether the ID exists.

### Requirement: REQ-IS-11 Required Skills and Questions
**THE SYSTEM SHALL** require at least 1 skill and 1 core question in a configuration, except when the grouping mode is `SOFT_SKILLS` and BFI-10 is on (Decision 2.23).

#### Scenario: SOFT_SKILLS-only assessment
- **WHEN** the professor submits grouping mode `SOFT_SKILLS`, BFI-10 on, 0 skills and 0 core questions
- **THEN** validation passes and the configuration is saved.

#### Scenario: HARD_SKILLS assessment without questions
- **WHEN** the professor submits grouping mode `HARD_SKILLS` with 0 core questions
- **THEN** the system answers `400` naming the core questions and stores nothing.
