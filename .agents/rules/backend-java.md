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
5. **Strict Controller DTO & Error Boundary:**
   - Controllers MUST ONLY validate input (`@Valid`), delegate to a single service method, and return explicit response DTOs — never expose JPA entities or call repositories directly from controllers.
   - Handle all API errors through a single `@RestControllerAdvice` returning a consistent `ProblemDetail`/JSON error envelope across every endpoint.
6. **Formula Engine & Timefold:**
   - Evaluate custom formula expressions strictly inside a sandboxed context (e.g., SpEL `SimpleEvaluationContext` with allowlisted functions).
   - Keep Timefold Solver constraints pure, deterministic, and explainable.
7. **Testing:** Write JUnit 5 + Mockito unit tests and PostgreSQL Testcontainers integration tests. Use recorded/mocked `ChatClient` fixtures — never call live paid LLM APIs in automated tests.
