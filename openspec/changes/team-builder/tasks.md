# Tasks: Team Builder (`team-builder`)

## Status
- **Branch:** `feat/team-builder` (branch off `dev`)
- **Worktree:** `../nifty-team-builder`
- **Current:** US-TB-02 done, committed
- **Next action:** US-TB-03 (SimulatedAnnealingTeamOptimizer)
- **Uncommitted changes:** none (after the task commit)
- **PR / MR:** — (orchestrator opens it)
- **Open review findings:** —

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

- [ ] **US-TB-03: Deterministic seeded `SimulatedAnnealingTeamOptimizer` (2-opt swaps)**
  - [ ] Write RED pure-JVM tests: 22 students with `teamSize = 5` → 5 teams of sizes `{5,5,4,4,4}`, every student placed exactly once, fixed `seed = 42` returns identical assignments across runs, cost strictly improves or equals random baseline, and validation rejects `numTeams > N`, negative weights, or out-of-range `alpha`.
  - [ ] Implement `SimulatedAnnealingTeamOptimizer` using primitive arrays and 2-opt swaps.
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [ ] Commit: `feat(teams): implement seeded Simulated Annealing 2-opt team optimizer`

- [ ] **US-TB-04: `TeamBuilderService` (generate `DRAFT`, drag-and-drop recalculate, `AuditService` publish)**
  - [ ] Write RED Mockito tests against `StudioQueryService`, `EvaluationQueryService`, `AssessmentQueryService`, and `AuditService`: generate uses only `CandidateProfilesView.released` and shows `pendingInviteIds` as waiting for review (REQ-TB-11), generate saves `DRAFT`, recalculate updates diagnostics with zero LLM calls and validates every candidate assigned once, publish flips `DRAFT -> PUBLISHED` and writes `TEAMS_PUBLISHED` via `AuditService`.
  - [ ] Implement `TeamBuilderService`.
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [ ] Commit: `feat(teams): implement team generation, live recalculation, and audited publish`

- [ ] **US-TB-05: `TeamBuilderController` (`PROFESSOR` only) and `docs/api.md` sync**
  - [ ] Write RED `@WebMvcTest` + `@MockitoBean` tests: `POST .../teams/generate` (`201`), `POST .../recalculate` (`200`), `POST .../publish` (`200`), `GET .../teams` (`200` with professor-only traits), invalid request → `400` RFC 9457 `ProblemDetail`, `CANDIDATE` → `403`.
  - [ ] Implement `TeamBuilderController` and sync `docs/api.md`.
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test` (including `ArchitectureTest`).
  - [ ] Commit: `feat(teams): add professor team builder endpoints and update api docs`

- [ ] **US-TB-06: Vue 3.5 + PrimeVue + ECharts Team Builder UI (`frontend/src/views/teams/`)**
  - [ ] Write RED Vitest tests: team cards show per-skill averages, `N/A` counts, balance/diversity diagnostics, and professor-only BFI-10 trait summaries; dragging a member calls `/recalculate` and updates cards live; Publish button calls `/publish` and locks UI.
  - [ ] Implement `TeamBuilderView.vue` under `frontend/src/views/teams/`.
  - [ ] Verify GREEN with `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`.
  - [ ] Commit: `feat(frontend): add Team Builder UI with drag-and-drop balance charts`

## Delivery

- [ ] `git fetch origin && git merge origin/dev` (never rebase a pushed branch, no force pushes)
- [ ] Run `cd backend && ./mvnw -B test`, `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all`, and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` (expect exit 0).
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive team-builder -y`, update `docs/feature_backlog.md` and `docs/feature_history.md`, commit `chore(specs): archive team-builder`.
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
- Commits: <sha-us-tb-02> feat(teams): implement zero-stream objective calculator with N/A handling
- Verification: RED: `cd backend && ./mvnw -B test -Dtest=TeamObjectiveCalculatorTest` → FAIL (compile error: calculator absent); GREEN: `cd backend && ./mvnw -B test` → exit 0 (211 run, 11 in the new class)
- Contracts/Migrations added: `teams/service/TeamObjectiveCalculator` (public static `normalizeSkill`, `normalizeTrait`, `traitWeights`; records `Params`, `TeamDiagnostics`); migrations none
- Decisions: 1.20, 2.23, 4.3 (pointer comments in the class; trait term only for BFI-10 on and SOFT_SKILLS/COMBINED, REQ-TB-06)
- Open Questions: none
