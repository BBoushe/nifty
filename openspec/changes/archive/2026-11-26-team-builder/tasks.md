# Tasks: Team Builder (`team-builder`)

## Status
- **Branch:** `feat/team-builder` (branch off `dev`)
- **Worktree:** `../nifty-team-builder`
- **Current:** US-TB-01..06 done, change archived, Task 7 (review fixes) committed
- **Next action:** orchestrator pushes, then a short re-review
- **Uncommitted changes:** none
- **PR / MR:** PR #27 / MR !26 (pushed by the orchestrator)
- **Open review findings:** none open; the six non-blocking items are fixed in Task 7

## Tasks

- [x] **US-TB-01: Flyway `V8__teams.sql`, private repositories, and `TeamCleanupListener`**
  - [x] Write RED `@DataJpaTest` (PostgreSQL Testcontainers) proving `V8` applies cleanly, unique `(proposal_id, invite_id)` prevents duplicate candidate assignment, and `AssessmentDeletedEvent` deletes all proposals and members for `configId`.
  - [x] Implement `V8__teams.sql`, entities/repositories in `teams/repository/`, and `TeamCleanupListener`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(teams): add V8 team proposal schema, repositories, and GDPR cleanup`

- [x] **US-TB-02: Pure zero-Stream `TeamObjectiveCalculator` with normalization and explicit `N/A` handling**
  - [x] Write RED pure-JVM tests: hard-skill `[0, 4] -> [0, 1]`, BFI-10 `[1, 5] -> [0, 1]` without double-inversion, `ENGINEERING_DEFAULT` `[0.30, 0.25, 0.20, 0.15, 0.10]` weights, `HARD_SKILLS` / `SOFT_SKILLS` / `COMBINED` blending, trait means in the imbalance term only when BFI-10 is on and the mode is `SOFT_SKILLS` or `COMBINED` (REQ-TB-06), and candidates with `N/A` hard skills excluded per dimension (never rejected, never treated as `0`).
  - [x] Implement primitive-array `TeamObjectiveCalculator` (zero Java Streams).
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(teams): implement zero-stream objective calculator with N/A handling`

- [x] **US-TB-03: Deterministic seeded `SimulatedAnnealingTeamOptimizer` (2-opt swaps)**
  - [x] Write RED pure-JVM tests: 22 students with `teamSize = 5` → 5 teams of sizes `{5,5,4,4,4}`, every student placed exactly once, fixed `seed = 42` returns identical assignments across runs, cost strictly improves or equals random baseline, and validation rejects `numTeams > N`, negative weights, or out-of-range `alpha`.
  - [x] Implement `SimulatedAnnealingTeamOptimizer` using primitive arrays and 2-opt swaps.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(teams): implement seeded Simulated Annealing 2-opt team optimizer`

- [x] **US-TB-04: `TeamBuilderService` (generate `DRAFT`, drag-and-drop recalculate, `AuditService` publish)**
  - [x] Write RED Mockito tests against `StudioQueryService`, `EvaluationQueryService`, `AssessmentQueryService`, and `AuditService`: generate uses only `CandidateProfilesView.released` and shows `pendingInviteIds` as waiting for review (REQ-TB-11), generate saves `DRAFT`, recalculate updates diagnostics with zero LLM calls and validates every candidate assigned once, publish flips `DRAFT -> PUBLISHED` and writes `TEAMS_PUBLISHED` via `AuditService`.
  - [x] Implement `TeamBuilderService`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(teams): implement team generation, live recalculation, and audited publish`

- [x] **US-TB-05: `TeamBuilderController` (`PROFESSOR` only) and `docs/api.md` sync**
  - [x] Write RED `@WebMvcTest` + `@MockitoBean` tests: `POST .../teams/generate` (`201`), `POST .../recalculate` (`200`), `POST .../publish` (`200`), `GET .../teams` (`200` with professor-only traits), invalid request → `400` RFC 9457 `ProblemDetail`, `CANDIDATE` → `403`.
  - [x] Implement `TeamBuilderController` and sync `docs/api.md`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test` (including `ArchitectureTest`).
  - [x] Commit: `feat(teams): add professor team builder endpoints and update api docs`

- [x] **US-TB-06: Vue 3.5 + PrimeVue + ECharts Team Builder UI (`frontend/src/views/teams/`)**
  - [x] Write RED Vitest tests: team cards show per-skill averages, `N/A` counts, balance/diversity diagnostics, and professor-only BFI-10 trait summaries; dragging a member calls `/recalculate` and updates cards live; Publish button calls `/publish` and locks UI.
  - [x] Implement `TeamBuilderView.vue` under `frontend/src/views/teams/`.
  - [x] Verify GREEN with `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`.
  - [x] Commit: `feat(frontend): add Team Builder UI with drag-and-drop balance charts`

## Delivery

- [x] `git fetch origin && git merge origin/dev` (never rebase a pushed branch, no force pushes)
- [x] Run `cd backend && ./mvnw -B test`, `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all`, and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` (expect exit 0).
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive team-builder -y`, update `docs/feature_backlog.md` and `docs/feature_history.md`, commit `chore(specs): archive team-builder`.
- [ ] Push and open PR/MR via `node scripts/forge.mjs`.
- [ ] Pass cold read-only review, CI checks, human merge approval, and mirror sync.

## Handoff Log

### US-TB-01 Flyway V8, repositories, TeamCleanupListener
- Commits: b051751 feat(teams): add V8 team proposal schema, repositories, and GDPR cleanup
- Verification: RED: `cd backend && ./mvnw -B test -Dtest=TeamSchemaAndCleanupTest` → FAIL (compile error: teams entities/migration absent); GREEN: `cd backend && ./mvnw -B test` → exit 0 (193 run)
- Contracts/Migrations added: `backend/src/main/resources/db/migration/V8__teams.sql`; entities and repositories in `teams/repository/`; `TeamCleanupListener`
- Decisions: 4.4 (MANDATORY purge listener), 1.20
- Open Questions: none

### US-TB-02 TeamObjectiveCalculator
- Commits: d000c9f feat(teams): implement zero-stream objective calculator with N/A handling
- Verification: RED: `cd backend && ./mvnw -B test -Dtest=TeamObjectiveCalculatorTest` → FAIL (compile error: calculator absent); GREEN: `cd backend && ./mvnw -B test` → exit 0 (211 run, 11 in the new class)
- Contracts/Migrations added: `teams/service/TeamObjectiveCalculator` (public static `normalizeSkill`, `normalizeTrait`, `traitWeights`; records `Params`, `TeamDiagnostics`); migrations none
- Decisions: 1.20, 2.23, 4.3 (pointer comments in the class; trait term only for BFI-10 on and SOFT_SKILLS/COMBINED, REQ-TB-06)
- Open Questions: none

### US-TB-03 SimulatedAnnealingTeamOptimizer
- Commits: 890afd0 feat(teams): implement seeded Simulated Annealing 2-opt team optimizer
- Verification: RED: `cd backend && ./mvnw -B test -Dtest=SimulatedAnnealingTeamOptimizerTest` → FAIL (compile error: optimizer absent); GREEN: `cd backend && ./mvnw -B test` → exit 0 (211 run, 7 in the new class)
- Contracts/Migrations added: `teams/service/SimulatedAnnealingTeamOptimizer.optimize(calculator, candidates, numTeams, seed)`; migrations none
- Decisions: 1.20 (seeded, 2-opt swaps keep floor/ceil sizes; test greps both sources for zero Streams)
- Open Questions: none

### US-TB-04 TeamBuilderService
- Commits: 2392de2 feat(teams): implement team generation, live recalculation, and audited publish
- Verification: RED: `cd backend && ./mvnw -B test -Dtest=TeamBuilderServiceTest` → FAIL (compile error: service absent); GREEN: `cd backend && ./mvnw -B test` → exit 0 (223 run, 12 in the new class)
- Contracts/Migrations added: `TeamBuilderService` (`generate`, `recalculate`, `publish`, `list`), `GenerateTeamsRequest`, `RecalculateTeamsRequest`, `TeamProposalResponse` in `teams/service/`; migrations none. Deviation: DTO records live in `service/` (studio pattern), `design.md` updated. Extra optional request fields `combinedHardWeight`, `traitWeights` (REQ-TB-08 names custom weights). `candidateRef` comes from `InviteService.listInvites`.
- Decisions: 1.20, 2.27 (400 without BFI-10, no fallback), 4.3 (traits read, never re-inverted), 2.10/2.13 (released only)
- Open Questions: none

### US-TB-05 TeamBuilderController and docs/api.md
- Commits: f2a8c40 feat(teams): add professor team builder endpoints and update api docs
- Verification: RED: `cd backend && ./mvnw -B test -Dtest=TeamBuilderControllerTest` → FAIL (compile error: controller absent); GREEN: `cd backend && ./mvnw -B test` → exit 0 (229 run, 6 in the new class, ArchitectureTest included)
- Contracts/Migrations added: `teams/web/TeamBuilderController` (4 PROFESSOR-only endpoints); `docs/api.md` §4 rows added and §5 Team Builder block marked implemented; migrations none
- Decisions: 1.20, 2.23, 2.27, 4.3
- Open Questions: none

### US-TB-06 Team Builder UI
- Commits: bdebf30 feat(frontend): add Team Builder UI with drag-and-drop balance charts
- Verification: RED: `cd frontend && npx vitest run src/views/teams` → FAIL (TeamBuilderView.vue absent, import unresolved); GREEN: `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build` → exit 0 (86 tests, 6 new)
- Contracts/Migrations added: `frontend/src/api/teams.ts`, `frontend/src/views/teams/{TeamBuilderView,TeamCard}.vue`, route `/ranking/teams/:configId`; migrations none. No link to the page exists yet in `views/studio/` (not owned by this change).
- Decisions: 1.20, 4.3 (zero client-side maths, traits shown to the professor only)
- Open Questions: none

### Task 7 (Review fixes)
- Commits: 789e2f5 fix(teams): lock publish, safe seeds, stable columns and attempt-matched traits
- Verification: RED: `cd backend && ./mvnw -B test -Dtest='TeamBuilderServiceTest,TeamBuilderControllerTest,TeamPublishRaceTest'` → FAIL (7 new tests: two publishes wrote two audit calls, out-of-range generated seed, `null` assignment element answered 200, skill column order followed upstream, same-name skills collided, mismatched-attempt traits were used, `cost_metrics` rewritten by list/publish); GREEN: `cd backend && ./mvnw -B test` → exit 0 (236 run), `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build` → exit 0 (86 run), `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 (43 run), `OPENSPEC_TELEMETRY=0 openspec validate --all --strict` → exit 0
- Contracts/Migrations added: `TeamProposalRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`, no schema change, V8 untouched); `GenerateTeamsRequest.MAX_SEED` and `@Min/@Max` on `seed`; `docs/api.md` notes (seed range, name-collision suffix, attempt-matched traits, uneven placement, `409` after a lock wait); REQ-TB-05 and REQ-TB-03 reworded in `openspec/specs/team-builder/spec.md`; archived `design.md` section "Review Fixes (Task 7)". API shape unchanged: two skills with one name now get the key suffix ` [<8 characters of skillId>]`.
- Decisions: 1.20 (seed determinism independent of upstream order), 4.3 (traits only from the released attempt), 2.10 (REQ-TB-04 one audit row per publish)
- Open Questions: none

### Task 8 (Invariant and mutation testing, before merge)
- Commits: `test(teams): add seeded invariant property tests from mutation run`
- Verification: tests only, no production change. Mutation RED: 55 hand mutants of `TeamObjectiveCalculator`, `SimulatedAnnealingTeamOptimizer`, `TeamBuilderService` and the lock call, each run with `./mvnw -B -q test -Dtest='Team*,SimulatedAnnealing*'`; 43 killed by the existing tests, 9 more killed after the new tests, 3 survive (O1 `delta <= 0` vs `< 0` and O9 same-team swap guard are equivalent; O8 fixed annealing RNG has no observable requirement). GREEN: `./mvnw -B test -Dtest='TeamInvariantPropertyTest'` → exit 0 (22 run, about 18 s, seed `20261126`); production code equals a87f3a3 (`git diff --quiet -- backend/src/main` → 0).
- Contracts/Migrations added: none. `TeamInvariantPropertyTest` covers placement and floor/ceil sizes, seed determinism and input-order independence, N/A masking (poison values never read, N/A never treated as 0), cost never above the starting deal, team relabelling, trait-weight scaling, BFI-10 trait term and 1..5 to 0..1 mapping, bounds, and an annealing hit rate above 90 percent on tiny worlds.
- Decisions: 1.20, 4.3
- Open Questions: Simulated Annealing is a heuristic; it misses the optimum in 1 of 300 tiny worlds (accepted, not a spec violation). The lock mutants are killed only by the timing-based `TeamPublishRaceTest`.

