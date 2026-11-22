# Domain Spec: Assessment Chat (`assessment-chat`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-09, UX-11, §10 & §14)
> **Primary View:** Assessment View (`CANDIDATE`)

## Purpose

Conduct an adaptive yet standardized conversational assessment via streaming SSE chat, recording every Q/A turn into the `assessment-transcript/v1` JSON contract without revealing scores during the interview.

## Requirements

### Requirement: REQ-AC-01 Mandatory Core Question Coverage
**WHILE** an interview is in progress **THE SYSTEM SHALL** ask every core question in the active configuration before ending the session, unless the turn budget is exhausted.

#### Scenario: All core questions covered within turn budget
- **WHEN** a candidate completes an interview under a configuration containing 6 core questions and a sufficient turn budget
- **THEN** the interviewer asks all 6 core questions before concluding the session.

### Requirement: REQ-AC-02 Invented Questions Toggle Enforcement
**WHERE** "invented questions" is disabled **THE SYSTEM SHALL** only ask core questions and follow-ups on them.

#### Scenario: Invented questions disabled in Studio
- **WHEN** the active configuration has `inventedQuestions = false`
- **THEN** every recorded transcript entry has `question.origin` set strictly to `core` or `follow_up` and never `invented`.

### Requirement: REQ-AC-03 Zero Mid-Interview Score Leakage
**WHILE** an interview is in progress **THE SYSTEM SHALL NOT** display any provisional or final skill scores, weights, or evaluation formulas to the candidate.

#### Scenario: Candidate chats with interviewer
- **WHEN** the candidate views the active Assessment Chat UI
- **THEN** only core-topic progress is displayed, with zero scores, weights, or formula details exposed.

### Requirement: REQ-AC-04 Structured Transcript Persistence
**WHEN** each question-and-answer turn completes **THE SYSTEM SHALL** persist the turn in `assessment-transcript/v1` JSONB format tagged with its question origin (`core`, `follow_up`, or `invented`) and target skills.

#### Scenario: Q/A turn captured and exportable
- **WHEN** a candidate submits an answer to a core question
- **THEN** the backend stores the entry in PostgreSQL JSONB conforming to `assessment-transcript/v1` and allows exporting `*.transcript.json`.
