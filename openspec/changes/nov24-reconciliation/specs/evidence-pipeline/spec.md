# Spec Delta: Evidence Pipeline

## MODIFIED Requirements

### Requirement: REQ-EP-03 Deterministic Confidence Calculation
**THE SYSTEM SHALL** compute confidence for each (question, skill) score deterministically from measurable signals (evidence count, quote verification ratio, judge agreement, question origin, and prompted vs. unprompted terms) and **SHALL NOT** use LLM self-reported confidence values (Decision 2.11).

#### Scenario: Judge disagreement lowers calculated confidence
- **WHEN** two independent judge runs disagree on a rubric level boundary for skill `spring_boot` on core question 2
- **THEN** the deterministic confidence calculator reduces the `0..1` confidence of the (question 2, `spring_boot`) score based on the documented signal formula.

#### Scenario: One answer scores two skills
- **WHEN** one answer gives verified evidence for `spring_boot` and `testing`
- **THEN** the calculator stores a separate confidence for (question, `spring_boot`) and (question, `testing`).

## ADDED Requirements

### Requirement: REQ-EP-05 Asynchronous Evaluation After Commit
**WHEN** an attempt completes and its transaction commits **THE SYSTEM SHALL** start the evaluator asynchronously (`@TransactionalEventListener(phase = AFTER_COMMIT)`) with only the attempt's ledger entries and the hidden rubric as input (Decision 1.12).

#### Scenario: Evaluator starts after the last answer
- **WHEN** the candidate's final answer commits
- **THEN** the evaluator runs outside the request thread, and the candidate's request returns without waiting for it.

#### Scenario: Rolled-back completion
- **WHEN** the transaction that completes an attempt rolls back
- **THEN** the evaluator does not start.

### Requirement: REQ-EP-06 Timed-Out Questions Produce N/A
**IF** a core question timed out **THEN THE SYSTEM SHALL** record `N/A` for every (question, skill) score of that question and its follow-ups, and **SHALL NOT** send their entries to the evaluator (Decision 2.14).

#### Scenario: Partial answer at timeout
- **WHEN** question 3 has a ledger answer with `timedOut = true`
- **THEN** every (question 3, skill) score is `N/A`, and the evaluator input contains no entry of question 3.

### Requirement: REQ-EP-07 Paste-Suspected Scores Are Flagged
**WHEN** a ledger entry has `pasteSuspected = true` **THE SYSTEM SHALL** mark every (question, skill) score that uses evidence from that entry as flagged.

#### Scenario: Pasted answer reaches review
- **WHEN** evidence for `testing` comes from an entry with `pasteSuspected = true`
- **THEN** the (question, `testing`) score has review status `flagged`.
