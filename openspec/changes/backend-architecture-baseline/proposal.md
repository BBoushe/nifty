# Proposal: Backend Architecture Baseline

## Context & Problem

The course guide "Spring Boot Architecture Best Practices" (`docs/prompts/done/2 - Spring Boot Architecture Best Practices.pdf`) sets five rules: feature slices, strict DTOs, RFC 7807/9457 errors, commit-safe events, and layered tests. The backend is still a skeleton, so adopting them now costs little:

| Rule | Today |
|---|---|
| Package-by-feature (`shared/` + `<feature>/{web,service,repository}`) | Hybrid: `config/` (cross-cutting) and `health/` (feature, no layers) |
| Allowed vs. forbidden dependencies (no foreign repository access) | Written nowhere, enforced nowhere |
| `@Valid` record DTOs | Rule in `backend-java.md` §5; no request DTO exists yet |
| `open-in-view: false` | **Done** (`application.yml`) |
| Secrets via env only | API key: done. DB password: committed default `nifty_dev_password` in `application.yml` |
| Central RFC 9457 `ProblemDetail` | Rule in `backend-java.md` §5 and `docs/api.md` §3; **no handler** |
| Domain events with `@TransactionalEventListener(AFTER_COMMIT)` | Not needed yet (no feature publishes events) |
| Test pyramid (unit / `@WebMvcTest` / `@DataJpaTest` + Testcontainers) | One `@SpringBootTest` on H2 |
| Token cost | `./mvnw -B test` prints the Spring banner, download progress and INFO logs. RTK does not filter `./mvnw`. |

Decision 1.10 records the target. Feature slices also let agents read one folder and keep each PR's blast radius small.

## Scope

- Move existing classes into `shared/config/` and `health/web/`.
- `DomainException` + `GlobalExceptionHandler` (RFC 9457 `ProblemDetail`, no stack traces).
- ArchUnit tests for layout and dependency rules, each proven against a violating fixture.
- `backend-java.md` §8 Architecture (layout, dependencies, data rules, errors, events, test pyramid).
- Feature slice map in `docs/architecture.md`; error fields in `docs/api.md` §3.
- Quieter Maven test output; DB password from env only in `application.yml`.

## Out of Scope

- Testcontainers persistence tests (they arrive with the first `@Entity`; see `docs/feature_backlog.md`).
- Domain event code (written rule only until a feature needs it).
- CI files (`.github/`, `.gitlab-ci.yml`): the `forge-ci-parity` drift test compares CI commands, so this change must not touch them.
- Frontend (already has feature-grouped views in coding standard #6).
