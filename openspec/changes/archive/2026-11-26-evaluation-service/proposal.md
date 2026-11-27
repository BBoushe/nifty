# Proposal: Evaluation, Formula, Matrix Review & Candidate Results (`evaluation-service`)

## Summary

Implement the unified `evaluation` backend slice (`de.fhdortmund.nifty.evaluation`) plus its Ranking View UI (`frontend/src/views/ranking/`) and Candidate Results UI (`frontend/src/views/assessment/ResultsView.vue`) (Decisions 1.18, 1.19, 2.10–2.13, 2.15, 2.16, 2.20, 2.22, 2.24).

## Scope

- Flyway `V7__evaluation.sql` (`formula_version`, `evaluation_run`, `candidate_evaluation_summary` keyed by `attempt_id`, `response_evaluation`, `skill_score`) using `UUID` primary and foreign keys.
- Asynchronous `AFTER_COMMIT` listener on `AttemptCompletedEvent(attemptId, configId, inviteId)` with idempotent `evaluation_run`.
- Two-run fresh-context `ki:connect` NRW judge per `(entryId, skillId)` calling `ContextCreationService.buildJudgeContext(...)` (skipping blank entries, evaluating non-blank timed-out partial answers per Decision 2.20).
- Pure zero-Stream Java calculators: `VerbatimQuoteVerifier`, `ConfidenceCalculator`, `SkillFormulaCalculator` (question-weighted Formula B, `1.0` follow-up inheritance, `D2` `0.50` cap, explicit `N/A`, unweighted `fit`, question-weighted `coverage`), and `ReleaseGateEvaluator` (`auto_released` vs professor queue, flagging `pasteSuspected || injectionSuspected`).
- Named formula presets (`WEIGHTED_MEAN`), immutable formula versions and a what-if preview (REQ-FE-02, REQ-FE-05).
- Review/override workflow writing `audit_log` strictly through `AuditService`, SQL-only Cohort Gap Overview, `PROFESSOR` transcript export (`/api/v1/ranking/transcripts/{attemptId}`), `CANDIDATE` post-release results endpoint (`/api/v1/assessment/results/{configId}`), `EvaluationQueryService` for `teams`, and `AssessmentDeletedEvent` GDPR cleanup.
- Vue 3.5 + PrimeVue + ECharts views for Skills Matrix, Evidence Drawer, Review Queue, Cohort Gaps (`views/ranking/`), and Candidate Results (`views/assessment/ResultsView.vue`).

## Out of Scope

- Free-form SpEL formula expressions (Post-MVP, Decision 2.19).
- `SKILL.weight` grading weights (removed by Decision 2.22).
- BFI-10 scoring or passing BFI-10 to Agent 2 (owned by `assessment`, isolated by REQ-AC-16).
