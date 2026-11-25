# Tasks: `backend-foundation`

## Status
- **Branch:** `feat/backend-foundation`
- **Worktree:** `/Users/alek/Developer/Programming/nifty-backend-foundation`
- **Current:** not started
- **Next action:** Task 1
- **Uncommitted changes:** no
- **PR / MR:** none yet
- **Open review findings:** none

## Tasks

### Task 1: US-BF-01 Web starter and Spring AI property
- [ ] RED: n/a (dependency rename). Record `./mvnw -B test` before the change.
- [ ] GREEN: `spring-boot-starter-web` → `spring-boot-starter-webmvc`; move `spring.ai.openai.chat.options.model` to `spring.ai.openai.chat.model` (check `docs/ai_docs/spring-ai-2-kiconnect.md`); the app context still starts with no LLM key.
- [ ] Verify: `cd backend && ./mvnw -B test` → exit 0; commit `build(backend): use the webmvc starter and the Spring AI 2 model property`

### Task 2: US-BF-02 PostgreSQL Testcontainers for all Spring tests (REQ-BF-01)
- [ ] RED: add `FlywayBaselineTest` (`@DataJpaTest`, extends `PostgresContainerBase`) that checks `audit_log` exists; it fails to compile before the base class exists.
- [ ] GREEN: add `spring-boot-starter-data-jpa-test`, the base class, move `HealthEndpointTest` and `RoleAccessTest` onto it, drop the H2 datasource and the `h2` test dependency.
- [ ] Verify: `cd backend && ./mvnw -B test` → exit 0 (Docker running); commit `test(backend): run Spring tests on PostgreSQL Testcontainers`

### Task 3: US-BF-03 GitLab dind
- [ ] GREEN: `services: [docker:dind]` and Testcontainers variables on the GitLab `backend` job (design §2). No command changes.
- [ ] Verify: `node --test scripts/harness-scripts.test.mjs` → exit 0 (CI drift test); commit `ci(gitlab): run Testcontainers with docker:dind`

### Task 4: US-BF-04 and US-BF-05 Validation errors and shared events (REQ-BF-02)
- [ ] RED: `GlobalExceptionHandlerTest` case for `FieldValidationException` (400, 2 `errors`, no `trace`/`exception`).
- [ ] GREEN: `FieldErrorDetail`, `FieldValidationException`, handler mapping; `AttemptCompletedEvent`, `AssessmentDeletedEvent` in `shared/config`.
- [ ] Verify: `cd backend && ./mvnw -B test` (incl. `ArchitectureTest`) → exit 0; commit `feat(shared): add field validation errors and cross-slice events`

### Task 5: US-BF-06 Contract stubs, placeholders and role probes (REQ-BF-03)
- [ ] RED: `ContractStubsTest` (`@SpringBootTest`): one bean per shared interface, placeholder call throws `UnsupportedOperationException`, enum JSON literals (`"N/A"`, `"follow_up"`, `"auto_released"`).
- [ ] GREEN: every §3 type of `docs/tickets/00-parallel-waves-and-contracts.md` in its owner package; 6 `<Interface>Placeholder` beans; `RoleAccessTest` probes `/api/v1/ranking/__probe` and `/api/v1/assessment/__probe`.
- [ ] Verify: `cd backend && ./mvnw -B test` → exit 0; commit `feat(contracts): add cross-slice contract stubs with placeholder beans`

### Task 6: Docs
- [ ] `docs/architecture.md` (test base, placeholders), `docs/feature_backlog.md` row 1 → done, `CHANGELOG.md`
- [ ] Verify: harness tests → exit 0; commit `docs: record backend-foundation`

## Delivery
- [ ] `git fetch origin && git merge origin/dev`
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive backend-foundation -y --skip-specs`, history entry (`PR/MR: pending`)
- [ ] `node scripts/forge.mjs push`; PR/MR with `pr-memo`
- [ ] Cold review (fresh `reviewer` agent)
- [ ] CI green on both forges (`node scripts/forge.mjs prs`)
- [ ] Ask the user with the architecture summary, then merge on GitHub and `node scripts/forge.mjs sync dev`
- [ ] Branch and worktree cleanup

## Handoff Log
