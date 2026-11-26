# Tasks: Frontend Integration (`frontend-integration`)

## Status
- **Branch:** `feat/frontend-integration` (branch off `dev`)
- **Worktree:** `../nifty-frontend-integration`
- **Current:** US-NAV-02 done
- **Next action:** US-NAV-03
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

- [ ] **US-NAV-03: Links between pages (REQ-NAV-06, 07)**
  - [ ] RED: Studio detail shows "Open ranking" and "Build teams"; the ranking empty state links to `/ranking/studio/new`; the completed interview links to the results page.
  - [ ] GREEN: add the `RouterLink`s in `AssessmentDetailView.vue`, `RankingView.vue` and `AssessmentChatView.vue`.
  - [ ] Verify: `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`.

## Delivery

- [ ] `git fetch origin && git merge origin/dev`
- [ ] `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all --strict`, `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`
- [ ] Archive, `docs/feature_history.md` entry, push, PR/MR, review, human merge approval, `forge.mjs sync dev`

## Handoff Log

### US-NAV-01 (2e31f15 feat(frontend): role-aware header and sign out)
- Commits: 2e31f15 feat(frontend): role-aware header and sign out
- Verification: RED: `npm run test -- src/App.test.ts` → FAIL (4 failed, no nav-* test ids); GREEN: `npm run test -- src/App.test.ts` → exit 0 (4 run)
- Contracts/Migrations added: none
- Decisions: none
- Open Questions: none

### US-NAV-02 (feat(frontend): role start page and studio sign-in default)
- Commits: see `git log` (feat(frontend): role start page and studio sign-in default)
- Verification: RED: `npm run test -- src/router src/views/auth` → FAIL (6 failed: `/` went to `/ranking`, sign-in default `/ranking`); GREEN: same command → exit 0 (21 run)
- Contracts/Migrations added: none
- Decisions: none (design deviation: `beforeEnter` guard instead of a redirect function, because vue-router 4 does not await redirect functions)
- Open Questions: none
