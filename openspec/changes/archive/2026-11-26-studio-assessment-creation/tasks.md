# Tasks: Studio Assessment Creation (`studio-assessment-creation`)

## Status
- **Branch:** `feat/studio-assessment-creation` (branch off `docs/ticket-01-prestep`)
- **Worktree:** `../nifty-studio-assessment-creation`
- **Current:** delivered; PR/MR open; waiting for cold review and merge by Teammate A
- **Next action:** none for the builder (Teammate A reviews, checks CI and merges; invite-links starts after #2 and #3 merge on dev)
- **Uncommitted changes:** none
- **PR / MR:** GitHub #25, GitLab !24
- **Open review findings:** —

## Tasks

- [x] **US-ST-01: Hand-off view contracts (`StudioQueryService`) and rubric-leak reflection guard**
  - [x] Verify the US-BF-06 stubs of `StudioQueryService` and `studio/service/view/` match `docs/tickets/00-parallel-waves-and-contracts.md` §3. Never add a second copy.
  - [x] Add the reflection guard test: `InterviewerView` and `InterviewerQuestion` declare zero fields containing `rubric`, `weight`, `threshold`, or `coverage` (RED: n/a, the stub from US-BF-06 already complies; log that in the Handoff).
  - [x] Verify GREEN with `cd backend && ./mvnw -B test` (including `ArchitectureTest`).
  - [x] Commit: `feat(studio): add StudioQueryService hand-off view contracts and rubric guard`

- [x] **US-ST-02: Flyway `V3__studio.sql`, `@Immutable` entities in `studio/repository/`, and mutation triggers**
  - [x] Write RED `@DataJpaTest` (PostgreSQL Testcontainers) verifying schema CHECK constraints (`min_coverage`, `rubric_levels` length 5, `follow_up_limit` 0..3, `min < max`) and trigger rejection on `UPDATE` and unflagged `DELETE`, plus purge success under `SET LOCAL nifty.allow_purge = 'on'`.
  - [x] Implement `V3__studio.sql`, entities in `studio/repository/` (NOT `domain/`), and package-private `Repository<T, UUID>` interfaces.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(studio): add V3 studio schema, entities, repositories, and immutability triggers`

- [x] **US-ST-03: Pure validators (`SettingsRules`, `SkillRules`, `QuestionRules`, `ConfigValidator`)**
  - [x] Write RED pure-JVM unit tests covering every error code (`REQUIRED`, `OUT_OF_RANGE`, `BAD_WEIGHTS`, `RUBRIC_INCOMPLETE`, `DUPLICATE_SKILL`, `TIME_RANGE`, `NO_SKILL`, `UNUSED_SKILL`, `ORDER_GAP`, `EMPTY_CONFIG`) AND the `SOFT_SKILLS` + `bfi10Enabled = true` zero-question exemption (Decision 2.23).
  - [x] Implement zero-Stream pure validators collecting all errors at once.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(studio): implement pure configuration validators with SOFT_SKILLS exemption`

- [x] **US-ST-04: One-shot idempotent creation, `AuditService` (`CONFIG_CREATED`), and `StudioQueryServiceImpl`**
  - [x] Write RED tests for `201` create, `200` idempotent replay (single audit row), other-professor key collision (`400`), `400` `FieldValidationException` rollback (zero rows, zero audit), `StudioQueryServiceImpl.ownedBy(configId, professorId)` (true only for the owner; used by `invite-links`, REQ-AA-09), and `StudioQueryServiceImpl` `JOIN FETCH` queries matching all 4 fixtures.
  - [x] Add `boolean ownedBy(UUID configId, UUID professorId)` to `StudioQueryService` and its placeholder. Implement `ConfigCreationService` and `StudioQueryServiceImpl` (`@Transactional(readOnly = true)` class default). Delete `StudioQueryServicePlaceholder`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(studio): implement idempotent assessment creation and StudioQueryServiceImpl`

- [x] **US-ST-05: Professor read endpoints, GDPR `DELETE`, and `StudioConfigController`**
  - [x] Write RED `@WebMvcTest` tests for `POST /api/v1/ranking/studio/configs` (`201`/`200`/`400`/`403`), `GET` list & detail (professor scoping, `404` for other professor), `PUT`/`PATCH` → `405`, and `DELETE` (`204`, emits `AssessmentDeletedEvent`, writes `CONFIG_DELETED` via `AuditService`).
  - [x] Implement `AssessmentReadService`, `AssessmentDeletionService`, `StudioConfigController`, and update `docs/api.md`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(studio): add ranking studio REST endpoints and GDPR assessment deletion`

- [x] **US-ST-06: Vue 3.5 + PrimeVue Studio form, list, and detail views (`frontend/src/views/studio/`)**
  - [x] Write RED Vitest tests for `assessmentForm` Pinia store (`localStorage` persistence, skill removal cleanup, idempotency key reuse), `SettingsSection`, `SkillsSection` (5 rubric boxes, no weight input), `QuestionsSection` (skill dropdown, reorder), `ReviewStep` (submit + `400` field mapping), `AssessmentList`, and read-only `AssessmentDetail` ("Send to students" link).
  - [x] Implement store and views using PrimeVue + Tailwind v4 tokens.
  - [x] Verify GREEN with `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`.
  - [x] Commit: `feat(frontend): add Studio assessment form, list, and detail views`

## Delivery

- [x] `git fetch origin && git merge origin/dev` (never rebase a pushed branch, no force pushes)
- [x] Run `cd backend && ./mvnw -B test`, `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all`, and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` (expect exit 0).
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive studio-assessment-creation -y`, update `docs/feature_backlog.md` and `docs/feature_history.md`, commit `chore(specs): archive studio-assessment-creation`.
- [x] Push and open PR/MR via `node scripts/forge.mjs`.
- [ ] Pass cold read-only review, CI checks, human merge approval, and mirror sync.

## Handoff Log


### US-ST-01 (REQ-IS-09)
- Commits: `feat(studio): add StudioQueryService hand-off view contracts and rubric guard`
- Verification: RED: n/a (the US-BF-06 stubs already comply with the reflection guard, per tasks.md); GREEN: `cd backend && ./mvnw -B test` → exit 0 (31 run, 0 failed, incl. `ArchitectureTest` and `InterviewerViewRubricGuardTest`)
- Contracts/Migrations added: verified US-BF-06 `StudioQueryService` and `studio/service/view/*` stubs against contract pack §3.4 (zero duplicates)
- Decisions: applies 1.12, 1.19
- Open Questions: none

### US-ST-02 (REQ-IS-06, REQ-IS-08)
- Commits: `feat(studio): add V3 studio schema, entities, repositories, and immutability triggers`
- Verification: RED: `./mvnw -B test -Dtest=StudioSchemaAndTriggerTest` → FAIL (compile: entities and repositories missing); GREEN: `cd backend && ./mvnw -B test` → exit 0 (34 run, 0 failed, incl. `StudioSchemaAndTriggerTest` and `ArchitectureTest`)
- Contracts/Migrations added: `V3__studio.sql` (`assessment_config`, `skill`, `core_question`, `core_question_skill`, `studio_block_mutation()` trigger), `@Immutable` entities and `Repository` interfaces in `studio/repository/`
- Decisions: applies 1.10, 1.19, 2.21, 2.22, 4.4
- Open Questions: none

### US-ST-03 (REQ-IS-05, REQ-IS-06, REQ-IS-11)
- Commits: `feat(studio): implement pure configuration validators with SOFT_SKILLS exemption`
- Verification: RED: `./mvnw -B test -Dtest=ConfigValidatorTest` → FAIL (compile: validator & input records missing); GREEN: `cd backend && ./mvnw -B test` → exit 0 (42 run, 0 failed)
- Contracts/Migrations added: `CreateAssessmentRequest`, `SettingsInput`, `SkillInput`, `QuestionInput`, `ConfigValidator`, `SettingsRules`, `SkillRules`, `QuestionRules` (zero Streams)
- Decisions: applies 1.19, 2.22, 2.23
- Open Questions: none

### US-ST-04 (REQ-IS-01, REQ-IS-09, REQ-IS-12)
- Commits: `feat(studio): implement idempotent assessment creation and StudioQueryServiceImpl`
- Verification: RED: `./mvnw -B test -Dtest=ConfigCreationAndQueryIntegrationTest` → FAIL (compile: `ConfigCreationService`, `CreatedAssessment`, `ownedBy` missing); GREEN: `cd backend && ./mvnw -B test` → exit 0 (46 run, 0 failed, 1 skipped by design: `StudioQueryServicePlaceholder` deleted)
- Contracts/Migrations added: `StudioQueryService.ownedBy(configId, professorId)`, `ConfigCreationService`, `CreatedAssessment`, `StudioQueryServiceImpl` (`StudioQueryServicePlaceholder` deleted)
- Decisions: applies 1.12, 1.19, 2.21
- Open Questions: none

### US-ST-05 (REQ-IS-01, REQ-IS-07, REQ-IS-08, REQ-IS-10)
- Commits: `feat(studio): add ranking studio REST endpoints and GDPR assessment deletion`
- Verification: RED: `./mvnw -B test -Dtest=StudioConfigControllerTest,AssessmentReadAndDeletionIntegrationTest` → FAIL (compile: controller and read/deletion services missing); GREEN: `cd backend && ./mvnw -B test` → exit 0 (53 run, 0 failed, 1 skipped by design)
- Contracts/Migrations added: `POST/GET/DELETE /api/v1/ranking/studio/configs/**` (`StudioConfigController`), `AssessmentReadService`, `AssessmentDeletionService`, `AssessmentSummaryResponse`, `AssessmentDetailResponse`; `docs/api.md` §4/§5 updated
- Decisions: applies 1.15, 1.19, 2.21, 4.4
- Open Questions: none

### US-ST-06 (REQ-IS-01, REQ-IS-05, REQ-IS-06, REQ-IS-07, REQ-IS-10)
- Commits: `feat(frontend): add Studio assessment form, list, and detail views`
- Verification: RED: `cd frontend && npm run test` → FAIL (`assessmentForm` store and Studio views missing); GREEN: `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build` → exit 0 (7 files, 43 passed)
- Contracts/Migrations added: `frontend/src/api/studio.ts`, `frontend/src/stores/assessmentForm.ts`, `frontend/src/views/studio/{SettingsSection,SkillsSection,QuestionsSection,ReviewStep,AssessmentCreateView,AssessmentListView,AssessmentDetailView}.vue`, `frontend/src/views/studio/routes.ts`
- Decisions: applies 1.14, 2.21, 2.22, 2.23, 4.4
- Open Questions: none

### Post-audit hardening (invariant & mutation audit)
- Commits: `fix(studio): add custom trait weight inputs, fix skill rename, and kill surviving mutants`
- Verification: RED: mutation & invariant audit caught missing CUSTOM Big Five trait weight inputs in `SettingsSection.vue`, clear-and-retype blank orphan in `assessmentForm.ts` `renameSkill`, missing Cancel on GDPR delete, and 4 surviving mutants (M3, M4, M10, M11); GREEN: `cd backend && ./mvnw -B test` (56 run, 0 failed, 1 skipped), `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build` (7 files, 47 passed), `openspec validate --all --strict` (16/16), harness tests (43/43)
- Contracts/Migrations added: none
- Decisions: applies 2.23, 4.4
- Open Questions: none
