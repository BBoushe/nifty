## MODIFIED Requirements

### Requirement: REQ-AC-02 Invented Questions Toggle Enforcement
**THE SYSTEM SHALL** only ask core questions and follow-ups on them. **THE SYSTEM SHALL** ask no more follow-ups on a core question than its follow-up limit. The LLM words each core question and each follow-up. **THE SYSTEM SHALL NOT** offer an invented-questions setting (Decision 2.32).

#### Scenario: Invented questions disabled in Studio
- **WHEN** any configuration is active, including one stored with `inventedQuestions = true` before Decision 2.32
- **THEN** every recorded transcript entry has `question.origin` set strictly to `core` or `follow_up` and never `invented`.

#### Scenario: Follow-up limit reached
- **WHEN** core question 2 has follow-up limit 1 and the interviewer has asked 1 follow-up on it
- **THEN** the next interviewer question is the next core question.

## ADDED Requirements

### Requirement: REQ-AC-21 Expired Attempt Without BFI-10
**IF** an attempt expires after its last core question and before its BFI-10 form **THEN THE SYSTEM SHALL** complete it without a soft-skill profile (Decision 2.33). **THE SYSTEM SHALL** publish `AttemptCompletedEvent` after commit. The traits stay `N/A`.

#### Scenario: Candidate leaves before the BFI-10 form
- **WHEN** a candidate answers every core question, closes the browser, and the attempt expires
- **THEN** the attempt becomes `COMPLETED`, its interview answers are evaluated, and its BFI-10 traits are `N/A`.

### Requirement: REQ-AC-22 Retake From the Interview Page
**WHEN** the candidate opens the interview page after a completed attempt **THE SYSTEM SHALL** show "Start new attempt" while attempts are left. Otherwise it shows "No attempts left" and no start button.

#### Scenario: Second attempt allowed
- **WHEN** a candidate with 1 completed attempt and a maximum of 2 opens `/assessment`
- **THEN** the page shows "Start new attempt", and a click starts attempt 2.

#### Scenario: No attempts left
- **WHEN** a candidate with 2 completed attempts and a maximum of 2 opens `/assessment` in a new tab
- **THEN** the page shows "No attempts left" and no start button.

### Requirement: REQ-AC-23 Immediate Fallback on Prompt Failure
**IF** the interviewer prompt cannot be built or the interviewer LLM call fails **THEN THE SYSTEM SHALL** send the configured question text at once (Decision 2.29). **THE SYSTEM SHALL** log a `WARN` line with the attempt ID and the exception class.

#### Scenario: Prompt build throws
- **WHEN** `buildInterviewerContext` throws for an attempt
- **THEN** the candidate receives the configured question in the same response, with no wait for the deadline.

### Requirement: REQ-AC-24 Bounded Streaming Threads
**THE SYSTEM SHALL** run interviewer streams on a bounded thread pool. **IF** the pool and its queue are full **THEN THE SYSTEM SHALL** answer `503` ProblemDetail.

#### Scenario: Pool full
- **WHEN** all stream threads are busy and the queue is full
- **THEN** a new answer submission gets `503` ProblemDetail, and the ledger stays unchanged.
