# Spec Delta: Frontend Foundation (`frontend-foundation`)

## ADDED Requirements

### Requirement: REQ-FF-01 Score Badge Never Shows N/A as Zero
**THE SYSTEM SHALL** show a score as `<level>/4 · <label>` for levels `0..4`, and show a missing score as `N/A · not enough evidence` on a hatched fill, never as `0`.

#### Scenario: N/A badge
- **WHEN** `ScoreBadge` renders with no level
- **THEN** it shows `N/A · not enough evidence` with the hatch class and no digit `0`.

### Requirement: REQ-FF-02 Problem Detail Parsing
**WHEN** the backend answers `application/problem+json` **THE SYSTEM SHALL** raise an `ApiProblem` that holds `status`, `title`, `detail` and every `errors[]` entry.

#### Scenario: Validation error with two fields
- **WHEN** the backend answers `400` with 2 entries in `errors`
- **THEN** `apiFetch` throws an `ApiProblem` with `status = 400` and 2 field errors.

### Requirement: REQ-FF-03 SSE Stream From a POST
**WHEN** a `POST` response has `text/event-stream` **THE SYSTEM SHALL** yield each SSE event in order and end when the stream closes.

#### Scenario: Three events
- **WHEN** the response body streams 3 `data:` events split across chunks
- **THEN** `postSse` yields 3 events in order and then ends.
