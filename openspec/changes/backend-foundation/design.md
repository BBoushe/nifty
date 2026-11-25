# Design: Backend Foundation

## 1. Sources

`docs/feature_backlog.md` §1 (US-BF-01..06), `docs/tickets/00-parallel-waves-and-contracts.md` §1 shared conventions and §3 signatures, `docs/ai_docs/spring-boot-4.md`, `docs/ai_docs/spring-ai-2-kiconnect.md`, `.agents/rules/backend-java.md`.

## 2. Decisions inside this change

- **Test base:** `de.fhdortmund.nifty.PostgresContainerBase` (test sources) holds one static `postgres:17-alpine` container with `@ServiceConnection`, shared by all subclasses. `application-test.yml` loses the H2 datasource. The `h2` test dependency goes away. Flyway runs `V1` on the container.
- **Stubs:** each §3 type goes in its owner package exactly as written in `00` §3 (`audit.service`, `auth.service`, `studio.service` and `studio.service.view`, `context.service` and `context.service.dto`, `assessment.service`, `evaluation.service`). Enums carry `@JsonValue` with the JSON/DB literal (for example `"N/A"`, `"follow_up"`, `"auto_released"`).
- **Placeholders:** one `@Service` class per interface, named `<Interface>Placeholder`, in the same package. Every method throws `UnsupportedOperationException("<Interface> is not implemented yet: owner <slice>")`. Owners delete their placeholder when the real bean lands.
- **Events:** records in `shared/config`, no feature imports (ArchUnit `sharedIsFeatureFree`).
- **Validation errors:** `FieldErrorDetail(field, message, code)` and `FieldValidationException` in `shared/exception`, mapped in the existing `GlobalExceptionHandler`. Response: `application/problem+json`, status `400`, `errors: [{field, message, code}]`, no stack trace.
- **Role probes:** `RoleAccessTest` uses `/api/v1/ranking/__probe` and `/api/v1/assessment/__probe`.
- **GitLab dind:** the `backend` job gets `services: [docker:dind]` and the Testcontainers variables (`DOCKER_HOST`, `DOCKER_TLS_CERTDIR`, `TESTCONTAINERS_HOST_OVERRIDE`). Commands stay the same, so the CI drift test still passes.

## 3. Risks

- GitLab runner without privileged mode cannot run dind. The pipeline shows it, and the PR reports it to the user.
