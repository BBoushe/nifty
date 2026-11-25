# Living API Documentation (`docs/api.md`)

> **Maintenance Rule (Decision 2.9 & `.agents/rules/coding-standards.md` #4):** It applies to every PR that adds, modifies, or removes a backend controller endpoint, DTO, auth rule, or SSE event. That PR **MUST** update this file in the same commit/PR. Live machine-readable OpenAPI remains available at `/v3/api-docs` and `/swagger-ui.html`.

## 1. Base URLs & Live OpenAPI

| Environment | Base URL | OpenAPI JSON | Swagger UI |
|---|---|---|---|
| **Local Backend** | `http://localhost:8080` | `http://localhost:8080/v3/api-docs` | `http://localhost:8080/swagger-ui.html` |
| **Docker / Nginx Proxy** | `http://localhost:3000/api` | `http://localhost:8080/v3/api-docs` *(Nginx does not proxy `/v3/api-docs`)* | `http://localhost:8080/swagger-ui.html` |

## 2. Authentication & Role Boundaries (`SecurityConfig`)

| Path Prefix | Allowed Roles | Notes |
|---|---|---|
| `/api/v1/health`, `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Public (`permitAll`) | Liveness, readiness, and OpenAPI schema |
| `/api/v1/auth/**` | Public | Professor sign-in, invite-link sign-in (Decision 1.17) |
| `/api/v1/ranking/**` | `PROFESSOR` | Ranking View: Studio, invites, Matrix, review queue, conversation logs, Team Builder |
| `/api/v1/assessment/**` | `CANDIDATE` | Assessment Chat and Candidate Results. A candidate session reaches only the assessment and candidate of its invite link. Results stay closed until release. |
| All other routes | Authenticated | Default deny for unauthenticated callers |

MVP has two roles (Decision 1.15). `SecurityConfig` holds the path rules, and method-level `@PreAuthorize` holds finer rules. A new role (for example `REVIEWER`) is one `Role` value plus rules in these two places.

## 3. Standard Error Envelope

Every API error is an RFC 9457 `ProblemDetail` with media type `application/problem+json`. `shared/exception/GlobalExceptionHandler` (`@RestControllerAdvice`) produces it. Rules are in `.agents/rules/backend-java.md` §9. Responses never contain stack traces or exception class names.

| Field | Meaning |
|---|---|
| `type` | Problem type URI. The value is `about:blank` unless a specific type exists. |
| `title` | Short summary. It defaults to the HTTP reason phrase (e.g. `Bad Request`). |
| `status` | HTTP status code |
| `detail` | Human-readable explanation, safe for clients |
| `instance` | Request path that failed |
| `errors` | Only on `400` for an invalid `@Valid` request body: list of `{ "field", "message" }` |

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
| `DomainException(status, detail)` that a service throws | its `status` | its `detail` |
| Spring MVC errors (404, 405, 415, ...) | the status that `ResponseEntityExceptionHandler` maps | Spring's default |
| Any other exception | `500` | `An unexpected error occurred.` (the server logs the error) |

## 4. Implemented Endpoints

| Method | Path | Roles | Request | Response DTO | Notes |
|---|---|---|---|---|---|
| `GET` | `/api/v1/health` | Public | — | `{ status, service, aiProviderMode, timestamp }` | Returns `"UP"`, `"nifty-backend"`, active evaluator mode (`kiconnect-nrw` or `placeholder`), ISO-8601 timestamp (`HealthController`) |
| `GET` | `/actuator/health` | Public | — | Spring Boot Actuator health JSON | The Docker Compose healthcheck uses it |

## 5. Planned MVP Module Endpoints (Update as Implemented)

- **Auth (`/api/v1/auth/**`):** `POST /auth/login` (professor), `POST /auth/invite/{token}` (candidate session from an invite link) (`openspec/specs/auth-access/spec.md`).
- **Interviewer Studio (`/api/v1/ranking/studio/**`):** assessment form CRUD, config versions, publish, skill/tag taxonomy and inferred-tag review (the evidence pipeline proposes tags through the `studio` service), assessment deletion (`DELETE /ranking/studio/configs/{id}`), invite links (`POST /ranking/studio/configs/{id}/invites`, CSV import) (`openspec/specs/interviewer-studio/spec.md`).
- **Assessment Chat (`/api/v1/assessment/sessions/**`):** (Decision 1.11, `openspec/specs/assessment-chat/spec.md`)
  - `POST /assessment/sessions` → `text/event-stream`: greeting and question 1. Each question event carries `expiresAt`. `409` when the attempts are used up.
  - `POST /assessment/sessions/{id}/answers` (`{ text }`) → `text/event-stream`: the interviewer reply, then the next question with `expiresAt`, or `done`. `409` after `expiresAt` plus the grace period, or when the state does not allow an answer.
  - `GET /assessment/sessions/{id}` → session state from the ledger: messages, open question, `expiresAt`.
  - `POST /assessment/sessions/{id}/bfi10` (`{ items: [1..5 x 10] }`) → `204`. `400` names each missing item.
- **Evidence Pipeline (`/api/v1/ranking/transcripts/**`):** `assessment-transcript/v1` export, evidence per attempt (`openspec/specs/evidence-pipeline/spec.md`). The evaluator starts from an event, not from an endpoint.
- **Formula Engine (`/api/v1/ranking/formulas/**`):** preset list, formula version per config, what-if preview. No free-form expressions in MVP (`openspec/specs/formula-engine/spec.md`).
- **Skills Matrix & Review (`/api/v1/ranking/matrix/**`, `/api/v1/ranking/reviews/**`):** matrix cells (`status: "SCORED" | "N/A"`, `value: number | null`), Evidence Drawer, "Needs attention" queue, attempt approval, audited overrides, full conversation log per attempt (`openspec/specs/skills-matrix-review/spec.md`).
- **Team Builder (`/api/v1/ranking/teams/**`):** Timefold proposals with configured team size and BFI-10 balance, deterministic validation, professor publishing (`openspec/specs/team-builder/spec.md`).
- **Candidate Results (`/api/v1/assessment/results/**`):** released scores, verified evidence, formula and weights, deterministic advice, own conversation log, own BFI-10 traits (`openspec/specs/candidate-results/spec.md`).
