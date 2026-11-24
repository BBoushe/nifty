# Design: Backend Architecture Baseline

## 1. Target Layout (Decision 1.10)

```text
de.fhdortmund.nifty
├── NiftyApplication.java
├── shared/
│   ├── config/      Role, SecurityConfig            (moved from config/)
│   └── exception/   DomainException, GlobalExceptionHandler   (new)
└── health/
    └── web/         HealthController (+ nested record HealthResponse)   (moved from health/)
```

`shared/` holds cross-cutting infrastructure with zero domain logic. Every feature uses `web/` (controllers, request/response records), `service/` (logic, `@Transactional`, event publishing) and `repository/` (`@Entity`, `JpaRepository`). A feature creates only the layers it needs.

### Feature Slice Map (put in `docs/architecture.md`; `ai-context-docs` links to it)

| OpenSpec spec | Backend package | Frontend views |
|---|---|---|
| `interviewer-studio` | `studio` | `views/studio/` |
| `assessment-chat` | `assessment` | `views/assessment/` |
| `evidence-pipeline` | `evidence` | — (backend only) |
| `formula-engine` | `formula` | `views/ranking/` (formula editor) |
| `skills-matrix-review` | `matrix` | `views/ranking/` |
| `team-builder` | `teams` | `views/teams/` |
| `candidate-results` | `results` | `views/assessment/` (results page) |

## 2. Errors

- `shared/exception/DomainException(HttpStatus status, String detail) extends RuntimeException`. Services throw it (or a subclass) for expected domain failures.
- `shared/exception/GlobalExceptionHandler extends ResponseEntityExceptionHandler`, annotated `@RestControllerAdvice`. The base class already maps Spring MVC exceptions (including `MethodArgumentNotValidException`) to `ProblemDetail`.
  - Override validation handling so the body lists invalid fields in an `errors` property.
  - Add `@ExceptionHandler(DomainException.class)` → `ProblemDetail.forStatusAndDetail(status, detail)`.
  - Add `@ExceptionHandler(Exception.class)` → 500 with detail `An unexpected error occurred.`. Log the exception server-side at ERROR.
- Keep `server.error.include-stacktrace: never` (the Boot default; set it explicitly).

## 3. ArchUnit

- Dependency: `com.tngtech.archunit:archunit-junit5`, test scope. Pin the latest stable release that supports Java 21 (check Maven Central).
- `src/test/java/de/fhdortmund/nifty/architecture/ArchitectureRules.java`: static factories that take the root package, for example `ArchRule featureRepositoryIsPrivate(String root)`. One factory per REQ-BA-01..04:
  - layout (every class except `NiftyApplication` resides in `root.shared.config`, `root.shared.exception` or `root.<feature>.(web|service|repository)`; controllers in `..web..`, `@Entity`/`JpaRepository` in `..repository..`, `@Service` in `..service..`);
  - repository privacy (custom `ArchCondition` comparing the first package segment after `root`; `shared` is not a feature);
  - layer direction;
  - `shared` → feature ban;
  - no `@Entity` in `@RestController` signatures.
- `ArchitectureTest`:
  1. Imports `de.fhdortmund.nifty` with `ImportOption.DoNotIncludeTests` and applies every rule with `.allowEmptyShould(true)`. Today there are no entities or services, so the rules hold vacuously.
  2. For each rule, imports one violating fixture package under `de.fhdortmund.nifty.archfixture.<case>` (test sources, mirroring the layout with features `alpha` and `beta`), and asserts `rule.evaluate(classes).hasViolation()`. This proves the rule works.

## 4. Spring Boot 4 Test Pitfalls

- `@WebMvcTest` lives in module `spring-boot-webmvc-test` (already in `pom.xml` as `spring-boot-starter-webmvc-test`). Use `@MockitoBean`, not the removed `@MockBean`.
- Spring Security is on the classpath, so `@WebMvcTest` loads the filter chain. Use `@WithMockUser` or import `SecurityConfig` and call a permitted test path.
- The test-only controller for `GlobalExceptionHandlerTest` lives in test sources.

## 5. Maven Noise (REQ-BA-07)

- `backend/.mvn/maven.config`: `-ntp` (no transfer progress; it applies to `./mvnw`, CI and the Docker build alike, so no CI file changes).
- `backend/src/test/resources/application-test.yml`: `spring.main.banner-mode: off`, `logging.level.root: WARN`.
- Measure `./mvnw -B test 2>&1 | wc -l` before and after. Log both numbers in the Handoff Log.

## 6. Credentials (REQ-BA-08)

`application.yml`: `password: ${SPRING_DATASOURCE_PASSWORD}` (no default). `docker-compose.yml` keeps its existing local-container fallback, which `.env.example` documents. Tests use the H2 profile and do not depend on it.

## 7. `backend-java.md` §8 Architecture (new)

| Rule | Enforcement |
|---|---|
| Layout (§1) and feature slice map | ArchUnit |
| Repository privacy, layer direction, `shared` ban | ArchUnit |
| No `@Entity` in controllers; record DTOs with `@Valid` | ArchUnit + review |
| `open-in-view: false`; `JOIN FETCH` / entity graphs for associations | Config + review |
| Secrets from env only | Review + secret scan (pre-commit) |
| Errors through `GlobalExceptionHandler` / `DomainException` | Test + review |
| Events: publish in services via `ApplicationEventPublisher`; side effects in `@TransactionalEventListener(phase = AFTER_COMMIT)` | Review |

Test pyramid table: unit = pure JVM with JUnit 5, Mockito and AssertJ (~80%); web slice = `@WebMvcTest` with mocked services; persistence slice = `@DataJpaTest` + PostgreSQL Testcontainers (from the first `@Entity`; H2 cannot run JSONB).
