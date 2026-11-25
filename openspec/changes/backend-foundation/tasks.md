# Tasks: `backend-foundation`

## Status
- **Branch:** `feat/backend-foundation`
- **Worktree:** `/Users/alek/Developer/Programming/nifty-backend-foundation`
- **Current:** Tasks 1-6 done (GitLab dind config unverified until the pipeline runs)
- **Next action:** `## Delivery` (merge `origin/dev`, archive, push, PR/MR, cold review)
- **Uncommitted changes:** no
- **PR / MR:** none yet
- **Open review findings:** none

## Tasks

### Task 1: US-BF-01 Web starter and Spring AI property
- [x] RED: n/a (dependency rename). Record `./mvnw -B test` before the change.
- [x] GREEN: `spring-boot-starter-web` → `spring-boot-starter-webmvc`; move `spring.ai.openai.chat.options.model` to `spring.ai.openai.chat.model` (check `docs/ai_docs/spring-ai-2-kiconnect.md`); the app context still starts with no LLM key.
- [x] Verify: `cd backend && ./mvnw -B test` → exit 0; commit `build(backend): use the webmvc starter and the Spring AI 2 model property`

### Task 2: US-BF-02 PostgreSQL Testcontainers for all Spring tests (REQ-BF-01)
- [x] RED: add `FlywayBaselineTest` (`@DataJpaTest`, extends `PostgresContainerBase`) that checks `audit_log` exists; it fails to compile before the base class exists.
- [x] GREEN: add `spring-boot-starter-data-jpa-test`, the base class, move `HealthEndpointTest` and `RoleAccessTest` onto it, drop the H2 datasource and the `h2` test dependency.
- [x] Verify: `cd backend && ./mvnw -B test` → exit 0 (Docker running); commit `test(backend): run Spring tests on PostgreSQL Testcontainers`

### Task 3: US-BF-03 GitLab dind
- [x] GREEN: `services: [docker:dind]` and Testcontainers variables on the GitLab `backend` job (design §2). No command changes.
- [x] Verify: `node --test scripts/harness-scripts.test.mjs` → exit 0 (CI drift test); commit `ci(gitlab): run Testcontainers with docker:dind`

### Task 4: US-BF-04 and US-BF-05 Validation errors and shared events (REQ-BF-02)
- [x] RED: `GlobalExceptionHandlerTest` case for `FieldValidationException` (400, 2 `errors`, no `trace`/`exception`).
- [x] GREEN: `FieldErrorDetail`, `FieldValidationException`, handler mapping; `AttemptCompletedEvent`, `AssessmentDeletedEvent` in `shared/config`.
- [x] Verify: `cd backend && ./mvnw -B test` (incl. `ArchitectureTest`) → exit 0; commit `feat(shared): add field validation errors and cross-slice events`

### Task 5: US-BF-06 Contract stubs, placeholders and role probes (REQ-BF-03)
- [x] RED: `ContractStubsTest` (`@SpringBootTest`): one bean per shared interface, placeholder call throws `UnsupportedOperationException`, enum JSON literals (`"N/A"`, `"follow_up"`, `"auto_released"`).
- [x] GREEN: every §3 type of `docs/tickets/00-parallel-waves-and-contracts.md` in its owner package; 6 `<Interface>Placeholder` beans; `RoleAccessTest` probes `/api/v1/ranking/__probe` and `/api/v1/assessment/__probe`.
- [x] Verify: `cd backend && ./mvnw -B test` → exit 0; commit `feat(contracts): add cross-slice contract stubs with placeholder beans`

### Task 6: Docs
- [x] `docs/architecture.md` (test base, placeholders), `docs/feature_backlog.md` row 1 → done, `CHANGELOG.md`
- [x] Verify: harness tests → exit 0; commit `docs: record backend-foundation`

## Delivery
- [ ] `git fetch origin && git merge origin/dev`
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive backend-foundation -y --skip-specs`, history entry (`PR/MR: pending`)
- [ ] `node scripts/forge.mjs push`; PR/MR with `pr-memo`
- [ ] Cold review (fresh `reviewer` agent)
- [ ] CI green on both forges (`node scripts/forge.mjs prs`)
- [ ] Ask the user with the architecture summary, then merge on GitHub and `node scripts/forge.mjs sync dev`
- [ ] Branch and worktree cleanup

## Handoff Log

### Task 1 (US-BF-01)
- Commits: 3db4f72 build(backend): use the webmvc starter and the Spring AI 2 model property
- Verification: RED: n/a (dependency rename; baseline `cd backend && ./mvnw -B test` → BUILD SUCCESS, 24 tests); GREEN: `cd backend && ./mvnw -B test` → exit 0, 24 tests
- Contracts/Migrations added: none
- Decisions: none
- Open Questions: none

### Task 2 (US-BF-02, REQ-BF-01)
- Commits: c44805c test(backend): run Spring tests on PostgreSQL Testcontainers
- Verification: RED: `cd backend && ./mvnw -B test-compile` → FAIL (`FlywayBaselineTest`: PostgresContainerBase and `@DataJpaTest` package missing); GREEN: `cd backend && ./mvnw -B test` → exit 0, 25 tests on PostgreSQL 17
- Contracts/Migrations added: `de.fhdortmund.nifty.PostgresContainerBase` (test sources); no migration
- Decisions: none (supersedes the "H2 until first entity" sentence of Decision 1.10 per design §2)
- Open Questions: none

### Task 3 (US-BF-03)
- Commits: 46e49f0 ci(gitlab): run Testcontainers with docker:dind
- Verification: RED: n/a (CI config, no behaviour); GREEN: `node --test scripts/harness-scripts.test.mjs` → exit 0 (18 pass); dind itself is unverified until the GitLab pipeline runs
- Contracts/Migrations added: none
- Decisions: none (design §2: `docker:27-dind` service, `DOCKER_HOST=tcp://docker:2375`, `DOCKER_TLS_CERTDIR=""`, `TESTCONTAINERS_HOST_OVERRIDE=docker`)
- Open Questions: runner must be privileged

### Task 4 (US-BF-04, US-BF-05, REQ-BF-02)
- Commits: cd6be26 feat(shared): add field validation errors and cross-slice events
- Verification: RED: `cd backend && ./mvnw -B test-compile` → FAIL (`FieldValidationException`, `FieldErrorDetail` not found); GREEN: `cd backend && ./mvnw -B test` → exit 0, 26 tests incl. `ArchitectureTest`
- Contracts/Migrations added: `shared/exception/{FieldErrorDetail,FieldValidationException}`, `shared/config/{AttemptCompletedEvent,AssessmentDeletedEvent}`; no migration
- Decisions: none (Decision 4.4 pointer in `AssessmentDeletedEvent` javadoc)
- Open Questions: none

### Task 5 (US-BF-06, REQ-BF-03)
- Commits: ce84729 feat(contracts): add cross-slice contract stubs with placeholder beans
- Verification: RED: `cd backend && ./mvnw -B test-compile` → FAIL (`ContractStubsTest`: owner packages and types missing); GREEN: `cd backend && ./mvnw -B test` → exit 0, 29 tests, 0 skipped, incl. `ArchitectureTest`
- Contracts/Migrations added: every §3 type in `audit.service`, `auth.service`, `studio.service(.view)`, `context.service(.dto)`, `assessment.service`, `evaluation.service`; 6 package-private `<Interface>Placeholder` beans; `RoleAccessTest` uses `/api/v1/{ranking,assessment}/__probe`; no migration
- Decisions: none (`@JsonValue` literals per design §2; `com.fasterxml.jackson.annotation` stays under Jackson 3)
- Open Questions: none

### Task 6 (docs)
- Commits: 66eadda docs: record backend-foundation
- Verification: RED: n/a (no behaviour); GREEN: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 (43 pass); `ste-lint.py` shows no hit in the new `docs/architecture.md` lines
- Contracts/Migrations added: none
- Decisions: none
- Open Questions: none
