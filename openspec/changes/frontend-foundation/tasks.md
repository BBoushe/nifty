# Tasks: `frontend-foundation`

## Status
- **Branch:** `feat/frontend-foundation`
- **Worktree:** `/Users/alek/Developer/Programming/nifty-frontend-foundation`
- **Current:** Tasks 1-5 done; `## Delivery` not started
- **Next action:** Delivery (merge `origin/dev`, archive, push, PR/MR)
- **Uncommitted changes:** no
- **PR / MR:** none yet
- **Open review findings:** none

## Tasks

### Task 1: US-FF-01 Vitest and CI test step
- [x] RED: a first `*.test.ts` (for example for `src/api/health.ts`); `npm run test` fails because no script exists.
- [x] GREEN: `vitest`, `@vue/test-utils`, `jsdom`, `src/test/echarts-mock.ts`, `"test": "vitest run"`; `npm run test` in the GitHub and GitLab frontend jobs (design §2).
- [x] Verify: `cd frontend && npm run test && npm run typecheck && npm run lint && npm run build` → exit 0; `node --test scripts/harness-scripts.test.mjs` → exit 0; commit `test(frontend): add Vitest and the CI test step`

### Task 2: US-FF-02 PrimeVue preset and ScoreBadge (REQ-FF-01)
- [x] RED: `ScoreBadge.test.ts` for levels `0..4` and `N/A` (no `0`, hatch class).
- [x] GREEN: PrimeVue, `@theme` tokens, `src/theme/preset.ts`, `src/components/ScoreBadge.vue`.
- [x] Verify: frontend checks → exit 0; commit `feat(frontend): add the PrimeVue preset and ScoreBadge`

### Task 3: US-FF-03 Feature folders and route files
- [x] RED: `router/index.test.ts` checks the existing paths resolve to the moved views.
- [x] GREEN: move the two views; add the five `routes.ts` files; spread them in `router/index.ts`.
- [x] Verify: frontend checks → exit 0; commit `refactor(frontend): feature folders with one routes file each`

### Task 4: US-FF-04 Shared API client (REQ-FF-02, REQ-FF-03)
- [x] RED: `client.test.ts` for `ApiProblem` (2 field errors) and `postSse` (3 events across chunks).
- [x] GREEN: `src/api/client.ts`; `health.ts` uses `apiFetch`.
- [x] Verify: frontend checks → exit 0; commit `feat(frontend): add the shared API client`

### Task 5: Docs
- [x] `docs/architecture.md` (frontend structure), `docs/feature_backlog.md` row 2 → done, `CHANGELOG.md`
- [x] Verify: harness tests → exit 0; commit `docs: record frontend-foundation`

## Delivery
- [ ] `git fetch origin && git merge origin/dev` (after `backend-foundation` merges; keep both sides in the CI files and shared docs)
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive frontend-foundation -y --skip-specs`, history entry (`PR/MR: pending`)
- [ ] `node scripts/forge.mjs push`; PR/MR with `pr-memo`
- [ ] Cold review (fresh `reviewer` agent)
- [ ] CI green on both forges (`node scripts/forge.mjs prs`)
- [ ] Ask the user with the architecture summary, then merge on GitHub and `node scripts/forge.mjs sync dev`
- [ ] Branch and worktree cleanup

## Handoff Log

### Task 1: Vitest and CI test step
- Commits: `c86e47c test(frontend): add Vitest and the CI test step`
- Verification: RED: `cd frontend && npm run test` → FAIL (`Missing script: "test"`); GREEN: `cd frontend && npm run test && npm run typecheck && npm run lint && npm run build` → exit 0; `node --test scripts/harness-scripts.test.mjs` → exit 0 (18 pass, CI drift test included)
- Contracts/Migrations added: none (`vitest` 5.1.0, `@vue/test-utils` 2.5.1, `jsdom` 31.0.0 pinned exact; `src/test/echarts-mock.ts` is a global `setupFiles` stub)
- Decisions: none
- Open Questions: none

### Task 2: PrimeVue preset and ScoreBadge
- Commits: `c2bb75c feat(frontend): add the PrimeVue preset and ScoreBadge`
- Verification: RED: `cd frontend && npm run test` → FAIL (`Failed to resolve import "./ScoreBadge.vue"`); GREEN: `cd frontend && npm run test && npm run typecheck && npm run lint && npm run build` → exit 0 (12 tests)
- Contracts/Migrations added: none (`primevue` 4.5.5 and `@primeuix/themes` 2.0.3, MIT, exact; PrimeVue 5 needs a license key; see design.md; `@theme` tokens in `src/style.css`, preset in `src/theme/preset.ts`)
- Decisions: 1.14 (inline pointers in `style.css`, `preset.ts`, `ScoreBadge.vue`, `main.ts`)
- Open Questions: none

### Task 3: Feature folders and route files
- Commits: `da4336e refactor(frontend): feature folders with one routes file each`
- Verification: RED: `cd frontend && npm run test` → FAIL (`Failed to resolve import "@/views/assessment/AssessmentView.vue"`); GREEN: `cd frontend && npm run test && npm run typecheck && npm run lint && npm run build` → exit 0 (15 tests)
- Contracts/Migrations added: `views/{auth,studio,assessment,ranking,teams}/routes.ts` (default export `RouteRecordRaw[]`; paths unchanged)
- Decisions: none
- Open Questions: none

### Task 4: Shared API client
- Commits: `36360d3 feat(frontend): add the shared API client`
- Verification: RED: `cd frontend && npm run test` → FAIL (`Failed to resolve import "./client"`); GREEN: `cd frontend && npm run test && npm run typecheck && npm run lint && npm run build` → exit 0 (23 tests); `node --test scripts/harness-scripts.test.mjs` → exit 0
- Contracts/Migrations added: `src/api/client.ts` (`ApiProblem`, `apiFetch<T>`, `postSse` yields `{ event, data }`; no backend endpoint change, so `docs/api.md` is unchanged)
- Decisions: none
- Open Questions: none
