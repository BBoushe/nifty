# Design: Evaluation Service (`evaluation`)

> **Canonical Contract Reference:** `docs/architecture.md`, `docs/api.md`, `docs/tickets/00-parallel-waves-and-contracts.md`, `docs/tickets/04-evaluation.md`.

## Package Layout (`de.fhdortmund.nifty.evaluation`)
- `web/`:
  - `RankingEvaluationController` (`/api/v1/ranking/configs/{configId}/matrix`, `/api/v1/ranking/attempts/{attemptId}/evaluations`, `/api/v1/ranking/evaluations/approve`, `/api/v1/ranking/evaluations/{responseEvalId}/override`, `/api/v1/ranking/attempts/{attemptId}/release`, `/api/v1/ranking/configs/{configId}/cohort-gaps`, `/api/v1/ranking/transcripts/{attemptId}` — `PROFESSOR` only).
  - `CandidateResultsController` (`/api/v1/assessment/results/{configId}` — `CANDIDATE` only, scoped strictly to invite's candidate).
  - Record DTOs only (zero `@Entity` exposure).
- `service/`:
  - `EvaluationEventListener` (`@TransactionalEventListener(phase = AFTER_COMMIT)` on `AttemptCompletedEvent`)
  - `AsyncEvaluationRunner` (`@Async` bounded executor, idempotent `evaluation_run`, failure isolation)
  - `TwoRunResponseJudge` (Spring AI 2.0 `ChatClient`, 2 fresh-context calls per `(entryId, skillId)` via `ContextCreationService.buildJudgeContext`)
  - `VerbatimQuoteVerifier`, `ConfidenceCalculator`, `SkillFormulaCalculator`, `ReleaseGateEvaluator`, `DeterministicAdviceBuilder` (**pure Java, ZERO Java Streams in hot loops!**)
  - `EvaluationReviewService` (drilldown, bulk-approve skipping `flagged`, override writing `audit_log` strictly via `AuditService` + Formula B recalculation, explicit attempt release)
  - `CohortGapQueryService` (SQL-only over released latest attempts)
  - `CandidateResultsService` (gates pre-release access, serves scores + quotes + full ledger + deterministic advice + own BFI-10 traits from `AssessmentQueryService`)
  - `EvaluationQueryService` (public interface for `teams`), `EvaluationCleanupListener` (`AssessmentDeletedEvent`).
- `repository/`: Private `UUID`-keyed `@Entity` and `JpaRepository` classes (`FormulaVersionEntity`, `EvaluationRunEntity`, `CandidateEvaluationSummaryEntity`, `ResponseEvaluationEntity`, `SkillScoreEntity`).

## Flyway Migration (`V7__evaluation.sql`)
- Every PK and FK is `UUID` (Decision 1.19).
- `candidate_evaluation_summary` has `UNIQUE (attempt_id)` so retakes (`attempt_no = 1, 2, ...`) never collide; queries select each `invite_id`'s latest completed `attempt_id` (Decision 2.13).
- `response_evaluation` stores `entry_id`, `core_question_id`, `skill_id`, `question_origin`, `question_weight`, `run1_level`, `run2_level`, `run1_quote`, `run2_quote`, `quote_verified`, `verified_quote`, `ai_rationale`, `ai_level`, `calculated_confidence`, `flag_reasons` (`TEXT`), `review_status` (`pending | flagged | auto_released | approved | overridden`), `effective_level`, `overridden_by`, `override_reason`, `reviewed_at`.
- `skill_score` has `CHECK ((status = 'N/A' AND score IS NULL) OR (status = 'SCORED' AND score BETWEEN 0.00 AND 4.00))`.

## Key Converged Formulas (Decisions 2.10, 2.11, 2.12, 2.20, 2.22, 2.24)
- **Skip blank entries only:** if `entry.answerText` is null/blank, skip Agent 2. Non-blank `timedOut = true` entries ARE evaluated.
- **Flagging rule:** `reviewStatus = flagged` if `!quoteVerified` (on substantive text `>= 8` chars) OR `|run1 - run2| >= 1` OR `calculatedConfidence < coreQuestion.confidenceThreshold` OR `entry.pasteSuspected` OR `entry.injectionSuspected`. (`N/A` does not fail confidence threshold.)
- **Formula B:** `w_eff = coreQuestion.weight` (`1.0` inheritance for follow-ups; no `SKILL.weight`); if `>= 2` scored entries for a skill, cap any single entry's weight share at `0.50` (`D2`). `fit = mean(SCORED skill scores)`. `coverage = sum(weight of answered scored core questions) / sum(weight of all core questions)`.
- **Release Gate:** if `flaggedCount == 0` (a score below its question threshold is flagged) AND `coverage >= minCoverage` → `releaseStatus = auto_released`; else waits for professor resolution + release.
