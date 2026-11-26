# Tasks: Frontend Integration (`frontend-integration`)

## Status
- **Branch:** `feat/frontend-integration` (branch off `dev`)
- **Worktree:** `../nifty-frontend-integration`
- **Current:** spec written
- **Next action:** US-NAV-01
- **Uncommitted changes:** yes
- **PR / MR:** —
- **Open review findings:** —

## Tasks

- [ ] **US-NAV-01: Role-aware header and sign out (REQ-NAV-01, 02, 03, 05)**
  - [ ] RED: `frontend/src/App.test.ts` — professor, candidate and signed-out headers; sign out calls logout and shows `/sign-in`.
  - [ ] GREEN: `App.vue` reads `useAuthStore().me`, calls `fetchMe()` on mount, renders the links and the sign-out button.
  - [ ] Verify: `cd frontend && npm run test -- src/App.test.ts`.

- [ ] **US-NAV-02: Role start page and sign-in default (REQ-NAV-04, 08)**
  - [ ] RED: `router/index.test.ts` resolves `/` to the role start page; `SignInView.test.ts` plain sign-in opens `/ranking/studio`.
  - [ ] GREEN: function redirect for `/`; `safeRedirectTarget()` default `/ranking/studio`.
  - [ ] Verify: `cd frontend && npm run test -- src/router src/views/auth`.

- [ ] **US-NAV-03: Links between pages (REQ-NAV-06, 07)**
  - [ ] RED: Studio detail shows "Open ranking" and "Build teams"; the ranking empty state links to `/ranking/studio/new`; the completed interview links to the results page.
  - [ ] GREEN: add the `RouterLink`s in `AssessmentDetailView.vue`, `RankingView.vue` and `AssessmentChatView.vue`.
  - [ ] Verify: `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`.

## Delivery

- [ ] `git fetch origin && git merge origin/dev`
- [ ] `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all --strict`, `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`
- [ ] Archive, `docs/feature_history.md` entry, push, PR/MR, review, human merge approval, `forge.mjs sync dev`

## Handoff Log
