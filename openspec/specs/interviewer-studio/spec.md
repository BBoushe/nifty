# Domain Spec: Interviewer Studio (`interviewer-studio`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-01, UX-02 & §14)
> **Primary View:** Ranking View (`PROFESSOR` / `REVIEWER`)

## Purpose

Allow professors to configure, preview, version, and approve how the LLM interviewer conducts assessments—without writing raw system prompts. Configuration drives the interviewer, evidence extractor, scorer, and team balancer.

## Scope

- Project/team vision input (free text or job/course description).
- Required **core questions** with per-question weights and linked skills/tags.
- Controlled **skills & tags taxonomy** (accept, merge, or reject pending extractor-suggested tags).
- Behaviour toggles: follow-up probing on/off, invented questions on/off, turn budget, and coverage targets.
- Candidate list management (CSV import / invite link generation).
- Mock-candidate preview mode prior to publishing.
- Immutable versioned configuration snapshots upon professor approval.

## Seed Requirements (EARS)

- **REQ-IS-01:** **WHEN** the professor publishes a configuration **THE SYSTEM SHALL** store an immutable, versioned snapshot and link all subsequent interviews to it.
- **REQ-IS-02:** **WHEN** the extractor suggests a new inferred tag **THE SYSTEM SHALL** store it in a `pending` state and **SHALL NOT** include it in scoring until the professor approves or merges it into the controlled taxonomy.
- **REQ-IS-03:** **WHEN** the professor adds new skills or core questions after interviews have already occurred **THE SYSTEM SHALL** create a new configuration version and mark previously assessed candidates as incomplete against the new version.
- **REQ-IS-04:** **THE SYSTEM SHALL** prevent any candidate from starting an interview against a draft (unapproved) configuration.
