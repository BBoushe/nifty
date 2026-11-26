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
WHILE a `CANDIDATE` is signed in, THE SYSTEM SHALL show the header links "Interview" (`/assessment`) and "Results" (`/assessment/results/{configId}` of the candidate's invite) and a "Sign out" button.

#### Scenario: Candidate sees candidate links
- **WHEN** a candidate session for assessment `C` is active
- **THEN** the header shows "Interview", "Results" linking to `/assessment/results/C`, and "Sign out"
- **AND** the header shows no professor link

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
WHEN a signed-in user selects "Sign out", THE SYSTEM SHALL end the session through `POST /api/v1/auth/logout` and show `/sign-in`.

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
