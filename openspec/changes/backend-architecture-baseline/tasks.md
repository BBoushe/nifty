# Tasks: Backend Architecture Baseline

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `refactor/backend-architecture-baseline` (pushed to origin + gitlab)
- **Worktree:** `../nifty-backend-architecture-baseline`
- **Current:** Task 2 done (GREEN committed)
- **Next action:** Task 3, first step (GlobalExceptionHandlerTest)
- **Uncommitted changes:** no
- **PR / MR:** —
- **Open review findings:** —

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
- [ ] **3. Problem details** — REQ-BA-05, REQ-BA-06
  - [ ] Write `GlobalExceptionHandlerTest` (`@WebMvcTest` with a test-only controller; design §4): invalid `@Valid` record body → 400 `application/problem+json` with the field name; `DomainException(CONFLICT, "Config is already approved")` → 409 with that detail; `IllegalStateException("secret internal state")` → 500 whose body does not contain `secret internal state`, `trace` or `IllegalStateException`.
  - [ ] Run `./mvnw -B test -Dtest=GlobalExceptionHandlerTest`. Expect: FAIL (no handler).
  - [ ] Implement `DomainException` and `GlobalExceptionHandler` (design §2).
  - [ ] Run `./mvnw -B test`. Expect: exit 0.
  - [ ] Commit `feat(backend): add RFC 9457 GlobalExceptionHandler and DomainException`.
- [ ] **4. Quiet Maven output** — REQ-BA-07
  - [ ] Record `./mvnw -B test 2>&1 | wc -l` (before).
  - [ ] Apply design §5.
  - [ ] Record the line count after; check that `grep -c "Downloading from\|Downloaded from"` is 0 and that the banner is gone. Expect: exit 0 and fewer lines.
  - [ ] Commit `build(backend): cut maven and spring test log noise`.
- [ ] **5. Credentials from env only** — REQ-BA-08
  - [ ] Apply design §6.
  - [ ] Run `cd backend && ./mvnw -B test` and `docker compose config >/dev/null` (from the repo root). Expect: both exit 0.
  - [ ] Commit `fix(backend): read datasource password from the environment only`.
- [ ] **6. Rules and docs**
  - [ ] Add `backend-java.md` §8 Architecture (design §7), including the test pyramid table. Update §5 and §7 to point to it instead of repeating it.
  - [ ] Add the feature slice map (design §1) to `docs/architecture.md` under a heading `## Feature Slice Map` (the `ai-context-docs` change links to this anchor).
  - [ ] Align `docs/api.md` §3 with the ProblemDetail fields (`type`, `title`, `status`, `detail`, `instance`, `errors`).
  - [ ] Update `.claude/skills/backend-spring-check/SKILL.md`: `ArchitectureTest` is part of `./mvnw -B test`; quiet output is expected.
  - [ ] Add a `CHANGELOG.md` `[Unreleased]` entry.
  - [ ] Run `cd backend && ./mvnw -B test` and `OPENSPEC_TELEMETRY=0 openspec validate --all`. Expect: both exit 0.
  - [ ] Commit `docs(backend): document feature slices, error contract and test pyramid`.

## Delivery

- [ ] `git fetch origin && git rebase origin/dev`. On conflict in `CHANGELOG.md`, keep both entries.
- [ ] Re-run `cd backend && ./mvnw -B test` and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive backend-architecture-baseline -y --skip-specs` (architecture rules live in `backend-java.md`, not in a domain spec). Continue in `openspec/changes/archive/*-backend-architecture-baseline/tasks.md`. Commit `chore(specs): archive backend-architecture-baseline`.
- [ ] If `docs/feature_history.md` exists after the rebase, add this change's entry (format in its header; the harness test enforces it) and amend it into the archive commit.
- [ ] `node scripts/forge.mjs push`
- [ ] PR/MR body via `pr-memo`, then `node scripts/forge.mjs pr --title "refactor(backend): package-by-feature baseline with ArchUnit and ProblemDetail" --body-file <file>`. Record the links in Status.
- [ ] Cold review session (also run the `backend-spring-check` skill): verdict has no Blocking items.
- [ ] GitLab MR pipeline green; `gh pr checks <n>` green.
- [ ] Merge on GitHub only: `gh pr merge <n> --merge`.
- [ ] `node scripts/forge.mjs sync dev`, then `node scripts/forge.mjs status` prints 0/0 for every branch.
- [ ] After `git merge-base --is-ancestor refactor/backend-architecture-baseline origin/dev`: delete the branch on both remotes and locally; `git worktree remove ../nifty-backend-architecture-baseline`.

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
