# Tasks: `invite-links`

## Status
- **Branch:** `feat/invite-links`
- **Worktree:** `../nifty-invite-links`
- **Current:** all tasks done; archived; waiting for the human to approve push and PR/MR
- **Next action:** after approval, `node scripts/forge.mjs push`, then `node scripts/forge.mjs pr ... --base dev`; fill the PR/MR numbers into `docs/feature_history.md`
- **Uncommitted changes:** no
- **PR / MR:** none yet
- **Open review findings:** none

## Tasks

### Task 1: US-IL-01..02 Invite table and creation (REQ-AA-03, REQ-AA-09)
- [x] RED: `InviteServiceTest` (extends `PostgresContainerBase`, `StudioQueryService` mocked): 3 refs → 3 distinct links, 3 hashes, no plain token in the table; a duplicate ref → `400`; a config that `ownedBy` rejects → `404`.
- [x] GREEN: `V4__auth_invites.sql`, entity and repository, `InviteService`, `TokenHasher`, create, list and revoke endpoints (design §2).
- [x] Verify: `cd backend && ./mvnw -B test` → exit 0; commit `feat(auth): invite links with hashed tokens`

### Task 2: US-IL-03 Invite sign-in and candidate principal (REQ-AA-04)
- [x] RED: `InviteSignInTest` (MockMvc): valid token → `200` + session, `GET /api/v1/auth/me` shows `CANDIDATE` with `inviteId` and `configId`; unknown and revoked tokens → same `401`; `currentCandidate()` returns the bound invite.
- [x] GREEN: `POST /api/v1/auth/invite/{token}`, `NiftyPrincipal` candidate fields, `currentCandidate()`.
- [x] Verify: `cd backend && ./mvnw -B test` → exit 0; commit `feat(auth): candidate sign-in by invite link`

### Task 3: US-IL-04 Revocation, scope and purge (REQ-AA-05, REQ-AA-08)
- [x] RED: revoking ends an open candidate session on the next request (`401`); `AssessmentDeletedEvent` deletes the config's invites in the same transaction; a candidate gets `403` on `/api/v1/ranking/**`.
- [x] GREEN: `RevokedInviteFilter`, purge listener.
- [x] Verify: `cd backend && ./mvnw -B test` (incl. `ArchitectureTest`) → exit 0; update `docs/api.md`; commit `feat(auth): revocation, candidate scope and invite purge`

### Task 4: Frontend landing route and studio invite panel
- [x] RED: Vitest: `InviteLandingView.test.ts` (success → `/assessment`, `401` → message); invite panel test (CSV of 3 rows posts 3 refs; revoke calls `DELETE`).
- [x] GREEN: design §3.
- [x] Verify: `cd frontend && npm run typecheck && npm run test && npm run lint && npm run build` → exit 0; commit `feat(frontend): invite landing page and studio invite panel`

### Task 5: Docs
- [x] `docs/architecture.md` (invite flow), `CHANGELOG.md`
- [x] Verify: harness tests → exit 0; commit `docs: record invite-links`

## Delivery
- [x] `git fetch origin && git merge origin/dev` (never rebase a pushed branch, no force pushes)
- [x] Run `cd backend && ./mvnw -B test`, `cd frontend && npm run typecheck && npm run test && npm run lint && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all --strict` and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive invite-links -y`, set its `docs/feature_backlog.md` row to `done`, add a `docs/feature_history.md` entry (`PR/MR: pending`), commit `chore(specs): archive invite-links`
- [ ] Ask the human, then `node scripts/forge.mjs push` and open the PR/MR with `pr-memo` (`node scripts/forge.mjs pr ... --base dev`)
- [ ] Stop. Teammate A runs the cold review, checks CI and merges.

## Handoff Log


### Task 1 (US-IL-01..02, REQ-AA-03, REQ-AA-09)
- Commits: `feat(auth): invite links with hashed tokens`
- Verification: RED: `./mvnw -B test -Dtest=InviteServiceTest` → FAIL (compile: `InviteService`, `TokenHasher`, `CreatedInvite`, `InviteSummary` missing); GREEN: `cd backend && ./mvnw -B test` → exit 0 (181 run, 0 failed)
- Contracts/Migrations added: `V4__auth_invites.sql`, `InviteEntity`, `InviteRepository`, `TokenHasher`, `InviteService`, `InviteController` (`/api/v1/ranking/studio/configs/{configId}/invites/**`)
- Decisions: applies 1.10, 1.17
- Open Questions: none

### Task 2 (US-IL-03, REQ-AA-04)
- Commits: `feat(auth): candidate sign-in by invite link`
- Verification: RED: `./mvnw -B test -Dtest=InviteSignInTest` → FAIL (compile: `NiftyPrincipal.candidate(...)` missing); GREEN: `cd backend && ./mvnw -B test` → exit 0 (184 run, 0 failed)
- Contracts/Migrations added: `POST /api/v1/auth/invite/{token}`, `NiftyPrincipal` candidate fields, `AuthContextServiceImpl.currentCandidate()`, `GET /api/v1/auth/me` candidate fields; capped Hikari test pool size to 3 in `application-test.yml`
- Decisions: applies 1.17, 1.23
- Open Questions: none

### Task 3 (US-IL-04, REQ-AA-05, REQ-AA-08)
- Commits: `feat(auth): revocation, candidate scope and invite purge`
- Verification: RED: `./mvnw -B test -Dtest=InviteRevocationAndPurgeTest` → FAIL (revoked session returned 200; deleted config left 2 invite rows); GREEN: `cd backend && ./mvnw -B test` → exit 0 (186 run, 0 failed, incl. `ArchitectureTest`)
- Contracts/Migrations added: `RevokedInviteFilter`, `InviteService.onAssessmentDeleted(AssessmentDeletedEvent)` (`MANDATORY` tx); `docs/api.md` §4/§5 updated
- Decisions: applies 1.15, 1.23, 4.4
- Open Questions: none

### Task 4 (Frontend)
- Commits: `feat(frontend): invite landing page and studio invite panel`
- Verification: RED: `cd frontend && npm run test` → FAIL (`InviteLandingView.vue` and `InvitePanel.vue` missing); GREEN: `cd frontend && npm run typecheck && npm run test && npm run lint && npm run build` → exit 0 (12 files, 75 passed, 0 lint warnings)
- Contracts/Migrations added: `frontend/src/api/invites.ts`, `authStore.loginWithInvite`, `/invite/:token` (`InviteLandingView.vue`), `InvitePanel.vue` on `AssessmentDetailView.vue` and `/ranking/studio/:configId/invites`
- Decisions: applies 1.14, 1.17, 1.23
- Open Questions: none

### Task 5 (Docs)
- Commits: `docs: record invite-links`
- Verification: RED: n/a (no behaviour); GREEN: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 after archiving
- Contracts/Migrations added: none
- Decisions: applies 1.17, 1.23, 4.4
- Open Questions: none
