# Tasks: `auth-accounts`

## Status
- **Branch:** `feat/auth-accounts`
- **Worktree:** `../nifty-auth-accounts`
- **Current:** not started
- **Next action:** Task 1
- **Uncommitted changes:** no
- **PR / MR:** none yet
- **Open review findings:** none

## Tasks

### Task 1: Accounts, bootstrap and principal (REQ-AA-07)
- [ ] RED: `ProfessorBootstrapTest` (extends `PostgresContainerBase`): creates one account from the two properties; a second run changes nothing; no property set → no account. `AuthContextServiceImplTest`: professor principal → ID; no professor → `403`.
- [ ] GREEN: `V2__auth_accounts.sql`, entity and repository in `auth/repository`, `ProfessorUserDetailsService`, `ProfessorBootstrap`, `NiftyPrincipal`, `AuthContextServiceImpl`; delete `AuthContextServicePlaceholder`; `.env.example` and `docker-compose.yml` keys.
- [ ] Verify: `cd backend && ./mvnw -B test` → exit 0; `docker compose config` → exit 0; commit `feat(auth): professor accounts with env bootstrap`

### Task 2: Login, logout, me and SecurityConfig (REQ-AA-02, REQ-AA-06)
- [ ] RED: `AuthControllerTest` (`@SpringBootTest`, `PostgresContainerBase`, MockMvc): login `204` + session cookie; wrong password and unknown email give the same `401` body; logout without CSRF `403`, with CSRF `204`; `me` after logout `401`. `RoleAccessTest` still passes (add `csrf()` where it posts).
- [ ] GREEN: `AuthController`, `SecurityConfig` changes (design §2), cookie properties, `401` ProblemDetail entry point.
- [ ] Verify: `cd backend && ./mvnw -B test` (incl. `ArchitectureTest`) → exit 0; update `docs/api.md` §2 and §5; commit `feat(auth): session login, logout and CSRF`

### Task 3: US-AU-02 Sign-in page, store, guards, CSRF header
- [ ] RED: Vitest: `client.test.ts` sends `X-XSRF-TOKEN` on POST from the cookie; `guard.test.ts` redirects a missing or wrong role to `/sign-in`; `SignInView.test.ts` calls `login` and shows the `401` detail.
- [ ] GREEN: design §3.
- [ ] Verify: `cd frontend && npm run typecheck && npm run test && npm run lint && npm run build` → exit 0; commit `feat(frontend): sign-in page, auth store and route guards`

### Task 4: Docs
- [ ] `docs/architecture.md` (sessions, bootstrap), `docs/setup.md` (bootstrap keys), `CHANGELOG.md`
- [ ] Verify: harness tests → exit 0; commit `docs: record auth-accounts`

## Delivery
- [ ] `git fetch origin && git merge origin/dev` (never rebase a pushed branch, no force pushes)
- [ ] Run `cd backend && ./mvnw -B test`, `cd frontend && npm run typecheck && npm run test && npm run lint && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all --strict` and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive auth-accounts -y`, set its `docs/feature_backlog.md` row to `done`, add a `docs/feature_history.md` entry (`PR/MR: pending`), commit `chore(specs): archive auth-accounts`
- [ ] Ask the human, then `node scripts/forge.mjs push` and open the PR/MR with `pr-memo` (`node scripts/forge.mjs pr ... --base dev`)
- [ ] Stop. Teammate A runs the cold review, checks CI and merges.

## Handoff Log
