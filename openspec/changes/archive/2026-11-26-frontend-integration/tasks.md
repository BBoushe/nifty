# Tasks: Frontend Integration (`frontend-integration`)

## Status
- **Branch:** `feat/frontend-integration` (branch off `dev`)
- **Worktree:** `../nifty-frontend-integration`
- **Current:** US-NAV-01..03 done and verified; Delivery (merge, archive, push, PR) left to the orchestrator
- **Next action:** Delivery steps
- **Uncommitted changes:** no
- **PR / MR:** —
- **Open review findings:** —

## Tasks

- [x] **US-NAV-01: Role-aware header and sign out (REQ-NAV-01, 02, 03, 05)**
  - [x] RED: `frontend/src/App.test.ts` — professor, candidate and signed-out headers; sign out calls logout and shows `/sign-in`.
  - [x] GREEN: `App.vue` reads `useAuthStore().me`, calls `fetchMe()` on mount, renders the links and the sign-out button.
  - [x] Verify: `cd frontend && npm run test -- src/App.test.ts`.

- [x] **US-NAV-02: Role start page and sign-in default (REQ-NAV-04, 08)**
  - [x] RED: `router/index.test.ts` resolves `/` to the role start page; `SignInView.test.ts` plain sign-in opens `/ranking/studio`.
  - [x] GREEN: `beforeEnter` guard for `/` (vue-router does not await a redirect function); `safeRedirectTarget()` default `/ranking/studio`.
  - [x] Verify: `cd frontend && npm run test -- src/router src/views/auth`.

- [x] **US-NAV-03: Links between pages (REQ-NAV-06, 07)**
  - [x] RED: Studio detail shows "Open ranking" and "Build teams"; the ranking empty state links to `/ranking/studio/new`; the completed interview links to the results page.
  - [x] GREEN: add the `RouterLink`s in `AssessmentDetailView.vue`, `RankingView.vue` and `AssessmentChatView.vue`.
  - [x] Verify: `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`.

## Delivery

- [x] `git fetch origin && git merge origin/dev` (dev unchanged since the branch point e54f67a)
- [x] `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all --strict`, `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`
- [ ] Archive, `docs/feature_history.md` entry, push, PR/MR, review, human merge approval, `forge.mjs sync dev`

## Handoff Log

### US-NAV-01 (2e31f15 feat(frontend): role-aware header and sign out)
- Commits: 2e31f15 feat(frontend): role-aware header and sign out
- Verification: RED: `npm run test -- src/App.test.ts` → FAIL (4 failed, no nav-* test ids); GREEN: `npm run test -- src/App.test.ts` → exit 0 (4 run)
- Contracts/Migrations added: none
- Decisions: none
- Open Questions: none

### US-NAV-02 (c22b84c feat(frontend): role start page and studio sign-in default)
- Commits: c22b84c feat(frontend): role start page and studio sign-in default
- Verification: RED: `npm run test -- src/router src/views/auth` → FAIL (6 failed: `/` went to `/ranking`, sign-in default `/ranking`); GREEN: same command → exit 0 (21 run)
- Contracts/Migrations added: none
- Decisions: none (design deviation: `beforeEnter` guard instead of a redirect function, because vue-router 4 does not await redirect functions)
- Open Questions: none

### US-NAV-03 (1d46dd7 feat(frontend): link studio, ranking, teams and results pages)
- Commits: 1d46dd7 feat(frontend): link studio, ranking, teams and results pages
- Verification: RED: `npm run test -- src/views/studio src/views/ranking src/views/assessment` → FAIL (3 failed: no open-ranking, build-teams, empty-state or results link); GREEN: `npm run typecheck && npm run lint && npm run test && npm run build` → exit 0 (111 run)
- Contracts/Migrations added: none
- Decisions: none (chat results link reads `configId` from the auth store; chat test now mounts pinia and router)
- Open Questions: none

### Review fix (orchestrator, after the cold review APPROVE)
- Commits: `fix(frontend): share one /auth/me request and keep the newest answer`
- Verification: RED: `npx vitest run src/stores/auth.test.ts` → FAIL (2 new tests: two concurrent checks made 2 requests; a slow 401 from before an invite sign-in overwrote the candidate session); GREEN: same → exit 0 (5 run); `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build` → exit 0 (113 run)
- Contracts/Migrations added: none. `fetchMe` shares an in-flight request and applies only the newest answer; the header hides "Results" when `configId` is missing.
- Decisions: none
- Open Questions: backend down still leaves `/` blank with no message (not a regression); a failed logout gives no message.

