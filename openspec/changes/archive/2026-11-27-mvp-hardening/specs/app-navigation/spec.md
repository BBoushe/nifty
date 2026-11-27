## ADDED Requirements

### Requirement: REQ-NAV-10 Unknown Route
**IF** a user opens a path that matches no route **THEN THE SYSTEM SHALL** show a "Page not found" page with a link to the role start page.

#### Scenario: Mistyped address
- **WHEN** a signed-in professor opens `/rankng`
- **THEN** the page shows "Page not found" and a link to Studio.

### Requirement: REQ-NAV-11 Ranking Links
**THE SYSTEM SHALL** keep the Ranking URL and the selected assessment the same. **WHEN** an assessment is selected **THE SYSTEM SHALL** show a "Build teams" link to its Team Builder.

#### Scenario: Direct link to another assessment
- **WHEN** the professor is on `/ranking/configs/A` and opens `/ranking/configs/B`
- **THEN** the page shows assessment B.

#### Scenario: Dropdown change
- **WHEN** the professor selects assessment B in the dropdown
- **THEN** the URL changes to `/ranking/configs/B`.

### Requirement: REQ-NAV-12 Invites Section Link
**WHEN** the professor follows "Send to students" **THE SYSTEM SHALL** open the assessment detail page scrolled to the invites section.

#### Scenario: Send to students
- **WHEN** the professor opens `/ranking/studio/{configId}/invites`
- **THEN** the invites section is in view.

### Requirement: REQ-NAV-13 Server Errors in the Shell
**IF** the backend cannot be reached when the app loads **THEN THE SYSTEM SHALL** show "Server not reachable" and a Retry button. **IF** sign-out fails **THEN THE SYSTEM SHALL** show an error message and keep the user on the page.

#### Scenario: Backend down
- **WHEN** the browser opens the app and `GET /api/v1/auth/me` fails with a network error
- **THEN** the page shows "Server not reachable" and a Retry button.

#### Scenario: Sign-out fails
- **WHEN** `POST /api/v1/auth/logout` answers `500`
- **THEN** the page shows "Sign-out failed. Try again." and does not navigate.

## MODIFIED Requirements

### Requirement: REQ-NAV-02 Candidate Header Links
WHILE a `CANDIDATE` is signed in, THE SYSTEM SHALL show the header links "Interview" (`/assessment`) and "Results" (`/assessment/results/{configId}` of the candidate's invite) and no "Sign out" button (Decision 2.36).

#### Scenario: Candidate sees candidate links
- **WHEN** a candidate session for assessment `C` is active
- **THEN** the header shows "Interview" and "Results" linking to `/assessment/results/C`
- **AND** the header shows no professor link and no "Sign out" button

### Requirement: REQ-NAV-05 Sign Out
WHEN a signed-in professor selects "Sign out", THE SYSTEM SHALL end the session through `POST /api/v1/auth/logout` and show `/sign-in`.

#### Scenario: Sign out ends the session
- **WHEN** a professor selects "Sign out"
- **THEN** the app calls the logout endpoint
- **AND** the app shows `/sign-in` with the signed-out header
