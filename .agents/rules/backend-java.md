---
trigger: glob
globs: "backend/**"
description: Java 21, Spring Boot 4, Spring AI 2.0, Flyway, PostgreSQL, and team optimizer conventions for backend changes.
---

# Backend Java / Spring Boot Rules

> Also follow the canonical [`.agents/rules/coding-standards.md`](./coding-standards.md). It covers ETC/DRY, small testable functions, no Java Streams in hot/repeated shared functions, and minimal reusable classes. It also requires you to update [`docs/api.md`](../../docs/api.md) on every endpoint change.

1. **Runtime & Build:** Target **Java 21** strictly. Build and test with `./mvnw test` inside `backend/`.
2. **Spring Boot 4 & Security:**
   - Maintain the two-role model (`PROFESSOR`, `CANDIDATE`, Decision 1.15). Keep the roles in one enum and all access rules in `SecurityConfig` plus method-level `@PreAuthorize`, so a new role is one enum value and a few rules.
   - Candidates MUST NEVER access another candidate's data or unreleased scores/formulas.
   - Keep `/actuator/health`, `/api/v1/health`, and `/v3/api-docs` clean. Document them with `springdoc-openapi`.
3. **Database & Flyway:**
   - Never edit a Flyway migration that the database already applied. Always add a new `V<n>__description.sql` under `src/main/resources/db/migration/`.
   - Store the conversation in the append-only `conversation_entry` ledger (Decision 1.11). A trigger blocks `UPDATE` and `DELETE`. Only the assessment deletion may purge rows. Build `assessment-transcript/v1` from the ledger. Use relational tables for scores, evidence, and audit logs.
4. **Spring AI 2.0 Provider Portability & Deterministic Boundaries:**
   - Use Spring AI 2.0 abstractions (`ChatClient` / `ChatModel`) cleanly. This way, a later switch to other providers, or the addition of providers, stays config-driven. The provider today is `ki:connect` NRW / FH AI. Later providers can be the Gemini, Claude, or OpenAI APIs.
   - Do **not** add an artificial offline demo stub gateway.
   - Keep automated tests deterministic. Mock or replay `ChatClient` responses with synthetic fixtures.
   - App MUST boot cleanly when the configuration has no LLM API key (env-var driven, EU-endpoint ready).
   - Validate all LLM structured outputs against schemas. Run deterministic quote verification before you persist them.
   - Never pass chat history into Team Builder Mode B/C LLM calls. Always use a fresh, empty context.
   - Never pass rubrics, level descriptions, scores or evaluator output to the interviewer `ChatClient` (Decision 1.12). Start the evaluator from an `AFTER_COMMIT` event listener.
5. **Strict Controller DTO & Error Boundary:** Controllers MUST ONLY validate input (`@Valid` record DTOs), delegate to a single service method, and return explicit response records. Follow §9 for entity exposure, repository access and error handling.
6. **Formula Engine & Team Optimizer:**
   - MVP uses formula presets only (Decision 2.19). Never evaluate free-form formula expressions. The sandboxed SpEL editor is Post-MVP.
   - Keep the Simulated Annealing team optimizer pure Java, seeded and deterministic, with no Streams in its swap loop (Decision 1.20).
7. **Java 21 & Spring Conventions (Decision 3.13):**
   - Annotate each `@Service` class with `@Transactional(readOnly = true)`. Annotate each write method with `@Transactional`. Never put `@Transactional` on controllers or repositories.
   - Use `Optional` only as a return type, never for fields or parameters.
   - Prefer `record`, pattern matching, `sealed` types and `.toList()`.
   - Do not add Lombok in MVP. If the team adds it later, use only `@RequiredArgsConstructor` and `@Getter`. Never put `@Data` on an `@Entity`.
8. **Testing:** Follow the §9 test pyramid. Use recorded or mocked `ChatClient` fixtures. Never call live paid LLM APIs in automated tests.
9. **Architecture (Decision 1.10):** Use package-by-feature. `ArchitectureTest` (part of `./mvnw -B test`) enforces every row marked ArchUnit.

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
   | Use the layout above. Put controllers in `web`, entities/repositories in `repository`, and `@Service` in `service`. A feature creates only the layers it needs. | ArchUnit |
   | Only that feature uses a feature's `repository`. Other features call its `service`. | ArchUnit |
   | `web` never uses `repository`. `repository` never uses `service` or `web`. `shared` never depends on a feature. | ArchUnit |
   | Do not use `@Entity` in `@RestController` parameter or return types (this includes generic arguments). Use record DTOs with `@Valid`. | ArchUnit + review |
   | Set `spring.jpa.open-in-view: false`. Load associations with `JOIN FETCH` or entity graphs. | Config + review |
   | Secrets come from the environment only (no committed defaults in `application*.yml`). | Review + pre-commit secret scan |
   | Errors: services throw `DomainException(HttpStatus, detail)` (or a subclass) for expected failures. `GlobalExceptionHandler` answers with RFC 9457 `application/problem+json` (fields in `docs/api.md` §3). For anything unexpected, it answers with a generic 500 detail. It never returns a stack trace or exception class name. | `GlobalExceptionHandlerTest` + review |
   | Events: publish from services with `ApplicationEventPublisher`. Run side effects in `@TransactionalEventListener(phase = AFTER_COMMIT)`. Exception: `AssessmentDeletedEvent` listeners are synchronous `@EventListener` + `@Transactional(propagation = MANDATORY)`, so the purge runs in the deleting transaction (Decision 4.4). | Review |

   **Test pyramid**

   | Layer | Tools | Scope |
   |---|---|---|
   | Unit (~80%) | JUnit 5, Mockito, AssertJ, pure JVM | Domain logic, calculators, validators |
   | Web slice | `@WebMvcTest` + `@MockitoBean` services (Boot 4: not `@MockBean`) | Controllers, validation, error mapping |
   | Persistence slice | `@DataJpaTest` + PostgreSQL Testcontainers. Every `@SpringBootTest` and `@DataJpaTest` extends `de.fhdortmund.nifty.PostgresContainerBase` (Decision 1.22). H2 cannot run JSONB or triggers. | Repositories, queries, Flyway |
   | Architecture | ArchUnit `ArchitectureTest` | §9 rules |

   Test output is quiet by design (`.mvn/maven.config` `-ntp`, profile `test`: no banner, root `WARN`). Spring tests use `@ActiveProfiles("test")`.
