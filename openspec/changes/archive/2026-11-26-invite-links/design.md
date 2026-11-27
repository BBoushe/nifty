# Design: Invite Links

## 1. Sources

`openspec/specs/auth-access/spec.md` (REQ-AA-03..05), Decisions 1.17, 1.23, 4.4, `docs/tickets/00-parallel-waves-and-contracts.md` §1 and §3, `docs/api.md` §5, the archived `auth-accounts` change.

## 2. Backend

- **Migration `V4__auth_invites.sql`:** `invite(id UUID PK, config_id UUID NOT NULL, candidate_ref VARCHAR(256) NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE, revoked BOOLEAN NOT NULL DEFAULT FALSE, created_by UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE (config_id, candidate_ref))`. No foreign key to `assessment_config` (contract pack §1).
- **Token:** 32 bytes from `SecureRandom`, Base64url without padding (256 bits). Store only the SHA-256 hex. The plain token appears once, in the create response. Link: `${nifty.frontend-base-url}/invite/<token>` (default `http://localhost:5173`).
- **Ownership:** `StudioQueryService.ownedBy(configId, professorId)` (added by `studio-assessment-creation`). An unknown config or another professor's config gives `404`.
- **Endpoints:**
  - `POST /api/v1/ranking/studio/configs/{id}/invites` `{candidateRefs: [1..500 non-blank, unique]}` → `201` `[{inviteId, candidateRef, link}]`. A duplicate `candidateRef` in the request or in the database gives `400` ProblemDetail naming it. The studio UI turns a CSV file (one `candidate_ref` column) into this list in the browser.
  - `GET /api/v1/ranking/studio/configs/{id}/invites` → `[{inviteId, candidateRef, revoked, createdAt}]` (no token).
  - `DELETE /api/v1/ranking/studio/configs/{id}/invites/{inviteId}` → revoke, `204`.
  - `POST /api/v1/auth/invite/{token}` (public) → `200` `{configId}` and a `CANDIDATE` session. Unknown or revoked token → `401`, same detail for both.
- **Principal:** extend `NiftyPrincipal` with `inviteId`, `configId`, `candidateRef` for candidates. `currentCandidate()` returns `CandidateSessionPrincipal` and checks that the invite is not revoked; a revoked invite gives `401`.
- **Revocation (Decision 1.23):** `auth.web.RevokedInviteFilter` (`OncePerRequestFilter` bean, runs after the security chain) invalidates a candidate session whose invite is revoked and answers `401`.
- **Scope (REQ-AA-05):** slices compare `currentCandidate().configId()` and `inviteId()` with the requested data. `GET /api/v1/auth/me` returns `{role, inviteId, configId}` for a candidate.
- **Purge (Decision 4.4):** a synchronous `@EventListener` with `@Transactional(propagation = MANDATORY)` on `AssessmentDeletedEvent` deletes the invites of that config.

## 3. Frontend

- `views/auth/routes.ts`: `/invite/:token` → `InviteLandingView.vue` calls `POST /api/v1/auth/invite/{token}`, then goes to `/assessment`. A `401` shows "This link is not valid."
- `views/studio/`: an invite panel on the assessment detail page: add one candidate, import a CSV, list invites, revoke, copy or download links.
