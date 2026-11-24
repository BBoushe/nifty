# Living API Documentation (`docs/api.md`)

> **Maintenance Rule (Decision 2.9 & `.agents/rules/coding-standards.md` #4):** Every PR that adds, modifies, or removes a backend controller endpoint, DTO, auth rule, or SSE event **MUST** update this file in the same commit/PR. Live machine-readable OpenAPI remains available at `/v3/api-docs` and `/swagger-ui.html`.

## 1. Base URLs & Live OpenAPI

| Environment | Base URL | OpenAPI JSON | Swagger UI |
|---|---|---|---|
| **Local Backend** | `http://localhost:8080` | `http://localhost:8080/v3/api-docs` | `http://localhost:8080/swagger-ui.html` |
| **Docker / Nginx Proxy** | `http://localhost:3000/api` | `http://localhost:3000/v3/api-docs` *(via backend `:8080`)* | `http://localhost:8080/swagger-ui.html` |

## 2. Authentication & Role Boundaries (`SecurityConfig`)

| Path Prefix | Allowed Roles | Notes |
|---|---|---|
| `/api/v1/health`, `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Public (`permitAll`) | Liveness, readiness, and OpenAPI schema |
| `/api/v1/ranking/**` | `PROFESSOR`, `REVIEWER` | Ranking View, Studio, Matrix, Approval Queue, Team Builder |
| `/api/v1/assessment/**` | `CANDIDATE`, `PROFESSOR` | Assessment Chat SSE & post-approval Candidate Results (candidates only see their own data; never unapproved scores) |
| All other routes | Authenticated | Default deny for unauthenticated callers |

## 3. Standard Error Envelope

Every API error is an RFC 9457 `ProblemDetail` with media type `application/problem+json`, produced by `shared/exception/GlobalExceptionHandler` (`@RestControllerAdvice`; rules in `.agents/rules/backend-java.md` §8). Responses never contain stack traces or exception class names.

| Field | Meaning |
|---|---|
| `type` | Problem type URI; `about:blank` unless a specific type is defined |
| `title` | Short summary, defaults to the HTTP reason phrase (e.g. `Bad Request`) |
| `status` | HTTP status code |
| `detail` | Human-readable explanation, safe for clients |
| `instance` | Request path that failed |
| `errors` | Only on `400` validation failures: list of `{ "field", "message" }` |

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/v1/ranking/scores/42/override",
  "errors": [{ "field": "reason", "message": "must not be blank" }]
}
```

| Source | Status | `detail` |
|---|---|---|
| `@Valid` body violation | `400` | Spring's validation summary, plus `errors` |
| `DomainException(status, detail)` thrown by a service | its `status` | its `detail` |
| Spring MVC errors (404, 405, 415, ...) | as mapped by `ResponseEntityExceptionHandler` | Spring's default |
| Any other exception | `500` | `An unexpected error occurred.` (logged server-side) |

## 4. Implemented Endpoints

| Method | Path | Roles | Request | Response DTO | Notes |
|---|---|---|---|---|---|
| `GET` | `/api/v1/health` | Public | — | `{ status, service, aiProviderMode, timestamp }` | Returns `"UP"`, `"nifty-backend"`, active evaluator mode (`kiconnect-nrw` or `placeholder`), ISO-8601 timestamp (`HealthController`) |
| `GET` | `/actuator/health` | Public | — | Spring Boot Actuator health JSON | Used by Docker Compose healthcheck |

## 5. Planned MVP Module Endpoints (Update as Implemented)

- **Interviewer Studio (`/api/v1/ranking/studio/**`):** Versioned interviewer config CRUD, skill/tag taxonomy, human config approval (`openspec/specs/interviewer-studio/spec.md`).
- **Assessment Chat (`/api/v1/assessment/sessions/**`):** Invite-link session start, SSE streaming turn exchange, `assessment-transcript/v1` persistence (`openspec/specs/assessment-chat/spec.md`).
- **Evidence Pipeline (`/api/v1/ranking/transcripts/**`):** Trigger extraction, deterministic verbatim quote verification, inferred-tag review (`openspec/specs/evidence-pipeline/spec.md`).
- **Formula Engine (`/api/v1/ranking/formulas/**`):** Presets, sandboxed SpEL evaluation, deterministic what-if preview (`openspec/specs/formula-engine/spec.md`).
- **Skills Matrix & Review (`/api/v1/ranking/matrix/**`, `/api/v1/ranking/reviews/**`):** Matrix cells (`status: "SCORED" | "N/A"`, `value: number | null`), Evidence Drawer, flagged approval queue, audited overrides (`openspec/specs/skills-matrix-review/spec.md`).
- **Team Builder (`/api/v1/ranking/teams/**`):** Timefold Mode A/B/C team proposals, deterministic validation, human sign-off (`openspec/specs/team-builder/spec.md`).
- **Candidate Results (`/api/v1/assessment/results/**`):** Post-approval verified evidence, formula/weight breakdown, mocked dispute submission (`openspec/specs/candidate-results/spec.md`).
