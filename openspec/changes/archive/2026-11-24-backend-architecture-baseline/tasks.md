# Tasks: Backend Architecture Baseline

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `refactor/backend-architecture-baseline` merged into `dev` (bcc1433); deleted on origin, gitlab and locally
- **Worktree:** removed
- **Current:** done: PR #8 merged on GitHub, `forge.mjs sync dev` ran, MR !7 shows merged, `forge.mjs status` 0/0
- **Next action:** none
- **Uncommitted changes:** no
- **PR / MR:** https://github.com/BBoushe/nifty/pull/8 (merged) · https://git.inf.fh-dortmund.de/01/alpac001/nifty/-/merge_requests/7 (merged)
- **Open review findings:** non-blocking only: composite ArchUnit sub-rules (layout packages/persistence/services, repository→service/web) lack their own fixtures; catch-all handler turns `@ResponseStatus` exceptions into 500 (use `DomainException`). Fixed: empty-import guard, `errors` wording in `docs/api.md` §3

## Tasks

Owned files: `backend/**`, `.agents/rules/backend-java.md`, `.claude/skills/backend-spring-check/SKILL.md`, `docs/architecture.md`, `docs/api.md` §3. Never touch `.github/` or `.gitlab-ci.yml`. Decision 1.10 is already recorded. Read `design.md` and `.agents/rules/backend-java.md` first.

- [x] **1. Architecture rules with fixtures (RED)** — REQ-BA-01..04
  - [x] Add the ArchUnit test dependency (design §3).
  - [x] Write `ArchitectureRules`, the `archfixture` packages and `ArchitectureTest` (design §3).
  - [x] Run `cd backend && ./mvnw -B test -Dtest=ArchitectureTest`. Expect: the fixture assertions pass, and the layout rule FAILS on `de.fhdortmund.nifty.config.SecurityConfig` and `de.fhdortmund.nifty.health.HealthController`.
  - [x] Commit `test(backend): add ArchUnit architecture rules with violating fixtures`.
- [x] **2. Package moves (GREEN)** — REQ-BA-01
  - [x] Move `config/*` → `shared/config/` and `health/HealthController` → `health/web/` (design §1). Update imports.
  - [x] Run `cd backend && ./mvnw -B test`. Expect: exit 0, including `HealthEndpointTest` and `ArchitectureTest`.
  - [x] Commit `refactor(backend): move to package-by-feature layout`.
- [x] **3. Problem details** — REQ-BA-05, REQ-BA-06
  - [x] Write `GlobalExceptionHandlerTest` (`@WebMvcTest` with a test-only controller; design §4): invalid `@Valid` record body → 400 `application/problem+json` with the field name; `DomainException(CONFLICT, "Config is already approved")` → 409 with that detail; `IllegalStateException("secret internal state")` → 500 whose body does not contain `secret internal state`, `trace` or `IllegalStateException`.
  - [x] Run `./mvnw -B test -Dtest=GlobalExceptionHandlerTest`. Expect: FAIL (no handler).
  - [x] Implement `DomainException` and `GlobalExceptionHandler` (design §2).
  - [x] Run `./mvnw -B test`. Expect: exit 0.
  - [x] Commit `feat(backend): add RFC 9457 GlobalExceptionHandler and DomainException`.
- [x] **4. Quiet Maven output** — REQ-BA-07
  - [x] Record `./mvnw -B test 2>&1 | wc -l` (before).
  - [x] Apply design §5.
  - [x] Record the line count after; check that `grep -c "Downloading from\|Downloaded from"` is 0 and that the banner is gone. Expect: exit 0 and fewer lines.
  - [x] Commit `build(backend): cut maven and spring test log noise`.
- [x] **5. Credentials from env only** — REQ-BA-08
  - [x] Apply design §6.
  - [x] Run `cd backend && ./mvnw -B test` and `docker compose config >/dev/null` (from the repo root). Expect: both exit 0.
  - [x] Commit `fix(backend): read datasource password from the environment only`.
- [x] **6. Rules and docs**
  - [x] Add `backend-java.md` §8 Architecture (design §7), including the test pyramid table. Update §5 and §7 to point to it instead of repeating it.
  - [x] Add the feature slice map (design §1) to `docs/architecture.md` under a heading `## Feature Slice Map` (the `ai-context-docs` change links to this anchor).
  - [x] Align `docs/api.md` §3 with the ProblemDetail fields (`type`, `title`, `status`, `detail`, `instance`, `errors`).
  - [x] Update `.claude/skills/backend-spring-check/SKILL.md`: `ArchitectureTest` is part of `./mvnw -B test`; quiet output is expected.
  - [x] Add a `CHANGELOG.md` `[Unreleased]` entry.
  - [x] Run `cd backend && ./mvnw -B test` and `OPENSPEC_TELEMETRY=0 openspec validate --all`. Expect: both exit 0.
  - [x] Commit `docs(backend): document feature slices, error contract and test pyramid`.

## Delivery

- [x] `git fetch origin && git rebase origin/dev`. On conflict in `CHANGELOG.md`, keep both entries.
- [x] Re-run `cd backend && ./mvnw -B test` and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive backend-architecture-baseline -y --skip-specs` (architecture rules live in `backend-java.md`, not in a domain spec). Continue in `openspec/changes/archive/*-backend-architecture-baseline/tasks.md`. Commit `chore(specs): archive backend-architecture-baseline`.
- [x] If `docs/feature_history.md` exists after the rebase, add this change's entry (format in its header; the harness test enforces it) and amend it into the archive commit. — n/a: file absent after rebase
- [x] `node scripts/forge.mjs push`
- [x] PR/MR body via `pr-memo`, then `node scripts/forge.mjs pr --title "refactor(backend): package-by-feature baseline with ArchUnit and ProblemDetail" --body-file <file>`. Record the links in Status.
- [x] Cold review session (also run the `backend-spring-check` skill): verdict has no Blocking items.
- [x] GitLab MR pipeline green; `gh pr checks <n>` green.
- [x] Merge on GitHub only: `gh pr merge <n> --merge`.
- [x] `node scripts/forge.mjs sync dev`, then `node scripts/forge.mjs status` prints 0/0 for every branch.
- [x] After `git merge-base --is-ancestor refactor/backend-architecture-baseline origin/dev`: delete the branch on both remotes and locally; `git worktree remove ../nifty-backend-architecture-baseline`.

## Handoff Log

### Task 1 — Architecture rules with fixtures (RED)
- **Commits:** `test(backend): add ArchUnit architecture rules with violating fixtures`
- **Verification:** RED `cd backend && ./mvnw -B test -Dtest=ArchitectureTest` → FAIL (12 run, 1 failure: layout rule on `config.Role`, `config.SecurityConfig`, `health.HealthController` (+ nested `HealthResponse`); all 5 fixture checks pass)
- **Contracts/Migrations added:** none (test dep `archunit-junit5` 1.5.1, latest on Maven Central)
- **Decisions:** fixtures live in `de.fhdortmund.archfixture.<case>`, not `de.fhdortmund.nifty.archfixture` (design §3): `@SpringBootApplication` would component/entity-scan them in `HealthEndpointTest`; rule factories take the root, so behaviour is unchanged
- **Open Questions:** none

### Task 2 — Package moves (GREEN)
- **Commits:** `refactor(backend): move to package-by-feature layout`
- **Verification:** RED `cd backend && ./mvnw -B test -Dtest=ArchitectureTest` → FAIL (Task 1); GREEN `cd backend && ./mvnw -B test` → exit 0 (14 tests: ArchitectureTest 12, HealthEndpointTest 2)
- **Contracts/Migrations added:** none (`/api/v1/health` unchanged; `HealthResponse` stays nested in `HealthController`)
- **Decisions:** none new (Decision 1.10 layout)
- **Open Questions:** none

### Task 3 — Problem details
- **Commits:** `feat(backend): add RFC 9457 GlobalExceptionHandler and DomainException`
- **Verification:** RED `cd backend && ./mvnw -B test -Dtest=GlobalExceptionHandlerTest` → FAIL (compile: `DomainException` missing); GREEN `cd backend && ./mvnw -B test` → exit 0 (18 tests)
- **Contracts/Migrations added:** error envelope = ProblemDetail `type` (`about:blank` default; Spring 7 leaves it null, so `createResponseEntity` sets it), `title`, `status`, `detail`, `instance`, plus `errors: [{field, message}]` on 400 validation; `server.error.include-stacktrace: never`
- **Decisions:** `AccessDeniedException`/`AuthenticationException` are rethrown so `@EnableMethodSecurity` denials stay 401/403 instead of hitting the catch-all 500 (tested); test controller needs `@Import` because Boot excludes classes nested in tests from scanning
- **Open Questions:** none

### Task 4 — Quiet Maven output
- **Commits:** `build(backend): cut maven and spring test log noise`, `build(backend): copy maven.config into the docker build` (design §5 promised `-ntp` there; Dockerfile only copied `pom.xml`/`src`; `docker build` → exit 0)
- **Verification:** RED `./mvnw -B test 2>&1 | wc -l` → 236 lines, 2 Spring banners, 41 framework INFO lines (warm cache, 0 downloads); GREEN `cd backend && ./mvnw -B test` → exit 0, 59 lines, 0 `Downloading from|Downloaded from`, 0 banners, 0 INFO framework logs
- **Contracts/Migrations added:** `backend/.mvn/maven.config` (`-ntp`), `application-test.yml` (banner off, root WARN), `src/test/resources/logback-test.xml` (root WARN before Boot reads the profile; console only)
- **Decisions:** `GlobalExceptionHandlerTest` activates profile `test` and turns off only the handler logger, which otherwise prints its expected ~110-line stack trace; remaining output is JVM/Mockito agent and framework WARN lines
- **Open Questions:** none

### Task 5 — Credentials from env only
- **Commits:** `fix(backend): read datasource password from the environment only`
- **Verification:** RED `grep -qE '^ +password: \$\{SPRING_DATASOURCE_PASSWORD\}$' backend/src/main/resources/application.yml` → FAIL (committed default); GREEN same check → exit 0, `cd backend && ./mvnw -B test` → exit 0 (18 tests), `docker compose config >/dev/null` → exit 0 (backend env has `SPRING_DATASOURCE_PASSWORD`)
- **Contracts/Migrations added:** none; running the backend outside compose now needs `SPRING_DATASOURCE_PASSWORD` set (startup fails on the unresolved placeholder)
- **Decisions:** none new (compose fallback kept per design §6)
- **Open Questions:** none

### Task 6 — Rules and docs
- **Commits:** `docs(backend): document feature slices, error contract and test pyramid`
- **Verification:** RED `git show HEAD~:docs/architecture.md | grep -q '^## Feature Slice Map'` → FAIL (no anchor yet); GREEN `cd backend && ./mvnw -B test` → exit 0 (18 tests), `OPENSPEC_TELEMETRY=0 openspec validate --all` → exit 0 (14/14)
- **Contracts/Migrations added:** `docs/api.md` §3 now lists `type`, `title`, `status`, `detail`, `instance`, `errors` and the source → status table; `docs/architecture.md#feature-slice-map`
- **Decisions:** none new; `backend-java.md` §5/§7 now point to §8 instead of repeating it
- **Open Questions:** none
