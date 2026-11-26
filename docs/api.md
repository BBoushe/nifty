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
| `POST` | `/api/v1/auth/invite/{token}` | Public (CSRF) | — | `{ configId }`, session cookie | Starts a `CANDIDATE` session bound to the invite's assessment and candidate. Unknown and revoked tokens return the same `401` ProblemDetail (REQ-AA-04, `AuthController`). |
| `GET` | `/api/v1/auth/me` | Authenticated | — | `{ role, professorId, inviteId, configId }` | `role` is `PROFESSOR` or `CANDIDATE`. `professorId` is `null` for a candidate; `inviteId` and `configId` are `null` for a professor. `401` without a session or after invite revocation. |
| `POST` | `/api/v1/ranking/studio/configs/{configId}/invites` | `PROFESSOR` | `{ candidateRefs: string[] }` (1..500 unique non-blank) | `CreatedInvite[]` (`{ inviteId, candidateRef, link }`, `201`) | Generates 256-bit random tokens, stores only SHA-256 hashes, returns links once. `400` on duplicate/invalid refs, `404` for unowned or unknown config (REQ-AA-03, REQ-AA-09, `InviteController`). |
| `GET` | `/api/v1/ranking/studio/configs/{configId}/invites` | `PROFESSOR` | — | `InviteSummary[]` (`{ inviteId, candidateRef, revoked, createdAt }`) | Lists invites newest first without tokens. `404` for unowned or unknown config (REQ-AA-09). |
| `DELETE` | `/api/v1/ranking/studio/configs/{configId}/invites/{inviteId}` | `PROFESSOR` | — | `204` | Revokes the invite; open candidate sessions end with `401` on their next request (`RevokedInviteFilter`, REQ-AA-08). `404` for unowned config or unknown invite. |
| `POST` | `/api/v1/ranking/context/preview/interviewer` | `PROFESSOR` | `{ vision, targetSkillNames[], questionText, followUpLimit (0..3), inventedQuestionsEnabled? }` | `{ templateVersion, systemPrompt, promptSha256, rubricLeakCheckPassed }` | Dry run of the rubric-blind Agent 1 prompt for the opening question, with no conversation (`ContextController`, Decision 1.18). Stores nothing and calls no LLM. `rubricLeakCheckPassed` is `false` when the prompt holds a rubric, weight, threshold, score, confidence or BFI-10 term. It is a keyword check only: rubric prose without these words passes, and a skill named "Credit scoring" fails. `400` ProblemDetail with `errors[]` for a blank `vision`, `questionText` or skill name, an empty `targetSkillNames`, or a missing or out-of-range `followUpLimit`. `403` for `CANDIDATE`, `401` without a session. |
| `GET` | `/api/v1/ranking/configs/{configId}/matrix` | `PROFESSOR` | — | `MatrixResponse` (`skills[]`, `rows[]` with `status: "SCORED" \| "N/A"`, `value`, `fitScore`, `coverage`, `releaseStatus`, `flaggedCount`) | Latest completed attempt per invite for an owned assessment (`404` for unowned config, `403` for `CANDIDATE`, REQ-SM-07, Decision 2.13). |
| `GET` | `/api/v1/ranking/attempts/{attemptId}/evaluations` | `PROFESSOR` | — | `AttemptEvaluationDrilldownResponse` (`responses[]`, `skillScores[]`) | Evidence Drawer drilldown with both judge runs, verified quotes, deterministic confidence, flag reasons, and override details (`404` for unowned attempt, REQ-SM-01, REQ-SM-08). |
| `POST` | `/api/v1/ranking/evaluations/approve` | `PROFESSOR` | `{ configId, responseEvaluationIds? }` | `{ approvedCount, skippedFlaggedCount }` | Bulk-approves unflagged `pending` evaluations only; skips every `flagged` item (REQ-SM-03). |
| `POST` | `/api/v1/ranking/evaluations/{responseEvalId}/override` | `PROFESSOR` | `{ newLevel (0..4), reason }` | `OverrideEvaluationResponse` | Preserves `aiLevel`, stores `effectiveLevel` and `reason`, writes `SCORE_OVERRIDDEN` via `AuditService`, and recalculates Formula B in Java with zero LLM calls (`400` on blank reason or out-of-range level, `404` for unowned attempt, REQ-SM-02, REQ-FE-02). |
| `POST` | `/api/v1/ranking/attempts/{attemptId}/release` | `PROFESSOR` | — | `ReleaseAttemptResponse` | Releases a reviewed attempt once zero `flagged` evaluations remain (`409` if any flagged evaluation remains, `404` for unowned attempt, REQ-SM-06). |
| `GET` | `/api/v1/ranking/configs/{configId}/cohort-gaps` | `PROFESSOR` | — | `CohortGapsResponse` (`releasedCandidateCount`, `skills[]` with `meanScore`, `naShare`, `levelDistribution[0..4]`) | SQL-only aggregation over released latest completed attempts, ordered weakest first (`404` for unowned config, REQ-SM-04). |
| `GET` | `/api/v1/ranking/transcripts/{attemptId}` | `PROFESSOR` | — | `TranscriptExportResponse` (`format: "assessment-transcript/v1"`, `entries[]`, `evidence[]`) | Full ledger export with `timedOut`, `pasteSuspected`, `injectionSuspected` markers and verified quotes (`404` for unowned attempt, REQ-SM-08). |
| `GET` | `/api/v1/ranking/formula-presets` | `PROFESSOR` | — | `FormulaPresetInfo[]` | Lists named MVP formula presets (`WEIGHTED_MEAN`, REQ-FE-05, Decision 2.19). |
| `POST` | `/api/v1/ranking/configs/{configId}/formula` | `PROFESSOR` | `{ preset, expression? }` | `FormulaVersionResponse` | Persists a new immutable `formula_version` and recomputes latest completed attempts in Java with zero LLM calls. `400` ProblemDetail on unknown preset or non-null `expression` (REQ-FE-02, REQ-FE-05). |
| `POST` | `/api/v1/ranking/configs/{configId}/formula/preview` | `PROFESSOR` | `{ preset, expression? }` | `FormulaPreviewResponse` | What-if cohort recalculation without persisting any version or score (`400` on unknown preset or `expression`, REQ-FE-02). |
| `GET` | `/api/v1/assessment/results/{configId}` | `CANDIDATE` | — | `CandidateResultsResponse` | Scoped to signed-in invite (`403` for other config or `PROFESSOR`). Before release: `200` with `released: false`, `releaseStatus: "pending"`, and no score/weight/formula/trait fields. After release: scores (`N/A` explicit), verified quotes, question weights, formula version, full conversation log, deterministic advice, and own neutral BFI-10 traits (REQ-CR-01..06). |

## 5. Planned MVP Module Endpoints (Update as Implemented)

- **Auth (`/api/v1/auth/**`, `/api/v1/ranking/studio/configs/{id}/invites`):**
  - Implemented (§4).
- **Studio (`studio`, `/api/v1/ranking/studio/configs/**`, `PROFESSOR` — Decisions 1.19, 2.21):**
  - Implemented (§4).
- **Assessment Chat (`assessment`, `/api/v1/assessment/sessions/**`, `CANDIDATE` — Decisions 1.11, 2.20, 2.23, 2.24):**
  - `POST /api/v1/assessment/sessions` → `text/event-stream` greeting + question 1 with `expiresAt` (or JSON `BFI10` start for `SOFT_SKILLS`-only). `409` if `max_attempts` is exceeded.
  - `POST /api/v1/assessment/sessions/{id}/answers` (`{ content }`) → `text/event-stream` reply + next question with `expiresAt` or `done`. `409` after `expiresAt + grace` or invalid state. `400` ProblemDetail (`errors[].field = content`) when `content` is longer than 4000 characters; nothing is stored (REQ-AC-20, Decision 2.26).
  - `GET /api/v1/assessment/sessions/{id}` → session state from ledger (`messages`, `status`, `questionsRemaining`, `expiresAt`, `bfi10Enabled`).
  - `POST /api/v1/assessment/sessions/{id}/bfi10` (`{ items: [1..5 x 10] }`) → `204` (`400` naming each missing/invalid item, `409` if wrong state or already submitted).
- **Evaluation, Matrix, Review, Cohort Gaps & Candidate Results (`evaluation` — Decisions 1.18, 1.19, 2.10, 2.22):**
  - Implemented (§4).
- **Team Builder (`teams`, `/api/v1/ranking/configs/{configId}/teams/**` and `/api/v1/ranking/teams/**`, `PROFESSOR` — Decisions 1.20, 2.23):**
  - `POST /api/v1/ranking/configs/{configId}/teams/generate` → run seeded Simulated Annealing optimizer (`numTeams?`, `groupingMode?`, `alpha?`, `seed?`) and save `DRAFT` proposal with balance & diversity diagnostics.
  - `POST /api/v1/ranking/teams/{proposalId}/recalculate` → deterministic live drag-and-drop balance/diversity recalculation (zero LLM calls).
  - `POST /api/v1/ranking/teams/{proposalId}/publish` → professor approval moving `DRAFT -> PUBLISHED` (`audit_log` entry).
  - `GET /api/v1/ranking/configs/{configId}/teams` → list proposals with member skill averages and BFI-10 trait scores (`PROFESSOR` only).
