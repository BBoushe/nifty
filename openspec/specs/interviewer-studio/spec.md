# Domain Spec: Interviewer Studio (`interviewer-studio`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-01, UX-02 & §14), updated 2026-11-25 by `nov24-reconciliation`
> **Primary View:** Ranking View (`PROFESSOR`)

## Purpose

Allow professors to configure, preview, version, and approve how the LLM interviewer conducts assessments. Professors do not write raw system prompts. The configuration drives the interviewer, evidence extractor, scorer, and team balancer.

## Requirements

### Requirement: REQ-IS-01 Immutable Configuration Snapshots
**WHEN** the professor publishes a configuration **THE SYSTEM SHALL** store an immutable, versioned snapshot and link all subsequent interviews to it.

#### Scenario: Publish approved interviewer configuration
- **WHEN** the professor approves and publishes a draft configuration with vision, core questions, skills, and toggles
- **THEN** the system creates an immutable versioned snapshot (e.g., `cfg_7@v3`) and links every subsequent candidate interview to that snapshot.

### Requirement: REQ-IS-02 Controlled Inferred Tag Approval
**WHEN** the extractor suggests a new inferred tag **THE SYSTEM SHALL** store it in a `pending` state and **SHALL NOT** include it in scoring until the professor approves or merges it into the controlled taxonomy. The `studio` slice owns the taxonomy and the tag states; the evidence pipeline submits suggestions only through the `studio` service (Decision 1.16).

#### Scenario: Pending tag awaits professor decision
- **WHEN** evidence extraction discovers an unrecognised tag such as `containers`
- **THEN** the system displays it as a `pending` chip for the professor to accept, merge (e.g., into `Docker`), or reject, excluding it from scoring until approved.

#### Scenario: Evidence pipeline cannot write tags directly
- **WHEN** the evidence pipeline proposes the tag `containers`
- **THEN** it calls the `studio` service, which stores the tag as `pending`, and no class outside `studio` uses the `studio` repository.

### Requirement: REQ-IS-03 Post-Interview Structural Configuration Changes
**WHEN** the professor adds new skills or core questions after interviews have already occurred **THE SYSTEM SHALL** create a new configuration version and mark previously assessed candidates as incomplete against the new version.

#### Scenario: Add core question after candidates completed interviews
- **WHEN** a new core question is added to a configuration with existing completed transcripts
- **THEN** the system increments the configuration version and flags affected prior candidates as incomplete rather than mutating their historical snapshot.

### Requirement: REQ-IS-04 Draft Configuration Gate
**THE SYSTEM SHALL** prevent any candidate from starting an interview against a draft (unapproved) configuration.

#### Scenario: Candidate opens invite for unapproved configuration
- **WHEN** a candidate attempts to begin an assessment tied to an unapproved draft configuration
- **THEN** the system rejects session creation until the professor explicitly approves and publishes the configuration.

### Requirement: REQ-IS-05 Assessment Configuration Form
**THE SYSTEM SHALL** let the professor set per assessment: skills and skill weights; core questions with weights, target skills, a confidence threshold, a minimum and maximum time, and a follow-up limit; minimum coverage; maximum attempts; the BFI-10 step on or off; team size; and soft-constraint weights. **THE SYSTEM SHALL NOT** fix a skill count or team size in code (Decisions 2.17, 2.18).

#### Scenario: Professor creates an assessment
- **WHEN** the professor submits the form with 5 skills, 4 core questions, team size 4 and BFI-10 on
- **THEN** the draft configuration stores exactly these values, and the interviewer, scorer and team builder read them from the configuration.

### Requirement: REQ-IS-06 Configuration Value Validation
**IF** a submitted configuration has a minimum time not below the maximum time, a confidence threshold or minimum coverage outside `0..1`, fewer than 1 attempt, a team size below 2, or a core question without a target skill, **THEN THE SYSTEM SHALL** reject it with a `400` ProblemDetail that names each invalid field.

#### Scenario: Minimum time above maximum time
- **WHEN** the professor saves a core question with minimum time 120 s and maximum time 60 s
- **THEN** the system answers `400` with an `errors` entry for that question's minimum time and stores nothing.

### Requirement: REQ-IS-07 Assessment Deletion
**WHEN** the professor deletes an assessment **THE SYSTEM SHALL** delete all candidate data of that assessment (ledger, transcripts, evidence, scores, BFI-10 answers, results and invite links) and write an `audit_log` entry that records the deletion without candidate content (Decision 4.4).

#### Scenario: Professor deletes a finished assessment
- **WHEN** the professor deletes assessment `cfg_7` with 30 completed attempts
- **THEN** no ledger row, score, trait score or invite link of `cfg_7` remains, and one `audit_log` row records actor, time, `cfg_7` and the action `delete`.
