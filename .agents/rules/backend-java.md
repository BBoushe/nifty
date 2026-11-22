---
trigger: glob
globs: "backend/**"
description: Java 21, Spring Boot 4, Spring AI 2.0, Flyway, PostgreSQL, and Timefold conventions for backend changes.
---

# Backend Java / Spring Boot Rules

1. **Runtime & Build:** Target **Java 21** strictly and build/test using `./mvnw test` inside `backend/`.
2. **Spring Boot 4 & Security:**
   - Maintain the two-role model (`PROFESSOR` / `REVIEWER` vs. `CANDIDATE`). Candidates MUST NEVER access another candidate's data or unapproved scores/formulas.
   - Keep `/actuator/health`, `/api/v1/health`, and `/v3/api-docs` clean and documented via `springdoc-openapi`.
3. **Database & Flyway:**
   - Never edit already-applied Flyway migrations; always add a new `V<n>__description.sql` under `src/main/resources/db/migration/`.
   - Store `assessment-transcript/v1` payloads as PostgreSQL `JSONB` with relational tables for scores, evidence, and audit logs.
4. **Spring AI 2.0 & Deterministic Boundaries:**
   - App MUST boot cleanly when no LLM API key is configured (env-var driven, EU-endpoint ready).
   - Validate all LLM structured outputs against schemas and run deterministic quote verification before persistence.
   - Never pass chat history into Team Builder Mode B/C LLM calls (always use a fresh, empty context).
5. **Formula Engine & Timefold:**
   - Evaluate custom formula expressions strictly inside a sandboxed context (e.g., SpEL `SimpleEvaluationContext` with allowlisted functions).
   - Keep Timefold Solver constraints pure, deterministic, and explainable.
6. **Testing:** Write JUnit 5 + Mockito unit tests and PostgreSQL Testcontainers integration tests. Use recorded LLM fixtures — never call live paid LLM APIs in automated tests.
