# app-navigation Specification

## Purpose

The shared app shell lets each role find its pages. It covers the header links per role, the start page, sign out, and the links between the professor and candidate pages.

## Requirements

### Requirement: REQ-NAV-01 Professor Header Links
WHILE a `PROFESSOR` is signed in, THE SYSTEM SHALL show the header links "Studio" (`/ranking/studio`) and "Ranking" (`/ranking`) and a "Sign out" button.

#### Scenario: Professor sees professor links
- **WHEN** a professor session is active
- **THEN** the header shows "Studio", "Ranking" and "Sign out"
- **AND** the header shows no candidate link

### Requirement: REQ-NAV-02 Candidate Header Links
WHILE a `CANDIDATE` is signed in, THE SYSTEM SHALL show the header links "Interview" (`/assessment`) and "Results" (`/assessment/results/{configId}` of the candidate's invite) and no "Sign out" button (Decision 2.36).

#### Scenario: Candidate sees candidate links
- **WHEN** a candidate session for assessment `C` is active
- **THEN** the header shows "Interview" and "Results" linking to `/assessment/results/C`
- **AND** the header shows no professor link and no "Sign out" button

### Requirement: REQ-NAV-03 Signed-Out Header
WHILE no user is signed in, THE SYSTEM SHALL show only a "Sign in" link (`/sign-in`) in the header navigation.

#### Scenario: Visitor sees only sign in
- **WHEN** no session exists
- **THEN** the header shows "Sign in" and no professor or candidate link

### Requirement: REQ-NAV-04 Role Start Page
WHEN a user opens `/`, THE SYSTEM SHALL send a professor to `/ranking/studio`, a candidate to `/assessment`, and a visitor without a session to `/sign-in`.

#### Scenario: Start page per role
- **WHEN** a professor opens `/`
- **THEN** the app shows `/ranking/studio`
- **WHEN** a candidate opens `/`
- **THEN** the app shows `/assessment`
- **WHEN** a visitor opens `/`
- **THEN** the app shows `/sign-in`

### Requirement: REQ-NAV-05 Sign Out
WHEN a signed-in professor selects "Sign out", THE SYSTEM SHALL end the session through `POST /api/v1/auth/logout` and show `/sign-in`.

#### Scenario: Sign out ends the session
- **WHEN** a professor selects "Sign out"
- **THEN** the app calls the logout endpoint
- **AND** the app shows `/sign-in` with the signed-out header

### Requirement: REQ-NAV-06 Links Between Professor Pages
THE SYSTEM SHALL link the Studio detail page of assessment `C` to `/ranking/configs/C` ("Open ranking") and `/ranking/teams/C` ("Build teams"), and SHALL link the ranking empty state to `/ranking/studio/new`.

#### Scenario: Studio detail links
- **WHEN** a professor opens the Studio detail page of assessment `C`
- **THEN** the page shows "Open ranking" to `/ranking/configs/C` and "Build teams" to `/ranking/teams/C`

#### Scenario: Ranking empty state links to Studio
- **WHEN** a professor has no assessment and opens `/ranking`
- **THEN** the empty state links to `/ranking/studio/new`

### Requirement: REQ-NAV-07 Results Link After the Interview
WHEN a candidate's interview is complete, THE SYSTEM SHALL show a link to `/assessment/results/{configId}`.

#### Scenario: Completed interview links to results
- **WHEN** the interview of a candidate for assessment `C` reaches `COMPLETED`
- **THEN** the page shows a link to `/assessment/results/C`

### Requirement: REQ-NAV-08 Professor Sign-In Default
WHEN a professor signs in without a safe `redirect` query value, THE SYSTEM SHALL show `/ranking/studio`.

#### Scenario: Plain professor sign-in opens Studio
- **WHEN** a professor signs in at `/sign-in` with no `redirect`
- **THEN** the app shows `/ranking/studio`

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
