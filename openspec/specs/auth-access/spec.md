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
