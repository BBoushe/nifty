# Feature Backlog

Planned features in delivery waves (Decisions 1.18, 3.14). Use this table to pick the next change and run teammates in parallel.

- **Status:** `queued` (ready when dependencies merge), `in progress` (an OpenSpec change exists under `openspec/changes/<change>/`), `blocked` (waiting on an open decision). No feature is blocked.
- **Wave:** features in the same wave can run in parallel. Teammates in later waves can also start immediately against mocked public `@Service` contracts in [`docs/tickets/00-parallel-waves-and-contracts.md`](tickets/00-parallel-waves-and-contracts.md).
- **Slice:** backend package and frontend folder from the [Feature Slice Map](architecture.md#feature-slice-map). Each feature edits only its own slice.
- **Migration:** Flyway versions are reserved here so parallel worktrees never collide. `V5` stays spare so `V6__assessment.sql` stays stable.
- **Stories:** each `US-` story is one RED → GREEN → REFACTOR loop and one commit.

Lifecycle (see `AGENTS.md` §3): when you propose a change, set its row to `in progress` and link the change folder. When you archive it, set its row to `done` (other rows refer to it by number) and add an entry to [`feature_history.md`](feature_history.md).

## MVP Features (7-Slice Converged Plan)

| # | Feature (spec / change) | Wave | Status | Owner / Slice | Depends on | Migration |
|---|---|---|---|---|---|---|
| 1 | `backend-foundation` | 0 | done | Shared (`shared`, `backend/pom.xml`, CI) | — | — |
| 2 | `frontend-foundation` | 0 | done | Shared (`frontend/` shell, CI) | — | — |
| 3 | `auth-accounts` ([change](../openspec/changes/archive/2026-11-26-auth-accounts/), [ticket](tickets/01-studio.md)) | 1 | done | Teammate R (`auth`, `views/auth/`) | 1, 2 | `V2__auth_accounts.sql` |
| 4 | `audit-log-service` ([change](../openspec/changes/archive/2026-11-25-audit-log-service/), [ticket](tickets/01-studio.md)) | 1 | done | Teammate R (`audit`) | 1 | — (uses `V1`) |
| 5 | `context-creation` ([spec](../openspec/specs/context-creation/spec.md), [ticket](tickets/02-context-creation.md)) | 1 | done | Teammate A (`context`) | 1, 4 (mockable) | — (stateless) |
| 6 | `studio-assessment-creation` ([change](../openspec/changes/archive/2026-11-26-studio-assessment-creation/), [ticket](tickets/01-studio.md)) | 2 | done | Teammate R (`studio`, `views/studio/`) | 1, 2, 3, 4 | `V3__studio.sql` |
| 7 | `invite-links` ([change](../openspec/changes/archive/2026-11-26-invite-links/), [ticket](tickets/01-studio.md)) | 3 | done | Teammate R (`auth`, `views/auth/` landing, `views/studio/` invite panel) | 3, 6 | `V4__auth_invites.sql` |
| 8 | `assessment-chat` ([change](../openspec/changes/archive/2026-11-26-assessment-chat/), [ticket](tickets/03-assessment-chat.md)) | 4 | done | Teammate S (`assessment`, `views/assessment/` chat) | 5, 6, 7 (mockable) | `V6__assessment.sql` |
| 9 | `evaluation-service` ([change](../openspec/changes/evaluation-service/), [ticket](tickets/04-evaluation.md)) | 4 | in progress | Teammate M (`evaluation`, `views/ranking/`, `views/assessment/` results) | 4, 5, 6, 8 (mockable) | `V7__evaluation.sql` |
| 10 | `team-builder` ([change](../openspec/changes/team-builder/), [ticket](tickets/05-teams.md)) | 5 | in progress | Teammate C (`teams`, `views/teams/`) | 4, 6, 8, 9 (mockable) | `V8__teams.sql` |
| 11 | `harness-review-followups` | any | queued | Shared (harness, CI, `shared`) | — | — |

## Stories (Foundation & Shared Glue)

> Stories for the 5 teammate services (#5, #6, #8, #9, #10) live in their `openspec/changes/<change>/tasks.md` and `docs/tickets/01..05-*.md` files.

### 1. `backend-foundation` (Wave 0)
Check each dependency change against `docs/ai_docs/` first.
- **US-BF-01:** Replace `spring-boot-starter-web` with `spring-boot-starter-webmvc`. Move `spring.ai.openai.chat.options.model` to `spring.ai.openai.chat.model`. All existing tests pass and app boots with no LLM key.
- **US-BF-02:** Add `spring-boot-starter-data-jpa-test`, Testcontainers PostgreSQL and the abstract base class `de.fhdortmund.nifty.PostgresContainerBase`. Move every `@SpringBootTest` (`HealthEndpointTest`, `RoleAccessTest`) onto it and drop H2 from the tests, because H2 cannot run JSONB or plpgsql triggers. One `@DataJpaTest` runs Flyway `V1` on PostgreSQL.
- **US-BF-03:** Add a `docker:dind` service to the GitLab `backend` job so Testcontainers runs on both forges. CI parity drift test passes.
- **US-BF-04:** Add generic `FieldValidationException(List<FieldErrorDetail>)` in `shared/exception/` and map it in `GlobalExceptionHandler` to `400` RFC 9457 `ProblemDetail` (`errors: [{ field, message, code }]`) so slices report multi-field validation errors without breaking ArchUnit `sharedIsFeatureFree` (Decisions 1.18, 1.19).
- **US-BF-05:** Add shared cross-slice event records (`AssessmentDeletedEvent(UUID configId, String actorId)` and `AttemptCompletedEvent(UUID attemptId, UUID configId, UUID inviteId)`) in `shared/config/` (the only package REQ-BA-01 allows for them) without referencing any feature class (Decision 1.19).
- **US-BF-06:** Add every cross-slice interface, record and enum of [`docs/tickets/00-parallel-waves-and-contracts.md`](tickets/00-parallel-waves-and-contracts.md) §3 as Java stubs in the owner packages (`audit.service`, `auth.service`, `studio.service`, `context.service`, `assessment.service`, `evaluation.service`). Enums carry `@JsonValue` literals. Add one placeholder `@Service` per interface that throws `UnsupportedOperationException`, so `@SpringBootTest` contexts start. Change the `RoleAccessTest` probes to unmapped paths (`/api/v1/ranking/__probe`, `/api/v1/assessment/__probe`), so later UUID routes cannot turn the expected `404` into `400`. `./mvnw -B test` passes, including `ArchitectureTest`. Teammates start only after this story merges.

### 2. `frontend-foundation` (Wave 0)
- **US-FF-01:** Add Vitest, `@vue/test-utils` and `jsdom`, an ECharts mock for component tests, an `npm run test` script, and the test step in both CI files.
- **US-FF-02:** Install PrimeVue. Define the design tokens as Tailwind `@theme` variables and map them into one PrimeVue preset. Component test shows `ScoreBadge` for levels `0..4` and hatched `N/A`.
- **US-FF-03:** Move `RankingView.vue` and `AssessmentView.vue` into `views/ranking/` and `views/assessment/`. Give each feature folder a `routes.ts` (`auth`, `studio`, `assessment`, `ranking`, `teams`) that `router/index.ts` spreads in, so each ticket edits only its own routes file. Routes stay the same.
- **US-FF-04:** Add a shared API client. It parses RFC 9457 `ProblemDetail` errors (including `errors[]`) and reads an SSE stream from a `POST` response (`fetch` + `ReadableStream`).

### 3. `auth-accounts` (Wave 1)
- **US-AU-01:** Add `professor_account` (`V2__auth_accounts.sql`) with hashed password (`DelegatingPasswordEncoder`) and `POST /api/v1/auth/login`. Unknown email and wrong password give the same `401` (REQ-AA-02). Expose current authenticated professor ID via `AuthContextService`.
- **US-AU-02:** Add the sign-in page in `views/auth/` and router guards for `/ranking/*` and `/assessment/*`.

### 4. `audit-log-service` (Wave 1)
- **US-AL-01:** Add `AuditLogEntity` over `V1` and public `AuditService.record(actorId, actorRole, entityType, entityId, action, oldValue, newValue, reason)`. ArchUnit confirms no class outside `audit` uses `AuditLogRepository`.
- **US-AL-02:** Expose no update or delete methods. Test verifies existing rows cannot be modified.

### 7. `invite-links` (Wave 3)
- **US-IL-01:** Add `invite` table (`V4__auth_invites.sql`). Create single invite with >=128-bit random token stored only as a hash (`REQ-AA-03`), verifying config exists via `StudioQueryService.exists(configId)`.
- **US-IL-02:** CSV import creates one invite per row and returns invite links (`POST /api/v1/ranking/studio/configs/{id}/invites`).
- **US-IL-03:** `POST /api/v1/auth/invite/{token}` starts an authenticated `CANDIDATE` session; revoked or unknown token gives `401` (`REQ-AA-04`).
- **US-IL-04:** Enforce candidate scope (`REQ-AA-05`) and listen to `AssessmentDeletedEvent` to purge invites for a deleted config (Decision 4.4).

## Post-MVP (Nice to Have)

- Backend draft/versioned assessment configs and "Duplicate as new" in `studio` (Decision 2.21).
- Inferred tags review workflow UI in `studio` (REQ-IS-02 removed for MVP, Decision 2.21).
- Team Builder Mode B (fresh-context LLM text-to-constraints) and Mode C (validated LLM team proposal).
- Case-study and project pool matching for teams (Decision 4.2).
- LLM-suggested core questions in Studio (Decision 4.2).
- Secondary `context` builders for UX-10 AI narrative phrasing (Decision 2.16) and Team Mode B.
- Sandboxed SpEL custom formula editor (Decision 2.19).
- Full dispute resolution workflow on the results page.
- Onboarding self-assessment, re-probe, and recruiter persona (P4).
- `REVIEWER` role for teaching assistants (Decision 1.15).

## Notes

- **Wave 0 touches CI:** rows 1 and 2 edit different jobs in `.github/workflows/ci.yml` and `.gitlab-ci.yml`. Merge one first, then run `git merge origin/dev` on the second.
- **Parallel mocking:** teammates on rows 5, 6, 8, 9, and 10 do not need to wait idle for upstream PRs; [`docs/tickets/00-parallel-waves-and-contracts.md`](tickets/00-parallel-waves-and-contracts.md) defines the exact Java interfaces and record DTOs to code and mock against.
- **Shared `views/assessment/`:** row 8 owns `views/assessment/chat/` (or `AssessmentChatView.vue` + BFI-10); row 9 owns `views/assessment/ResultsView.vue`. No file overlap.
- **`harness-review-followups`:** non-blocking findings from 2026-11-24 reviews (CI drift multi-line `run: |`, `ArchitectureTest` sub-rule fixtures, `GlobalExceptionHandler` `@ResponseStatus`, bash guard quoted redirection / `git commit -F .env`, minor `workflow.md`/`setup.md` and archived decision path cleanups).
