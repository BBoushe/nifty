## ADDED Requirements

### Requirement: REQ-EP-10 Tolerant Judge Output Envelope
**WHEN** a judge reply wraps its JSON object in a code fence or other text **THE SYSTEM SHALL** extract the object and validate it strictly (REQ-EP-08). **THE SYSTEM SHALL** call the judge with temperature `0`.

#### Scenario: Fenced JSON
- **WHEN** the judge replies with the JSON object inside a code fence marked `json`
- **THEN** the run is parsed, and no `JUDGE_PARSE_FAILED` flag is set.

#### Scenario: Invalid object
- **WHEN** the extracted object has `level` 7
- **THEN** the run fails parsing, as before.

### Requirement: REQ-EP-11 Failed Final Try
**IF** an evaluation run stays `RUNNING` past the stale limit on its last allowed try **THEN THE SYSTEM SHALL** set it to `FAILED`.

#### Scenario: Crash on try 3
- **WHEN** the backend stops during try 3 of a run and restarts
- **THEN** the retry scheduler sets the run to `FAILED`, and the matrix shows "evaluation failed".

### Requirement: REQ-EP-12 Professor Re-Run
**WHEN** the professor sends `POST /api/v1/ranking/attempts/{attemptId}/evaluation/retry` for an attempt with a `FAILED` run **THE SYSTEM SHALL** reset the tries, queue the run and write an `EVALUATION_RETRIED` audit log entry. **IF** the run is not `FAILED` **THEN THE SYSTEM SHALL** answer `409`.

#### Scenario: Re-run after 3 failures
- **WHEN** the professor re-runs a run that failed 3 times
- **THEN** the run is `QUEUED` with 0 tries, and the scheduler picks it up.

#### Scenario: Re-run of a completed run
- **WHEN** the professor re-runs a `COMPLETED` run
- **THEN** the system answers `409` and changes nothing.

### Requirement: REQ-EP-13 Recovery of Missed Completions
**IF** an attempt is `COMPLETED` and has no evaluation run **THEN THE SYSTEM SHALL** create the run on the next retry-scheduler pass.

#### Scenario: Crash before enqueue
- **WHEN** the backend stops after an attempt commits and before the evaluation run is created
- **THEN** the run exists after the next scheduler pass, and exactly one run exists for the attempt.
