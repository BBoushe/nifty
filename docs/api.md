# Living API Documentation (`docs/api.md`)

> **Maintenance Rule (Decision 2.9 & `.agents/rules/coding-standards.md` #4):** Every PR that adds, changes, or removes a backend controller endpoint, DTO, auth rule, or SSE event MUST update this file in the same commit/PR. Live OpenAPI stays available at `/v3/api-docs` and `/swagger-ui.html`.

## 1. Base URLs & Live OpenAPI

| Environment | Base URL | OpenAPI JSON | Swagger UI |
|---|---|---|---|
| **Local Backend** | `http://localhost:8080` | `http://localhost:8080/v3/api-docs` | `http://localhost:8080/swagger-ui.html` |
| **Docker / Nginx Proxy** | `http://localhost:3000/api` | `http://localhost:8080/v3/api-docs` *(Nginx does not proxy `/v3/api-docs`)* | `http://localhost:8080/swagger-ui.html` |

## 2. Authentication & Role Boundaries (`SecurityConfig`)

| Path Prefix | Allowed Roles | Notes |
|---|---|---|
| `/api/v1/health`, `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Public (`permitAll`) | Liveness, readiness, and OpenAPI schema |
| `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `POST /api/v1/auth/invite/**` | Public | Professor sign-in, logout, invite-link sign-in (Decisions 1.17, 1.23). CSRF token required. |
| `GET /api/v1/auth/me` | Authenticated | Role of the current session |
| `/api/v1/ranking/**` | `PROFESSOR` | Ranking View: Studio configs, invites, prompt preview, transcripts, Matrix, review queue, Cohort Gaps, Team Builder (Decision 1.19) |
| `/api/v1/assessment/**` | `CANDIDATE` | Assessment Chat, BFI-10, and post-release Candidate Results. Candidate sessions reach only the assessment and candidate of their invite link. |
| All other routes | Authenticated | Default deny for unauthenticated callers |

MVP has two roles (Decision 1.15). `SecurityConfig` holds the path rules, and method-level `@PreAuthorize` holds finer rules.

**Sessions and CSRF (Decision 1.23):**

- Sign-in creates a server session. The `JSESSIONID` cookie is `HttpOnly` and `SameSite=Lax`. It is `Secure` unless `NIFTY_SESSION_COOKIE_SECURE=false`.
- Every response can set the `XSRF-TOKEN` cookie. Send its value in the `X-XSRF-TOKEN` header on every `POST`, `PUT`, `PATCH` and `DELETE`. Without it, the request gets `403`.
- `frontend/src/api/client.ts` (`apiFetch`, `postSse`) sends the header.
- A request without a session to a protected route gets `401` as a ProblemDetail (§3). There is no HTTP Basic challenge.

## 3. Standard Error Envelope

Every API error is an RFC 9457 `ProblemDetail` with media type `application/problem+json`. `shared/exception/GlobalExceptionHandler` (`@RestControllerAdvice`) produces it. Responses never contain stack traces or exception class names.

| Field | Meaning |
|---|---|
| `type` | Problem type URI (`about:blank` unless a specific type exists) |
| `title` | Short summary (defaults to HTTP reason phrase) |
| `status` | HTTP status code |
| `detail` | Human-readable explanation, safe for clients |
| `instance` | Request path that failed |
| `errors` | Only on `400` validation failures (`@Valid` or `FieldValidationException` in `shared/exception`, Decision 1.18/1.19): list of `{ "field", "message", "code"? }` |

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/v1/ranking/studio/configs",
  "errors": [{ "field": "questions[0].maxTimeSec", "message": "must be greater than minTimeSec", "code": "TIME_RANGE" }]
}
```

| Source | Status | `detail` |
|---|---|---|
| `@Valid` body violation or `FieldValidationException` | `400` | Validation summary plus `errors[]` |
| `DomainException(status, detail)` | its `status` | its `detail` |
| Spring MVC errors (`404`, `405`, `415`, ...) | status mapped by `ResponseEntityExceptionHandler` | Spring default |
| Any other exception | `500` | `An unexpected error occurred.` (server logs full error) |

## 4. Implemented Endpoints

| Method | Path | Roles | Request | Response DTO | Notes |
|---|---|---|---|---|---|
| `GET` | `/api/v1/health` | Public | — | `{ status, service, aiProviderMode, timestamp }` | Returns `"UP"`, `"nifty-backend"`, active provider mode, ISO-8601 timestamp (`HealthController`) |
| `GET` | `/actuator/health` | Public | — | Spring Boot Actuator health JSON | Docker Compose healthcheck target |
| `POST` | `/api/v1/auth/login` | Public (CSRF) | `{ email, password }` | `204`, session cookie | `401` ProblemDetail with the same detail for an unknown email and a wrong password (REQ-AA-02). Email is case-insensitive (`AuthController`). |
| `POST` | `/api/v1/auth/logout` | Public (CSRF) | — | `204` | Ends the session. `403` without the CSRF header (REQ-AA-06, `SecurityConfig`). |
| `GET` | `/api/v1/auth/me` | Authenticated | — | `{ role, professorId }` | `role` is `PROFESSOR` or `CANDIDATE`. `professorId` is `null` for a candidate. `401` without a session. |

## 5. Planned MVP Module Endpoints (Update as Implemented)

- **Auth (`/api/v1/auth/**`, `/api/v1/ranking/studio/configs/{id}/invites`):**
  - Login, logout and `me` are implemented (§4).
  - `POST /api/v1/auth/invite/{token}` (Public) → candidate session from personal invite link (`401` on unknown/revoked token).
  - `POST /api/v1/ranking/studio/configs/{id}/invites` (`PROFESSOR`) → create single invite or CSV import returning invite links.
  - `DELETE /api/v1/ranking/studio/configs/{id}/invites/{inviteId}` (`PROFESSOR`) → revoke an invite link.
- **Studio (`studio`, `/api/v1/ranking/studio/configs/**`, `PROFESSOR` — Decisions 1.19, 2.21):**
  - `POST /api/v1/ranking/studio/configs` (Header `Idempotency-Key: <uuid>`) → one-shot immutable config creation (`201` created / `200` idempotent replay / `400` ProblemDetail with all `errors[]`).
  - `GET /api/v1/ranking/studio/configs` → list professor's own configs newest first (`AssessmentSummary[]`).
  - `GET /api/v1/ranking/studio/configs/{configId}` → full config detail including hidden rubrics (`404` for unknown or other professor's config).
  - `DELETE /api/v1/ranking/studio/configs/{configId}` → GDPR purge of all assessment and candidate data (`204`, emits `AssessmentDeletedEvent`, Decision 4.4).
  - `PUT` / `PATCH` on `/api/v1/ranking/studio/configs/{configId}` → `405 Method Not Allowed`.
- **Context Creation (`context`, `/api/v1/ranking/context/**`, `PROFESSOR` — Decision 1.18):**
  - `POST /api/v1/ranking/context/preview/interviewer` → dry-run preview of the rubric-blind Agent 1 prompt (`{ templateVersion, systemPrompt, promptSha256, rubricLeakCheckPassed }`).
- **Assessment Chat (`assessment`, `/api/v1/assessment/sessions/**`, `CANDIDATE` — Decisions 1.11, 2.20, 2.23, 2.24):**
  - `POST /api/v1/assessment/sessions` → `text/event-stream` greeting + question 1 with `expiresAt` (or JSON `BFI10` start for `SOFT_SKILLS`-only). `409` if `max_attempts` is exceeded.
  - `POST /api/v1/assessment/sessions/{id}/answers` (`{ content }`) → `text/event-stream` reply + next question with `expiresAt` or `done`. `409` after `expiresAt + grace` or invalid state.
  - `GET /api/v1/assessment/sessions/{id}` → session state from ledger (`messages`, `status`, `questionsRemaining`, `expiresAt`, `bfi10Enabled`).
  - `POST /api/v1/assessment/sessions/{id}/bfi10` (`{ items: [1..5 x 10] }`) → `204` (`400` naming each missing/invalid item, `409` if wrong state or already submitted).
- **Evaluation, Matrix, Review, Cohort Gaps & Candidate Results (`evaluation` — Decisions 1.18, 1.19, 2.10, 2.22):**
  - `GET /api/v1/ranking/transcripts/{attemptId}` (`PROFESSOR`) → `assessment-transcript/v1` export (`*.transcript.json`) + `timedOut`/`pasteSuspected`/`injectionSuspected` markers.
  - `GET /api/v1/ranking/configs/{configId}/matrix` (`PROFESSOR`) → candidate skill matrix (`status: "SCORED" | "N/A"`, `value: number | null`, `fit`, `coverage`, `releaseStatus`, `flaggedCount`) over each candidate's latest counted attempt.
  - `GET /api/v1/ranking/attempts/{attemptId}/evaluations` (`PROFESSOR`) → Evidence Drawer drilldown (`response_evaluation` rows, verified quotes, judge runs, confidence, flags, override history).
  - `POST /api/v1/ranking/evaluations/approve` (`PROFESSOR`) → bulk-approve unflagged responses (`flagged` items skipped).
  - `POST /api/v1/ranking/evaluations/{responseEvalId}/override` (`PROFESSOR`) → `{ newLevel, reason }` writes `audit_log` via `AuditService`, updates `effectiveLevel`, recalculates Formula B deterministically.
  - `POST /api/v1/ranking/attempts/{attemptId}/release` (`PROFESSOR`) → approve and release reviewed attempt after all flagged items are resolved.
  - `GET /api/v1/ranking/formula-presets` (`PROFESSOR`) → named formula presets (MVP: `WEIGHTED_MEAN`, REQ-FE-05).
  - `POST /api/v1/ranking/configs/{configId}/formula` (`PROFESSOR`, `{ preset }`) → saves a new immutable `formula_version` and recomputes scores, `fit` and `coverage` in Java with zero LLM calls (REQ-FE-02). `400` ProblemDetail on an unknown preset or a free-form expression field (Decision 2.19).
  - `POST /api/v1/ranking/configs/{configId}/formula/preview` (`PROFESSOR`, `{ preset }`) → what-if scores, `fit` and `coverage` for the cohort, stores nothing.
  - `GET /api/v1/ranking/configs/{configId}/cohort-gaps` (`PROFESSOR`) → SQL-only skill level distribution, `N/A` shares, and weakest domains over released counted attempts.
  - `GET /api/v1/assessment/results/{configId}` (`CANDIDATE`) → candidate's own released scores (`N/A` explicit), verified quotes, question weights, formula version, full conversation log, deterministic strengths/improvements advice, and own BFI-10 traits (`200` with `pending` status and no score fields before release; `403` for an assessment outside the candidate's invite).
- **Team Builder (`teams`, `/api/v1/ranking/configs/{configId}/teams/**` and `/api/v1/ranking/teams/**`, `PROFESSOR` — Decisions 1.20, 2.23):**
  - `POST /api/v1/ranking/configs/{configId}/teams/generate` → run seeded Simulated Annealing optimizer (`numTeams?`, `groupingMode?`, `alpha?`, `seed?`) and save `DRAFT` proposal with balance & diversity diagnostics.
  - `POST /api/v1/ranking/teams/{proposalId}/recalculate` → deterministic live drag-and-drop balance/diversity recalculation (zero LLM calls).
  - `POST /api/v1/ranking/teams/{proposalId}/publish` → professor approval moving `DRAFT -> PUBLISHED` (`audit_log` entry).
  - `GET /api/v1/ranking/configs/{configId}/teams` → list proposals with member skill averages and BFI-10 trait scores (`PROFESSOR` only).
