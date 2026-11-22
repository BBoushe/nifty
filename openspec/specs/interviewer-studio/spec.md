# Domain Spec: Interviewer Studio (`interviewer-studio`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-01, UX-02 & §14)
> **Primary View:** Ranking View (`PROFESSOR` / `REVIEWER`)

## Purpose

Allow professors to configure, preview, version, and approve how the LLM interviewer conducts assessments—without writing raw system prompts. Configuration drives the interviewer, evidence extractor, scorer, and team balancer.

## Requirements

### Requirement: REQ-IS-01 Immutable Configuration Snapshots
**WHEN** the professor publishes a configuration **THE SYSTEM SHALL** store an immutable, versioned snapshot and link all subsequent interviews to it.

#### Scenario: Publish approved interviewer configuration
- **WHEN** the professor approves and publishes a draft configuration with vision, core questions, skills, and toggles
- **THEN** the system creates an immutable versioned snapshot (e.g., `cfg_7@v3`) and links every subsequent candidate interview to that snapshot.

### Requirement: REQ-IS-02 Controlled Inferred Tag Approval
**WHEN** the extractor suggests a new inferred tag **THE SYSTEM SHALL** store it in a `pending` state and **SHALL NOT** include it in scoring until the professor approves or merges it into the controlled taxonomy.

#### Scenario: Pending tag awaits professor decision
- **WHEN** evidence extraction discovers an unrecognised tag such as `containers`
- **THEN** the system displays it as a `pending` chip for the professor to accept, merge (e.g., into `Docker`), or reject, excluding it from scoring until approved.

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
