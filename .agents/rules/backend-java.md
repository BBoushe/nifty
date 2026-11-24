---
trigger: glob
globs: "backend/**"
description: Java 21, Spring Boot 4, Spring AI 2.0, Flyway, PostgreSQL, and Timefold conventions for backend changes.
---

# Backend Java / Spring Boot Rules

> Also follow canonical [`.agents/rules/coding-standards.md`](./coding-standards.md) (ETC/DRY, small testable functions, avoid Java Streams in hot/repeated shared functions, minimal reusable classes, and update [`docs/api.md`](../../docs/api.md) on every endpoint change).

1. **Runtime & Build:** Target **Java 21** strictly and build/test using `./mvnw test` inside `backend/`.
2. **Spring Boot 4 & Security:**
   - Maintain the two-role model (`PROFESSOR` / `REVIEWER` vs. `CANDIDATE`). Candidates MUST NEVER access another candidate's data or unapproved scores/formulas.
   - Keep `/actuator/health`, `/api/v1/health`, and `/v3/api-docs` clean and documented via `springdoc-openapi`.
3. **Database & Flyway:**
   - Never edit already-applied Flyway migrations; always add a new `V<n>__description.sql` under `src/main/resources/db/migration/`.
   - Store `assessment-transcript/v1` payloads as PostgreSQL `JSONB` with relational tables for scores, evidence, and audit logs.
4. **Spring AI 2.0 Provider Portability & Deterministic Boundaries:**
   - Use Spring AI 2.0 abstractions (`ChatClient` / `ChatModel`) cleanly so switching or adding providers later (from `ki:connect` NRW / FH AI today to Gemini, Claude, or OpenAI APIs later) stays config-driven — do **not** add an artificial offline demo stub gateway; keep automated tests deterministic by mocking/replaying `ChatClient` responses with synthetic fixtures.
   - App MUST boot cleanly when no LLM API key is configured (env-var driven, EU-endpoint ready).
   - Validate all LLM structured outputs against schemas and run deterministic quote verification before persistence.
   - Never pass chat history into Team Builder Mode B/C LLM calls (always use a fresh, empty context).
5. **Strict Controller DTO & Error Boundary:** Controllers MUST ONLY validate input (`@Valid` record DTOs), delegate to a single service method, and return explicit response records. Entity exposure, repository access and error handling follow §8.
6. **Formula Engine & Timefold:**
   - Evaluate custom formula expressions strictly inside a sandboxed context (e.g., SpEL `SimpleEvaluationContext` with allowlisted functions).
   - Keep Timefold Solver constraints pure, deterministic, and explainable.
7. **Testing:** Follow the §8 test pyramid. Use recorded/mocked `ChatClient` fixtures — never call live paid LLM APIs in automated tests.
8. **Architecture (Decision 1.10):** Package-by-feature. `ArchitectureTest` (part of `./mvnw -B test`) enforces every row marked ArchUnit.

   ```text
   de.fhdortmund.nifty
   ├── NiftyApplication          (only class in the root package)
   ├── shared/config/            cross-cutting config (Role, SecurityConfig), zero domain logic
   ├── shared/exception/         DomainException, GlobalExceptionHandler
   └── <feature>/                one per OpenSpec spec (map: docs/architecture.md#feature-slice-map)
       ├── web/                  @RestController, request/response records
       ├── service/              logic, @Transactional, event publishing
       └── repository/           @Entity, JpaRepository (private to the feature)
   ```

   | Rule | Enforcement |
   |---|---|
   | Layout above; controllers in `web`, entities/repositories in `repository`, `@Service` in `service`. A feature creates only the layers it needs. | ArchUnit |
   | A feature's `repository` is used only by that feature; other features call its `service`. | ArchUnit |
   | `web` never uses `repository`; `repository` never uses `service` or `web`; `shared` never depends on a feature. | ArchUnit |
   | No `@Entity` in `@RestController` parameter or return types (generic arguments included); record DTOs with `@Valid`. | ArchUnit + review |
   | `spring.jpa.open-in-view: false`; load associations with `JOIN FETCH` or entity graphs. | Config + review |
   | Secrets come from the environment only (no committed defaults in `application*.yml`). | Review + pre-commit secret scan |
   | Errors: services throw `DomainException(HttpStatus, detail)` (or a subclass) for expected failures; `GlobalExceptionHandler` answers RFC 9457 `application/problem+json` (fields in `docs/api.md` §3), a generic 500 detail for anything unexpected, never a stack trace or exception class name. | `GlobalExceptionHandlerTest` + review |
   | Events: publish from services via `ApplicationEventPublisher`; run side effects in `@TransactionalEventListener(phase = AFTER_COMMIT)`. | Review |

   **Test pyramid**

   | Layer | Tools | Scope |
   |---|---|---|
   | Unit (~80%) | JUnit 5, Mockito, AssertJ, pure JVM | Domain logic, calculators, validators |
   | Web slice | `@WebMvcTest` + `@MockitoBean` services (Boot 4: not `@MockBean`) | Controllers, validation, error mapping |
   | Persistence slice | `@DataJpaTest` + PostgreSQL Testcontainers (from the first `@Entity`; H2 cannot run JSONB) | Repositories, queries, Flyway |
   | Architecture | ArchUnit `ArchitectureTest` | §8 rules |

   Test output is quiet by design (`.mvn/maven.config` `-ntp`, profile `test`: no banner, root `WARN`); Spring tests use `@ActiveProfiles("test")`.
