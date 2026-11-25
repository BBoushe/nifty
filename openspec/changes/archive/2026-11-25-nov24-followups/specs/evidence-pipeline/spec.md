# Spec Delta: Evidence Pipeline

## RENAMED Requirements

- FROM: `### Requirement: REQ-EP-06 Timed-Out Questions Produce N/A`
- TO: `### Requirement: REQ-EP-06 Unanswered Questions Produce N/A`

## MODIFIED Requirements

### Requirement: REQ-EP-06 Unanswered Questions Produce N/A
**IF** a core question or follow-up has no answer text (empty, rejected after the grace period, or never sent) **THEN THE SYSTEM SHALL** record `N/A` for every (question, skill) score that only that entry could support, and **SHALL NOT** send the entry to the evaluator. **THE SYSTEM SHALL** evaluate an answer with `timedOut = true` and text like any other answer (Decision 2.20).

#### Scenario: Partial answer at timeout
- **WHEN** question 3 has a ledger answer with `timedOut = true` and the text "I would wrap it in a transaction"
- **THEN** the evaluator receives the entry, and the (question 3, skill) scores come from its verified evidence like any other answer.

#### Scenario: No answer text
- **WHEN** question 4 timed out with no answer text and has no other answered entry
- **THEN** every (question 4, skill) score is `N/A`, and the evaluator input contains no entry of question 4.
