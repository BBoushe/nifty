# Proposal: Professor Accounts and Sessions (`auth-accounts`)

## Context & Problem

No one can sign in. `SecurityConfig` still uses HTTP Basic with a generated password, and `AuthContextService` is a placeholder. Studio needs the signed-in professor's ID, and the frontend needs a sign-in page and route guards.

## Scope

- US-AU-01: `professor_account` (`V2__auth_accounts.sql`), login, logout and `GET /api/v1/auth/me` with server session cookies and CSRF (Decision 1.23); the env bootstrap account (Decision 1.24); `AuthContextServiceImpl.currentProfessorId()`.
- US-AU-02: the sign-in page in `views/auth/`, an auth store, route guards for `/ranking/*` and `/assessment/*`, and the CSRF header in the shared API client.

## Out of Scope

- Invite links and candidate sessions (`invite-links`). Until then `currentCandidate()` answers `403`.
- Password reset and sign-up.
