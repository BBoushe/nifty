# Spec Delta: Auth & Access (`auth-accounts`)

## ADDED Requirements

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
