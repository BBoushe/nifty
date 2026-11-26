## MODIFIED Requirements

### Requirement: REQ-CC-02 Interviewer Guardrail Injection
**WHEN** building an `INTERVIEWER` context **THE SYSTEM SHALL** inject directives to stay on configured topics, reveal no answers or scores, avoid judging the candidate as a person, ask no invented questions, respect the remaining follow-ups, and refuse role-override or test-skip commands (REQ-AC-02, REQ-AC-13, Decision 2.32).

#### Scenario: Guardrails block role override
- **WHEN** `ContextCreationService` builds an interviewer context for an active core question
- **THEN** the system prompt forbids invented topics, score disclosure, personal judgement, and role changes.

## ADDED Requirements

### Requirement: REQ-CC-10 Core Question Before the Follow-Up Rule
**WHEN** the active core question is not asked yet **THE SYSTEM SHALL** tell the interviewer to ask it now, also with follow-up limit `0`.

#### Scenario: Follow-up limit 0 on question 1
- **WHEN** the context is built for core question 1 with follow-up limit 0
- **THEN** the prompt says to ask question 1 now and to ask no follow-up. It does not say that the next question comes next.
