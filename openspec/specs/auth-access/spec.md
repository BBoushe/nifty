# Domain Spec: Auth & Access (`auth-access`)

> **Status:** Added 2026-11-25 by `nov24-reconciliation` (Decisions 1.15, 1.17)
> **Primary View:** both views (sign-in), backend `auth` slice

## Purpose

Authenticate professors and candidates and limit each route to the roles that may use it. MVP has two roles; adding a role changes one enum and the central access rules (Decisions 1.15, 1.17).

## Requirements

### Requirement: REQ-AA-01 Two Extensible Roles
**THE SYSTEM SHALL** define the roles `PROFESSOR` and `CANDIDATE` in one enum and keep all route access rules in one place (`SecurityConfig` plus method-level `@PreAuthorize`). **THE SYSTEM SHALL** allow `/api/v1/ranking/**` only for `PROFESSOR` and `/api/v1/assessment/**` only for `CANDIDATE`.

#### Scenario: Candidate calls a professor route
- **WHEN** an authenticated `CANDIDATE` calls `GET /api/v1/ranking/matrix`
- **THEN** the system answers `403 Forbidden`.

#### Scenario: Professor calls a candidate route
- **WHEN** an authenticated `PROFESSOR` calls `POST /api/v1/assessment/sessions`
- **THEN** the system answers `403 Forbidden`.

### Requirement: REQ-AA-02 Professor Sign-In
**WHEN** a professor submits valid account credentials **THE SYSTEM SHALL** start an authenticated `PROFESSOR` session. **IF** the credentials are not valid **THEN THE SYSTEM SHALL** answer `401` without saying which part was wrong.

#### Scenario: Wrong password
- **WHEN** a professor signs in with a known email and a wrong password
- **THEN** the system answers `401` with the same detail it gives for an unknown email.

### Requirement: REQ-AA-03 Invite Link Creation
**WHEN** the professor creates invite links, one at a time or by CSV import, **THE SYSTEM SHALL** generate one random token of at least 128 bits per candidate, bind it to one assessment and one candidate, and store only a hash of the token.

#### Scenario: CSV import of 3 candidates
- **WHEN** the professor imports a CSV with 3 candidate rows for assessment `cfg_7`
- **THEN** the system returns 3 distinct invite links, and the database holds 3 token hashes but no plain token.

### Requirement: REQ-AA-04 Invite Link Sign-In
**WHEN** a candidate opens a valid invite link **THE SYSTEM SHALL** start an authenticated `CANDIDATE` session bound to that assessment and candidate. **IF** the token is unknown or revoked **THEN THE SYSTEM SHALL** answer `401`. The link stays valid after the attempts run out, so the candidate can open released results.

#### Scenario: Revoked invite link
- **WHEN** a candidate opens an invite link that the professor revoked
- **THEN** the system answers `401` and starts no session.

### Requirement: REQ-AA-05 Candidate Data Scope
**WHILE** a `CANDIDATE` session is active **THE SYSTEM SHALL** limit every read and write to the assessment and candidate bound to its invite link.

#### Scenario: Candidate requests another session
- **WHEN** candidate `cand_042` calls `GET /api/v1/assessment/sessions/{id}` with the session ID of `cand_043`
- **THEN** the system answers `403 Forbidden`.

### Requirement: REQ-AA-06 Session Cookie, CSRF and Logout
**THE SYSTEM SHALL** keep signed-in state in a server session with an `HttpOnly`, `SameSite=Lax` cookie, require a CSRF token on every state-changing request, and end the session on `POST /api/v1/auth/logout` (Decision 1.23).

#### Scenario: POST without CSRF token
- **WHEN** a signed-in professor sends `POST /api/v1/auth/logout` without the `X-XSRF-TOKEN` header
- **THEN** the system answers `403` and the session stays active.

#### Scenario: Logout
- **WHEN** a signed-in professor sends `POST /api/v1/auth/logout` with a valid CSRF token
- **THEN** the system answers `204`, and the next `GET /api/v1/auth/me` with the old cookie answers `401`.

### Requirement: REQ-AA-07 Bootstrap Professor Account
**WHERE** `NIFTY_BOOTSTRAP_PROFESSOR_EMAIL` and `NIFTY_BOOTSTRAP_PROFESSOR_PASSWORD` are set **THE SYSTEM SHALL** create that professor account at startup if no account has that email, store only a password hash, and **SHALL NOT** change an existing account (Decision 1.24).

#### Scenario: Second start
- **WHEN** the backend starts twice with the same bootstrap values and the professor changed nothing in between
- **THEN** exactly one account exists, and its password hash did not change on the second start.

### Requirement: REQ-AA-08 Revocation Ends Candidate Sessions
**WHEN** the professor revokes an invite link **THE SYSTEM SHALL** reject the next request of every session started with that link with `401` and end those sessions (Decision 1.23).

#### Scenario: Revoked during an interview
- **WHEN** the professor revokes the invite of a candidate who has an open session
- **THEN** the candidate's next request answers `401` and the session ends.

### Requirement: REQ-AA-09 Invites Only for Own Assessments
**IF** a professor creates, lists or revokes invites for an assessment that does not exist or that another professor owns **THEN THE SYSTEM SHALL** answer `404` and change nothing.

#### Scenario: Other professor's assessment
- **WHEN** professor A posts invites for professor B's assessment
- **THEN** the system answers `404` and creates no invite.

### Requirement: REQ-AA-10 New Link After a Revoke
**WHEN** the professor creates an invite for a candidate with a revoked invite in that assessment **THE SYSTEM SHALL** issue a new token for the same invite (Decision 2.34). The old token and its sessions stay invalid. **THE SYSTEM SHALL** write an `INVITE_REISSUED` audit log entry. The candidate's attempts and results stay linked to the invite.

#### Scenario: Leaked link replaced
- **WHEN** the professor revokes the invite of `anna.schmidt` and then creates an invite for `anna.schmidt` again
- **THEN** the new link signs in, the old link answers `401`, and the attempt count of `anna.schmidt` stays the same.

### Requirement: REQ-AA-11 Concurrent Invite Creation
**WHEN** two requests create the same invite at the same time **THE SYSTEM SHALL** create one invite and answer the other request with `409`. **THE SYSTEM SHALL NOT** answer `500`.

#### Scenario: Double click on Add
- **WHEN** two add requests for `anna.schmidt` arrive within 10 ms
- **THEN** one invite exists, one response is `201`, and the other is `409`.

### Requirement: REQ-AA-12 CSV Quoted Fields
**WHEN** the professor imports a CSV **THE SYSTEM SHALL** read quoted fields per RFC 4180, including commas, doubled quotes and line breaks inside quotes.

#### Scenario: Line break inside quotes
- **WHEN** a CSV row has the candidate field `"Schmidt,\nAnna"`
- **THEN** the import creates one invite for that row and no extra row.

### Requirement: REQ-AA-13 CSRF Token Renewal on Sign-In
**WHEN** a professor or candidate signs in **THE SYSTEM SHALL** issue a new CSRF token and reject the token from before the sign-in with `403`.

#### Scenario: Old token after login
- **WHEN** a client signs in and then sends a POST with the CSRF token it held before the sign-in
- **THEN** the system answers `403`.

### Requirement: REQ-AA-14 Sign-In Rate Limit
**IF** professor sign-in fails 5 times for one email within 15 minutes **THEN THE SYSTEM SHALL** answer `429` ProblemDetail with `Retry-After`. A successful sign-in resets the count.

#### Scenario: Sixth wrong password
- **WHEN** a client sends a sixth wrong password for `prof@example.org` within 15 minutes
- **THEN** the system answers `429` with a `Retry-After` header, also for a correct password.

### Requirement: REQ-AA-15 Bootstrap Password Length
**IF** the bootstrap professor password is longer than 72 bytes in UTF-8 **THEN THE SYSTEM SHALL** stop at startup with an error that names the limit and not the password.

#### Scenario: 80-byte password
- **WHEN** the backend starts with an 80-byte bootstrap password
- **THEN** startup fails, and the log names the 72-byte limit without the password.
