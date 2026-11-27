## ADDED Requirements

### Requirement: REQ-NAV-09 Wrong-Role Route
WHEN a signed-in user opens a route of the other role, THE SYSTEM SHALL send the user to the start page of the user's own role (REQ-NAV-04), not to `/sign-in`.

#### Scenario: Candidate opens a professor route
- **WHEN** a candidate session is active and the user opens `/ranking`
- **THEN** the app shows `/assessment`

#### Scenario: Professor opens a candidate route
- **WHEN** a professor session is active and the user opens `/assessment/results/C`
- **THEN** the app shows `/ranking/studio`

#### Scenario: Visitor still signs in
- **WHEN** no session exists and the user opens `/ranking`
- **THEN** the app shows `/sign-in` with `redirect=/ranking`
