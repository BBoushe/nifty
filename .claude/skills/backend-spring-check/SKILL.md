---
name: backend-spring-check
description: Validate the Java 21 Spring Boot 4 backend (Maven build, unit/smoke tests, Flyway SQL, Security, OpenAPI, and Spring AI config).
---

# Skill: Backend Spring Check

Use this skill before committing or reviewing any change under `backend/`.

## Team Settings (Update via First-Run Bootstrap once resolved)

- **LLM Provider & EU Region:** `[TBD — see docs/decisions.md O2; must boot with placeholder env vars]`
- **Java Version:** Java 21 (`backend/pom.xml`)

## Checklist & Verification Commands

1. Confirm Java 21 is active (`java -version`).
2. Run full backend tests:
   ```sh
   cd backend && ./mvnw test
   ```
3. Verify no secrets or hardcoded API keys appear in `application*.yml` or `src/`.
4. Verify any schema changes use a new immutable Flyway migration (`src/main/resources/db/migration/V<n>__*.sql`).
5. Confirm role boundaries (`PROFESSOR`/`REVIEWER` vs. `CANDIDATE`) and OpenAPI annotations for any new endpoints.
