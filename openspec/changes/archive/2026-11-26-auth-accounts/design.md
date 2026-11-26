# Design: Professor Accounts and Sessions

## 1. Sources

`openspec/specs/auth-access/spec.md`, Decisions 1.15, 1.17, 1.23, 1.24, `docs/tickets/00-parallel-waves-and-contracts.md` §1 and §3.3, `docs/api.md` §2 and §5, `.agents/rules/backend-java.md`, `.agents/rules/frontend-vue.md`.

## 2. Backend

- **Migration `V2__auth_accounts.sql`:** `professor_account(id UUID PK, email VARCHAR(320) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)`. Store the email in lower case.
- **Slice boundary:** `SecurityConfig` stays in `shared/config` and imports nothing from `auth` (ArchUnit). `auth.service.ProfessorUserDetailsService` is the only `UserDetailsService` bean. Spring wires it into the `AuthenticationManager` without a direct import.
- **`SecurityConfig` changes:** session policy `IF_REQUIRED`; remove `httpBasic`; permit `POST /api/v1/auth/login`, `POST /api/v1/auth/logout` and `POST /api/v1/auth/invite/**`; CSRF on with `CookieCsrfTokenRepository.withHttpOnlyFalse()` and the SPA request handler (cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`); an authentication entry point that answers `401` ProblemDetail instead of a Basic challenge. Keep the role rules of REQ-AA-01.
- **Cookie:** `server.servlet.session.cookie.http-only: true`, `same-site: lax`; `secure` from an env variable (true outside local dev).
- **Login:** `auth.web.AuthController` `POST /api/v1/auth/login` `{email, password}` → `AuthenticationManager.authenticate`, save the context with `HttpSessionSecurityContextRepository`, change the session ID, answer `204`. Unknown email and wrong password give the same `401` detail (REQ-AA-02).
- **Logout and me:** `POST /api/v1/auth/logout` → invalidate the session, `204`. `GET /api/v1/auth/me` → `{role, professorId}` for a professor, `401` without a session.
- **Principal:** `auth.service.NiftyPrincipal(UUID id, Role role)` as the authentication principal. `AuthContextServiceImpl.currentProfessorId()` returns `id` for a professor and throws `DomainException(FORBIDDEN)` otherwise. `currentCandidate()` throws `DomainException(FORBIDDEN)` until `invite-links`. Delete `AuthContextServicePlaceholder`.
- **Bootstrap (Decision 1.24):** `auth.service.ProfessorBootstrap` (`ApplicationRunner`) reads `nifty.bootstrap.professor.email` and `.password` (from `NIFTY_BOOTSTRAP_PROFESSOR_EMAIL` and `NIFTY_BOOTSTRAP_PROFESSOR_PASSWORD`). If both are set and no account has the email, it creates one with a `DelegatingPasswordEncoder` hash. It never changes an existing account and never logs the password. Add both keys to `.env.example` (empty) and pass them to the backend service in `docker-compose.yml`.

## 3. Frontend

- `frontend/src/api/client.ts`: on every non-GET request, read the `XSRF-TOKEN` cookie and send `X-XSRF-TOKEN`. `postSse` does the same.
- `frontend/src/stores/auth.ts` (Pinia): `me` from `GET /api/v1/auth/me`, `login`, `logout`.
- `frontend/src/views/auth/SignInView.vue` (PrimeVue form) at `/sign-in`, in `views/auth/routes.ts`.
- `frontend/src/views/auth/guard.ts`: `/ranking/*` needs `PROFESSOR`, `/assessment/*` needs `CANDIDATE`, otherwise redirect to `/sign-in`. Register it in `router/index.ts` with one line.
