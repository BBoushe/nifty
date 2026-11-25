# Ticket #4: `evaluation` — Evaluation, Formula B, Review Matrix & Candidate Results (Teammate M)

- **OpenSpec Change:** [`openspec/changes/evaluation-service/`](../../openspec/changes/evaluation-service/)
- **Branch / Worktree:** `feat/evaluation-service` (`../nifty-evaluation-service`)
- **Owned Folders:** `backend/src/main/java/de/fhdortmund/nifty/evaluation/`, `frontend/src/views/ranking/`, `frontend/src/views/assessment/ResultsView.vue`
- **Reserved Migration:** `V7__evaluation.sql`
- **Wave:** Wave 4 (start pure-JVM + mocked-upstream stories immediately in parallel!)

## Key Convergence Fixes Applied to Original Draft
1. **`UUID` IDs & `AttemptCompletedEvent` (Blocking):** Convert all `BIGSERIAL`/`VARCHAR` IDs in `V7` to `UUID` and listen to `AttemptCompletedEvent(attemptId, configId, inviteId)` (NOT `InterviewCompletedEvent`). Key `candidate_evaluation_summary` by `UNIQUE (attempt_id)` so retakes (`attempt_no = 1, 2, ...`) never collide (Decision 2.13).
2. **Nov 25 release & threshold rules (Blocking):** Replace two-bound (`0.60`/`0.85`) all-human-review with per-question `confidence_threshold` (`ScoringView`), threshold-gated **`auto_released`** (`reviewStatus: pending | flagged | auto_released | approved | overridden`, Decision 2.10), `pasteSuspected || injectionSuspected` → `flagged` (Decision 2.24), and `N/A` not failing a question's threshold.
3. **Question-weighted Formula B (Decision 2.22):** Remove `SKILL.weight` (`w_s`) and `0.70` follow-up multiplier; follow-ups inherit `1.0 * coreQuestion.weight`, keep `D2` `0.50` per-entry cap, compute `fit` as unweighted mean across `SCORED` skills, and compute `coverage` over weighted answered core questions (Decision 2.12).
4. **Timed-out partial answers & follow-up timeouts (Decision 2.20):** Skip only blank/null entries; evaluate non-blank `timedOut = true` entries normally via `ContextCreationService.buildJudgeContext(...)` using per-skill `List<String> rubricLevels` (`EvaluatorView`).
5. **Single unified slice + `AuditService` + GDPR:** Keep everything cleanly inside `de.fhdortmund.nifty.evaluation` (no separate `override` package touching `audit_log` directly — call `AuditService.record(...)`), add SQL Cohort Gaps, professor transcript export (`/api/v1/ranking/transcripts/{attemptId}`), candidate post-release results (`/api/v1/assessment/results/{configId}`), `EvaluationQueryService`, and `AssessmentDeletedEvent` cleanup.

## Exposes / Consumes
- **Exposes:** `EvaluationQueryService.getCandidateProfilesForConfig(configId)`, `PROFESSOR` `/api/v1/ranking/{configs,attempts,evaluations,transcripts}/**`, `CANDIDATE` `/api/v1/assessment/results/{configId}`.
- **Consumes:** `AttemptCompletedEvent`, `AssessmentDeletedEvent`, `StudioQueryService` (`evaluatorView`, `scoringView`), `ContextCreationService.buildJudgeContext`, `AssessmentQueryService` (`getTranscriptForAttempt`, `getSoftSkillProfileForAttempt`), `AuditService`, `AuthContextService`.

## Ready-to-Paste Agent Launch Prompt

```text
You are implementing the `evaluation` vertical slice (`evaluation-service`) for Nifty.
Setup (do this before anything else):
1. Prerequisite check: run `git fetch origin && git show origin/dev:backend/src/main/java/de/fhdortmund/nifty/audit/service/AuditService.java > /dev/null`. If it fails, Wave 0 (`backend-foundation` with the US-BF-06 contract stubs, and `frontend-foundation`) is not on `dev` yet. Stop and tell the human.
2. From the main checkout, run `git worktree add ../nifty-evaluation-service -b feat/evaluation-service origin/dev`. Work only in `../nifty-evaluation-service` and confirm it with `pwd` before each command. Ask the human to copy `.env` into the worktree. Never read `.env` yourself.
3. The shared interfaces, records and enums of `docs/tickets/00-parallel-waves-and-contracts.md` §3 already exist as stubs on `dev`. Implement only the ones your slice owns. Mock the others. Never create a second copy of a shared type.

Read these files first (do NOT scan the whole source tree):
1. `AGENTS.md`, `.agents/rules/coding-standards.md`, `.agents/rules/backend-java.md`, `.agents/rules/frontend-vue.md`
2. `docs/architecture.md`, `docs/api.md`, `docs/tickets/00-parallel-waves-and-contracts.md`
3. `openspec/specs/{evidence-pipeline,formula-engine,skills-matrix-review,candidate-results}/spec.md`
4. `openspec/changes/evaluation-service/{proposal.md,design.md,tasks.md,specs/evidence-pipeline/spec.md}`

Non-negotiable rules:
- Never merge. Ask the human before you push or open the PR/MR. Update `tasks.md` `## Status` before every stop, so another session can resume.
- Follow TDD strictly (RED failing test -> GREEN minimal code -> REFACTOR) for US-EV-01 through US-EV-06 in `tasks.md`.
- Keep all production backend code inside `de.fhdortmund.nifty.evaluation.{web,service,repository}` using `UUID` PKs/FKs in `V7__evaluation.sql`. Write `audit_log` ONLY via `AuditService.record(...)`.
- Never use Java Streams inside `VerbatimQuoteVerifier`, `ConfidenceCalculator`, `SkillFormulaCalculator`, or `ReleaseGateEvaluator`.
- Enforce Decisions 2.10 (`auto_released`), 2.11 (per-question threshold), 2.12 (question-weighted coverage), 2.13 (latest attempt counts, `UNIQUE(attempt_id)`), 2.20 (evaluate non-blank timed-out answers; skip blank entries), 2.22 (no `SKILL.weight`, `1.0` follow-up inheritance, `D2` `0.50` cap, unweighted `fit`), and 2.24 (`pasteSuspected || injectionSuspected` -> `flagged`).
- Call `ContextCreationService.buildJudgeContext` for each `(entryId, skillId)` 2-run fresh-context `ki:connect` NRW call (mock `ChatClient` and upstream `@Service` interfaces in tests).
- Add `EvaluationCleanupListener` on `AssessmentDeletedEvent`, implement `EvaluationQueryService` from `docs/tickets/00-parallel-waves-and-contracts.md`, sync `docs/api.md`, build Vue views in `views/ranking/` and `views/assessment/ResultsView.vue` (hatched `N/A`, zero client-side math), verify with `./mvnw -B test` + frontend checks, archive before pushing, and open PR/MR via `node scripts/forge.mjs`.
```
