# Proposal: Invite Links (`invite-links`)

## Context & Problem

Candidates sign in only through a personal invite link (Decision 1.17). No invite table, link creation, link sign-in or candidate session exists yet. Assessment and evaluation need `AuthContextService.currentCandidate()`.

## Scope

- US-IL-01..02: `invite` table (`V4__auth_invites.sql`) and link creation for one or many candidates on a professor's own assessment.
- US-IL-03: `POST /api/v1/auth/invite/{token}` starts a `CANDIDATE` session; `currentCandidate()` returns the bound invite.
- US-IL-04: candidate scope, revocation that ends sessions at once, and the `AssessmentDeletedEvent` purge.
- Frontend: the `/invite/:token` landing route (`views/auth/`) and the invite panel on the studio detail page (`views/studio/`).

## Out of Scope

- Email delivery of links. The professor copies or downloads them.
