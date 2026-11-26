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

## Review Fixes (PR #29: no lost evaluations, retake rule, fail closed)
- **Queue before dispatch (Decision 2.24).** `EvaluationEventListener` calls `AsyncEvaluationRunner.enqueue` AFTER_COMMIT. `enqueue` writes one `evaluation_run` (`QUEUED`, `attempt_count = 0`) and one `candidate_evaluation_summary` (`QUEUED`, `release_status = pending`, no scores) in its own transaction. Then the listener submits `runAsync(runId)`. A rejected submission (full executor queue) only logs. The run stays `QUEUED`, so no completed attempt can vanish. `shortcut:` a crash between the assessment commit and `enqueue` still loses the event. Upgrade: do the insert in `BEFORE_COMMIT`, or reconcile against the assessment slice at start-up.
- **Duplicate events.** `enqueue` returns `null` when a summary for the `attempt_id` exists, whatever the formula version. `UNIQUE (attempt_id)` settles a concurrent race. A duplicate event never starts a second LLM run and never creates a `FAILED` run.
- **Atomic claim and bounded retry.** `EvaluationRunRepository.claim` is one conditional `UPDATE` to `RUNNING` that adds 1 to `attempt_count`. It only wins for `QUEUED`, `FAILED`, or a `RUNNING` run whose `updated_at` is older than `nifty.evaluation.retry.stale-after` (crashed worker), and only while `attempt_count < nifty.evaluation.retry.max-attempts` (3). So two workers never run one attempt. `EvaluationRetryScheduler` (`@Scheduled`, `nifty.evaluation.retry.fixed-delay`, off in the `test` profile) dispatches `retryableRunIds()`. A failure marks the run and the summary `FAILED` and keeps both retryable until the run uses all its tries. The final write skips when the summary is already `COMPLETED`, so a stale twin cannot write twice. `shortcut:` a `RUNNING` run that is stale at the last try is never reclaimed. Upgrade: a lease heartbeat plus a reaper that marks it `FAILED`. A run that waited while a new formula version appeared is re-pointed to the newest version at claim time (REQ-FE-02).
- **Waiting attempts and the retake rule (Decision 2.13, REQ-SM-07, REQ-EV-01).** A summary exists from enqueue on. `findLatestPerInvite` returns the newest attempt per invite in ANY status. The matrix shows a non-`COMPLETED` row (`evaluationStatus`, `fitScore = null`, `releaseStatus = pending`, `N/A` cells). `EvaluationQueryService.pendingInviteIds` lists it. `findLatestCompletedPerInvite` and the cohort SQL pick the newest attempt first. Then they keep it only when it is `COMPLETED` and released. So an older attempt never counts while the retake waits, runs, or fails. Candidate results already read the newest summary, so they withhold. `releaseAttempt` answers `409` for a non-`COMPLETED` attempt.
- **Invented entries (Decision 2.22).** `SkillFormulaCalculator` and the runner skip `EntryOrigin.INVENTED` for coverage. They still count it in the skill score at weight x 0.5.
- **Zero weights (Decision 2.22).** Studio rejects a weight of 0 or less, and the calculator does not trust that. A zero-weight entry carries no share. One positive entry is the whole score (the 0.50 cap needs two contributing entries). With no positive weight, the plain mean of the eligible levels applies. A SCORED value stays inside the min..max level of its eligible entries.
- **Overrides survive (Decision 2.10).** `approveEvaluation` answers `409` for a row with `overridden_by`. Recalculation decides override eligibility by `overridden_by != null`, not by the mutable review status.
- **Release gate fails closed (Decision 2.10).** A scored response with unknown confidence or threshold gets the flag `LOW_CONFIDENCE`. `evaluateAttemptRelease` returns `pending` (never `auto_released`) for an empty status list or a `null` coverage or minimum. `N/A` still never fails a threshold.
- **No DB connection across LLM calls (Decision 2.24).** `TwoRunResponseJudge.judge` is `Propagation.NOT_SUPPORTED`.
- **Snapshot weights (REQ-FE-02).** `FormulaRecalculationHelper.computeForResponses` takes the weights of the summary's `formula_version.question_weights_json`. Only the what-if preview uses the live studio weights.
- **Formula version race.** `applyPreset` uses `saveAndFlush` and maps the `UNIQUE (config_id, version_number)` violation to `409`.
- **Schema (V7, not yet merged).** `evaluation_run.attempt_count` defaults to `0` (`CHECK >= 0`). A new `evaluation_run.updated_at` is the heartbeat for stale `RUNNING` runs.
