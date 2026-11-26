# Tasks: Studio Assessment Creation (`studio-assessment-creation`)

## Status
- **Branch:** `feat/studio-assessment-creation` (branch off `dev`)
- **Worktree:** `../nifty-studio-assessment-creation`
- **Current:** Ready to start after Wave 0 (the `StudioQueryService` stubs come from US-BF-06)
- **Next action:** Start US-ST-01
- **Uncommitted changes:** none
- **PR / MR:** —
- **Open review findings:** —

## Tasks

- [ ] **US-ST-01: Hand-off view contracts (`StudioQueryService`) and rubric-leak reflection guard**
  - [ ] Verify the US-BF-06 stubs of `StudioQueryService` and `studio/service/view/` match `docs/tickets/00-parallel-waves-and-contracts.md` §3. Never add a second copy.
  - [ ] Add the reflection guard test: `InterviewerView` and `InterviewerQuestion` declare zero fields containing `rubric`, `weight`, `threshold`, or `coverage` (RED: n/a, the stub from US-BF-06 already complies; log that in the Handoff).
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test` (including `ArchitectureTest`).
  - [ ] Commit: `feat(studio): add StudioQueryService hand-off view contracts and rubric guard`

- [ ] **US-ST-02: Flyway `V3__studio.sql`, `@Immutable` entities in `studio/repository/`, and mutation triggers**
  - [ ] Write RED `@DataJpaTest` (PostgreSQL Testcontainers) verifying schema CHECK constraints (`min_coverage`, `rubric_levels` length 5, `follow_up_limit` 0..3, `min < max`) and trigger rejection on `UPDATE` and unflagged `DELETE`, plus purge success under `SET LOCAL nifty.allow_purge = 'on'`.
  - [ ] Implement `V3__studio.sql`, entities in `studio/repository/` (NOT `domain/`), and package-private `Repository<T, UUID>` interfaces.
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [ ] Commit: `feat(studio): add V3 studio schema, entities, repositories, and immutability triggers`

- [ ] **US-ST-03: Pure validators (`SettingsRules`, `SkillRules`, `QuestionRules`, `ConfigValidator`)**
  - [ ] Write RED pure-JVM unit tests covering every error code (`REQUIRED`, `OUT_OF_RANGE`, `BAD_WEIGHTS`, `RUBRIC_INCOMPLETE`, `DUPLICATE_SKILL`, `TIME_RANGE`, `NO_SKILL`, `UNUSED_SKILL`, `ORDER_GAP`, `EMPTY_CONFIG`) AND the `SOFT_SKILLS` + `bfi10Enabled = true` zero-question exemption (Decision 2.23).
  - [ ] Implement zero-Stream pure validators collecting all errors at once.
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [ ] Commit: `feat(studio): implement pure configuration validators with SOFT_SKILLS exemption`

- [ ] **US-ST-04: One-shot idempotent creation, `AuditService` (`CONFIG_CREATED`), and `StudioQueryServiceImpl`**
  - [ ] Write RED tests for `201` create, `200` idempotent replay (single audit row), other-professor key collision (`400`), `400` `FieldValidationException` rollback (zero rows, zero audit), `StudioQueryServiceImpl.ownedBy(configId, professorId)` (true only for the owner; used by `invite-links`, REQ-AA-09), and `StudioQueryServiceImpl` `JOIN FETCH` queries matching all 4 fixtures.
  - [ ] Add `boolean ownedBy(UUID configId, UUID professorId)` to `StudioQueryService` and its placeholder. Implement `ConfigCreationService` and `StudioQueryServiceImpl` (`@Transactional(readOnly = true)` class default). Delete `StudioQueryServicePlaceholder`.
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [ ] Commit: `feat(studio): implement idempotent assessment creation and StudioQueryServiceImpl`

- [ ] **US-ST-05: Professor read endpoints, GDPR `DELETE`, and `StudioConfigController`**
  - [ ] Write RED `@WebMvcTest` tests for `POST /api/v1/ranking/studio/configs` (`201`/`200`/`400`/`403`), `GET` list & detail (professor scoping, `404` for other professor), `PUT`/`PATCH` → `405`, and `DELETE` (`204`, emits `AssessmentDeletedEvent`, writes `CONFIG_DELETED` via `AuditService`).
  - [ ] Implement `AssessmentReadService`, `AssessmentDeletionService`, `StudioConfigController`, and update `docs/api.md`.
  - [ ] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [ ] Commit: `feat(studio): add ranking studio REST endpoints and GDPR assessment deletion`

- [ ] **US-ST-06: Vue 3.5 + PrimeVue Studio form, list, and detail views (`frontend/src/views/studio/`)**
  - [ ] Write RED Vitest tests for `assessmentForm` Pinia store (`localStorage` persistence, skill removal cleanup, idempotency key reuse), `SettingsSection`, `SkillsSection` (5 rubric boxes, no weight input), `QuestionsSection` (skill dropdown, reorder), `ReviewStep` (submit + `400` field mapping), `AssessmentList`, and read-only `AssessmentDetail` ("Send to students" link).
  - [ ] Implement store and views using PrimeVue + Tailwind v4 tokens.
  - [ ] Verify GREEN with `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`.
  - [ ] Commit: `feat(frontend): add Studio assessment form, list, and detail views`

## Delivery

- [ ] `git fetch origin && git merge origin/dev` (never rebase a pushed branch, no force pushes)
- [ ] Run `cd backend && ./mvnw -B test`, `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all`, and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` (expect exit 0).
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive studio-assessment-creation -y`, update `docs/feature_backlog.md` and `docs/feature_history.md`, commit `chore(specs): archive studio-assessment-creation`.
- [ ] Push and open PR/MR via `node scripts/forge.mjs`.
- [ ] Pass cold read-only review, CI checks, human merge approval, and mirror sync.

## Handoff Log
