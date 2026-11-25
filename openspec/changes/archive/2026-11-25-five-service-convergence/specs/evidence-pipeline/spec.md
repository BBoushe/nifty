# Spec Delta: Evidence Pipeline

## MODIFIED Requirements

### Requirement: REQ-EP-01 Verbatim Quote Verification
**WHEN** the extractor returns an evidence item **THE SYSTEM SHALL** verify that its quote has at least 8 characters and appears word-for-word in the answer text of the referenced ledger entry, and discard it if it does not. The verifier collapses runs of whitespace in both the quote and the answer text before it compares them. It does not change any other character, and the stored ledger text stays unchanged.

#### Scenario: Hallucinated quote rejected by deterministic verifier
- **WHEN** the LLM extractor emits an evidence item whose `quote` is not an exact substring of the referenced `entryId` answer text
- **THEN** the deterministic quote verifier discards the item before scoring.

#### Scenario: Line break inside a quote
- **WHEN** the answer text has "wrap it in a\ntransaction" and the quote is "wrap it in a transaction"
- **THEN** the verifier accepts the quote, and the ledger text keeps its line break.

### Requirement: REQ-EP-03 Deterministic Confidence Calculation
**THE SYSTEM SHALL** compute confidence for each (question, skill) score deterministically from measurable signals (evidence count, quote verification ratio, judge agreement between the two judge runs, question origin, answer length, and prompted vs. unprompted terms) and **SHALL NOT** use LLM self-reported confidence values (Decision 2.11).

#### Scenario: Judge disagreement lowers calculated confidence
- **WHEN** two independent judge runs disagree on a rubric level boundary for skill `spring_boot` on core question 2
- **THEN** the deterministic confidence calculator reduces the `0..1` confidence of the (question 2, `spring_boot`) score based on the documented signal formula.

#### Scenario: One answer scores two skills
- **WHEN** one answer gives verified evidence for `spring_boot` and `testing`
- **THEN** the calculator stores a separate confidence for (question, `spring_boot`) and (question, `testing`).

### Requirement: REQ-EP-05 Asynchronous Evaluation After Commit
**WHEN** an attempt completes and its transaction commits **THE SYSTEM SHALL** start the evaluator asynchronously (`@TransactionalEventListener(phase = AFTER_COMMIT)` on `AttemptCompletedEvent`) with only the attempt's ledger entries and the hidden rubric as input (Decisions 1.12, 1.21). **THE SYSTEM SHALL** run two independent judge calls with fresh contexts for each (entry, skill) pair, with prompts from `ContextCreationService.buildJudgeContext` (Decision 1.18).

#### Scenario: Evaluator starts after the last answer
- **WHEN** the candidate's final answer commits
- **THEN** the evaluator runs outside the request thread, and the candidate's request returns without waiting for it.

#### Scenario: Rolled-back completion
- **WHEN** the transaction that completes an attempt rolls back
- **THEN** the evaluator does not start.

### Requirement: REQ-EP-07 Paste-Suspected Scores Are Flagged
**WHEN** a ledger entry has `pasteSuspected = true` or `injectionSuspected = true` **THE SYSTEM SHALL** mark every (question, skill) score that uses evidence from that entry as flagged and store the flag reason (Decision 2.24).

#### Scenario: Pasted answer reaches review
- **WHEN** evidence for `testing` comes from an entry with `pasteSuspected = true`
- **THEN** the (question, `testing`) score has review status `flagged`.

#### Scenario: Injection attempt reaches review
- **WHEN** evidence for `testing` comes from an entry with `injectionSuspected = true`
- **THEN** the (question, `testing`) score has review status `flagged`, and the attempt cannot release automatically.

## ADDED Requirements

### Requirement: REQ-EP-08 Machine-Parsable Evaluator Output
**THE SYSTEM SHALL** make each judge call return one JSON object with exactly the fields `level` (integer `0..4`), `verbatimQuote`, `rationale` and an optional `suggestion`. **THE SYSTEM SHALL** parse each object in code and discard it if it breaks the schema or the level range. **THE SYSTEM SHALL NOT** accept totals, fit, coverage, confidence, rankings or team-balance numbers from the LLM. Code computes these from the validated levels (Decision 2.25).

#### Scenario: Valid judge output
- **WHEN** a judge call returns `{"level": 3, "verbatimQuote": "I would add an index on email", "rationale": "Names the fix", "suggestion": "Explain the trade-off"}`
- **THEN** the parser accepts the object, and the quote goes to the verifier (REQ-EP-01).

#### Scenario: Judge output with a ranking
- **WHEN** a judge call returns `{"level": 5, "rank": 1}`
- **THEN** the parser discards the object, records the parse failure, and uses no number from it.

### Requirement: REQ-EP-09 Idempotent Evaluation Run
**THE SYSTEM SHALL** store one `evaluation_run` per attempt and formula version, so a repeated `AttemptCompletedEvent` starts no second run.

#### Scenario: Duplicate event
- **WHEN** two `AttemptCompletedEvent` notifications arrive for the same attempt and formula version
- **THEN** the system keeps one `evaluation_run` and makes no second set of LLM calls.
