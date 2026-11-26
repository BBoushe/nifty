# Tasks: `invite-links`

## Status
- **Branch:** `feat/invite-links`
- **Worktree:** `../nifty-invite-links`
- **Current:** not started
- **Next action:** Task 1
- **Uncommitted changes:** no
- **PR / MR:** none yet
- **Open review findings:** none

## Tasks

### Task 1: US-IL-01..02 Invite table and creation (REQ-AA-03, REQ-AA-09)
- [ ] RED: `InviteServiceTest` (extends `PostgresContainerBase`, `StudioQueryService` mocked): 3 refs → 3 distinct links, 3 hashes, no plain token in the table; a duplicate ref → `400`; a config that `ownedBy` rejects → `404`.
- [ ] GREEN: `V4__auth_invites.sql`, entity and repository, `InviteService`, `TokenHasher`, create, list and revoke endpoints (design §2).
- [ ] Verify: `cd backend && ./mvnw -B test` → exit 0; commit `feat(auth): invite links with hashed tokens`

### Task 2: US-IL-03 Invite sign-in and candidate principal (REQ-AA-04)
- [ ] RED: `InviteSignInTest` (MockMvc): valid token → `200` + session, `GET /api/v1/auth/me` shows `CANDIDATE` with `inviteId` and `configId`; unknown and revoked tokens → same `401`; `currentCandidate()` returns the bound invite.
- [ ] GREEN: `POST /api/v1/auth/invite/{token}`, `NiftyPrincipal` candidate fields, `currentCandidate()`.
- [ ] Verify: `cd backend && ./mvnw -B test` → exit 0; commit `feat(auth): candidate sign-in by invite link`

### Task 3: US-IL-04 Revocation, scope and purge (REQ-AA-05, REQ-AA-08)
- [ ] RED: revoking ends an open candidate session on the next request (`401`); `AssessmentDeletedEvent` deletes the config's invites in the same transaction; a candidate gets `403` on `/api/v1/ranking/**`.
- [ ] GREEN: `RevokedInviteFilter`, purge listener.
- [ ] Verify: `cd backend && ./mvnw -B test` (incl. `ArchitectureTest`) → exit 0; update `docs/api.md`; commit `feat(auth): revocation, candidate scope and invite purge`

### Task 4: Frontend landing route and studio invite panel
- [ ] RED: Vitest: `InviteLandingView.test.ts` (success → `/assessment`, `401` → message); invite panel test (CSV of 3 rows posts 3 refs; revoke calls `DELETE`).
- [ ] GREEN: design §3.
- [ ] Verify: `cd frontend && npm run typecheck && npm run test && npm run lint && npm run build` → exit 0; commit `feat(frontend): invite landing page and studio invite panel`

### Task 5: Docs
- [ ] `docs/architecture.md` (invite flow), `CHANGELOG.md`
- [ ] Verify: harness tests → exit 0; commit `docs: record invite-links`

## Delivery
- [ ] `git fetch origin && git merge origin/dev` (never rebase a pushed branch, no force pushes)
- [ ] Run `cd backend && ./mvnw -B test`, `cd frontend && npm run typecheck && npm run test && npm run lint && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all --strict` and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive invite-links -y`, set its `docs/feature_backlog.md` row to `done`, add a `docs/feature_history.md` entry (`PR/MR: pending`), commit `chore(specs): archive invite-links`
- [ ] Ask the human, then `node scripts/forge.mjs push` and open the PR/MR with `pr-memo` (`node scripts/forge.mjs pr ... --base dev`)
- [ ] Stop. Teammate A runs the cold review, checks CI and merges.

## Handoff Log
