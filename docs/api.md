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
| `POST` | `/api/v1/ranking/studio/configs` | `PROFESSOR` | Header `Idempotency-Key: <uuid>`, `CreateAssessmentRequest` (`settings`, `skills`, `questions`) | `AssessmentDetailResponse` (`201` created / `200` idempotent replay) | One-shot immutable snapshot `v1` (`PUBLISHED`), `CONFIG_CREATED` audit row. `400` ProblemDetail with all `errors[]` on validation failure or cross-professor key reuse (REQ-IS-01, REQ-IS-06, REQ-IS-12). Grouping mode `SOFT_SKILLS` or `COMBINED` with BFI-10 off gives `settings.bfi10Enabled` / `BFI10_REQUIRED` (REQ-IS-13, Decision 2.27). |
| `GET` | `/api/v1/ranking/studio/configs` | `PROFESSOR` | — | `AssessmentSummaryResponse[]` | Lists the signed-in professor's own configurations, newest first (REQ-IS-10). |
| `GET` | `/api/v1/ranking/studio/configs/{configId}` | `PROFESSOR` | — | `AssessmentDetailResponse` | Full configuration including hidden 5-level rubrics. `404` for unknown or other professor's ID (REQ-IS-10). `PUT`/`PATCH` return `405` (REQ-IS-08). |
| `DELETE` | `/api/v1/ranking/studio/configs/{configId}` | `PROFESSOR` | — | `204` | GDPR purge: sets `SET LOCAL nifty.allow_purge = 'on'`, publishes `AssessmentDeletedEvent`, deletes rows, writes `CONFIG_DELETED` audit row. `404` for non-owner (REQ-IS-07, Decision 4.4). |
| `POST` | `/api/v1/auth/login` | Public (CSRF) | `{ email, password }` | `204`, session cookie | `401` ProblemDetail with the same detail for an unknown email and a wrong password (REQ-AA-02). Email is case-insensitive (`AuthController`). |
| `POST` | `/api/v1/auth/logout` | Public (CSRF) | — | `204` | Ends the session. `403` without the CSRF header (REQ-AA-06, `SecurityConfig`). |
| `GET` | `/api/v1/auth/me` | Authenticated | — | `{ role, professorId }` | `role` is `PROFESSOR` or `CANDIDATE`. `professorId` is `null` for a candidate. `401` without a session. |
| `POST` | `/api/v1/ranking/context/preview/interviewer` | `PROFESSOR` | `{ vision, targetSkillNames[], questionText, followUpLimit (0..3), inventedQuestionsEnabled? }` | `{ templateVersion, systemPrompt, promptSha256, rubricLeakCheckPassed }` | Dry run of the rubric-blind Agent 1 prompt for the opening question, with no conversation (`ContextController`, Decision 1.18). Stores nothing and calls no LLM. `rubricLeakCheckPassed` is `false` when the prompt holds a rubric, weight, threshold, score, confidence or BFI-10 term. It is a keyword check only: rubric prose without these words passes, and a skill named "Credit scoring" fails. `400` ProblemDetail with `errors[]` for a blank `vision`, `questionText` or skill name, an empty `targetSkillNames`, or a missing or out-of-range `followUpLimit`. `403` for `CANDIDATE`, `401` without a session. |
| `POST` | `/api/v1/assessment/sessions` | `CANDIDATE` | — | `text/event-stream` (new attempt) or `SessionStateResponse` JSON | Starts the next attempt of the invite and streams question 1 (§4.1). A `SOFT_SKILLS`-only configuration answers JSON with `status: "BFI10"` and calls no LLM (REQ-AC-01). When the invite has an unfinished attempt, the call returns it as JSON with the same `attemptId` (Decision 1.21). `409` when the candidate has no attempts left (REQ-AC-11). |
| `POST` | `/api/v1/assessment/sessions/{attemptId}/answers` | `CANDIDATE` | `{ content }` (0..4000 characters) | `text/event-stream` | Stores the answer, then streams the follow-up or next question, or `done` (§4.1). After `expiresAt` within the grace period (`nifty.assessment.grace-seconds`, default 5 s), the ledger keeps the text with `timedOut = true` (Decision 2.20). After the grace period, the call answers `409`. The ledger then records the question as timed out without text, and the next question opens. `409` when no question is open. `400` ProblemDetail with `errors[].field = content` for a missing or longer `content`. The ledger stays unchanged (REQ-AC-20, Decision 2.26). `403` for another candidate's attempt. |
| `GET` | `/api/v1/assessment/sessions/{attemptId}` | `CANDIDATE` | — | `SessionStateResponse` | Resume after a reload (REQ-AC-06): `attemptId`, `status` (`IN_PROGRESS` \| `BFI10` \| `COMPLETED`), `attemptNo`, `bfi10Enabled`, `questionsRemaining` (core questions after the current one), `expiresAt` (open question only, else `null`), `messages[]` (`seq`, `author`, `content`, `createdAt`). No scores or flags (REQ-AC-03). `403` for another candidate's attempt, `404` for an unknown ID. |
| `POST` | `/api/v1/assessment/sessions/{attemptId}/bfi10` | `CANDIDATE` | `{ items: [1..5 x 10] }` | `204` | Scores the 5 traits once in Java and completes the attempt (REQ-AC-15, Decision 4.3). `400` with one `errors[]` entry per missing or invalid item (`field: "items[6]"` for item 7). `409` when the attempt is not at `BFI10` or was already submitted. |

### 4.1 Interview SSE Events (`assessment`)

The start and answer endpoints send `text/event-stream` with `X-Accel-Buffering: no`. Each event has one `data:` line of JSON.

| Event | `data` | Meaning |
|---|---|---|
| `delta` | `{ text }` | One part of the interviewer reply. Append the parts in order. |
| `question` | `{ attemptId, entryId, origin, expiresAt, questionsRemaining }` | Last event: the reply is complete, and the question is open until `expiresAt`. `origin` is `core` or `follow_up`. After a streamed start, `attemptId` is the only source of the attempt ID. |
| `done` | `{ status }` | Last event: the interview ended. `status` is `BFI10` (show the form) or `COMPLETED`. |

If the interviewer LLM fails or sends no text, the stream sends the configured question text as one `delta`.

## 5. Planned MVP Module Endpoints (Update as Implemented)

- **Auth (`/api/v1/auth/**`, `/api/v1/ranking/studio/configs/{id}/invites`):**
  - Login, logout and `me` are implemented (§4).
  - `POST /api/v1/auth/invite/{token}` (Public) → candidate session from personal invite link (`401` on unknown/revoked token).
  - `POST /api/v1/ranking/studio/configs/{id}/invites` (`PROFESSOR`) → create single invite or CSV import returning invite links.
  - `DELETE /api/v1/ranking/studio/configs/{id}/invites/{inviteId}` (`PROFESSOR`) → revoke an invite link.
- **Studio (`studio`, `/api/v1/ranking/studio/configs/**`, `PROFESSOR` — Decisions 1.19, 2.21):**
  - Implemented (§4).
- **Assessment Chat (`assessment`, `/api/v1/assessment/sessions/**`, `CANDIDATE`):**
  - Implemented (§4). Candidate sessions start working when `invite-links` replaces the `403` of `AuthContextService.currentCandidate()`.
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
