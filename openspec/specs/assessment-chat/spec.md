# Domain Spec: Assessment Chat (`assessment-chat`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-09, UX-11, §10 & §14)
> **Primary View:** Assessment View (`CANDIDATE`)

## Purpose

Conduct an adaptive yet standardized conversational assessment via streaming SSE chat, recording every Q/A turn into the `assessment-transcript/v1` JSON contract without revealing scores during the interview.

## Scope

- Invite-link entry for candidates.
- Streaming LLM interviewer responses via Server-Sent Events (SSE).
- Progress indicator showing core-question coverage (e.g., "3 of 6 topics covered").
- Pause and resume session support.
- Tagged transcript capture (`origin`: `core | follow_up | invented`, `coreQuestionId`, `weight`, `targetSkills`, `promptedTerms`, `unpromptedTerms`, `durationSec`) persisted as PostgreSQL `JSONB`.
- Exportable `*.transcript.json` conforming to `assessment-transcript/v1`.

## Seed Requirements (EARS)

- **REQ-AC-01:** **WHILE** an interview is in progress **THE SYSTEM SHALL** ask every core question in the active configuration before ending the session, unless the turn budget is exhausted.
- **REQ-AC-02:** **WHERE** "invented questions" is disabled **THE SYSTEM SHALL** only ask core questions and follow-ups on them.
- **REQ-AC-03:** **WHILE** an interview is in progress **THE SYSTEM SHALL NOT** display any provisional or final skill scores, weights, or evaluation formulas to the candidate.
- **REQ-AC-04:** **WHEN** each question-and-answer turn completes **THE SYSTEM SHALL** persist the turn in `assessment-transcript/v1` JSONB format tagged with its question origin (`core`, `follow_up`, or `invented`) and target skills.
