# Tasks: Evaluation Service (`evaluation-service`)

## Status
- **Branch:** `feat/evaluation-service` (branch off `dev`)
- **Worktree:** `../nifty-evaluation-service`
- **Current:** Archived (`2026-11-26-evaluation-service`). Second review round fixed (lost evaluations, retake rule, invented coverage, zero weights, override survival, fail-closed gate, duplicate events, judge transactions, snapshot weights) and `EvaluationSpecInvariantPropertyTest` added. Verified: 356 backend tests, 91 frontend tests, 43 harness tests, 11 OpenSpec validations (`--strict`)
- **Next action:** Push the fix commits to PR #29 / MR !27 (the builder session did not push), then cold re-review, CI check, and merge on GitHub (then `node scripts/forge.mjs sync dev`)
- **Uncommitted changes:** none after the docs commit
- **PR / MR:** GitHub #29, GitLab !27
- **Open review findings:** none (second-round findings fixed; see the last Handoff Log entry for what was not done)

## Tasks

- [x] **US-EV-01: Flyway `V7__evaluation.sql` (`UUID` schema), private repositories, and `EvaluationCleanupListener`**
  - [x] Write RED `@DataJpaTest` (PostgreSQL Testcontainers) proving `V7` applies cleanly, `chk_na_score_null` blocks `status = 'N/A'` with non-null score, multiple attempts per candidate coexist (`UNIQUE(attempt_id)`), and `AssessmentDeletedEvent` purges all 5 tables for `configId`.
  - [x] Implement `V7__evaluation.sql`, entities/repositories in `evaluation/repository/`, and `EvaluationCleanupListener`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(evaluation): add V7 UUID evaluation schema, repositories, and GDPR cleanup`

- [x] **US-EV-02: Pure deterministic core (`VerbatimQuoteVerifier`, `ConfidenceCalculator`, `SkillFormulaCalculator`, `ReleaseGateEvaluator`)**
  - [x] Write RED pure-JVM unit tests (zero Streams): verbatim quote check (`>= 8` chars, hallucinated discarded), confidence calculation + judge disagreement, question-weighted Formula B (`1.0` follow-up inheritance, `D2` `0.50` cap, explicit `N/A`, unweighted `fit`, question-weighted `coverage`), `pasteSuspected || injectionSuspected` → `flagged`, `N/A` does not fail question threshold, and threshold-gated `auto_released` vs professor review queue.
  - [x] Implement zero-Stream calculators.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(evaluation): implement deterministic verifier, confidence, Formula B, and release gate`

- [x] **US-EV-03: `TwoRunResponseJudge`, `AsyncEvaluationRunner`, and `AFTER_COMMIT` `AttemptCompletedEvent` listener**
  - [x] Write RED unit tests with mocked `ChatClient`, `ContextCreationService`, `StudioQueryService`, and `AssessmentQueryService`: 2 fresh-context calls per `(entryId, skillId)`, non-blank `timedOut = true` evaluated (Decision 2.20), blank entry skipped, duplicate event idempotency, failure isolation (no partial `skill_score` rows), rollback does not trigger run.
  - [x] Implement `TwoRunResponseJudge`, `AsyncEvaluationRunner`, and `EvaluationEventListener`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(evaluation): add async two-run judge and AFTER_COMMIT event runner`

- [x] **US-EV-04: Review queue, `AuditService` overrides, SQL Cohort Gaps, and `EvaluationQueryService`**
  - [x] Write RED tests: bulk-approve skips `flagged`, override requires non-blank `reason` + calls `AuditService.record(...)` + preserves `ai_level` + recalculates Formula B with zero LLM calls, explicit release requires zero remaining `flagged` items, latest-attempt-only matrix/cohort SQL, and `EvaluationQueryService.getCandidateProfilesForConfig` returns explicit `N/A`, returns released attempts only, and lists unreleased candidates in `pendingInviteIds` (REQ-EV-01).
  - [x] Implement `EvaluationReviewService`, `CohortGapQueryService`, and `EvaluationQueryServiceImpl`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(evaluation): implement review queue, audited overrides, cohort gaps, and query API`

- [x] **US-EV-04b: Formula presets, formula versions and what-if preview (REQ-FE-02, REQ-FE-05)**
  - [x] Write RED tests: preset list holds `WEIGHTED_MEAN`; selecting a preset stores a new immutable `formula_version`, tags recomputed scores with it and makes zero `ChatClient` calls; preview returns scores and stores nothing; an unknown preset or a free-form expression field → `400` ProblemDetail with no version stored.
  - [x] Implement `FormulaPresetService` and the `/api/v1/ranking/formula-presets` and `/api/v1/ranking/configs/{configId}/formula{,/preview}` endpoints (see `docs/api.md`).
  - [x] Verify GREEN with `cd backend && ./mvnw -B test`.
  - [x] Commit: `feat(evaluation): add formula presets, versions and what-if preview`

- [x] **US-EV-05: `RankingEvaluationController`, `CandidateResultsController`, `DeterministicAdviceBuilder`, and `docs/api.md`**
  - [x] Write RED `@WebMvcTest` + `@MockitoBean` tests: `PROFESSOR` matrix/drilldown/approve/override/release/cohort-gaps/transcript endpoints (`CANDIDATE` → `403`); `CANDIDATE` `/api/v1/assessment/results/{configId}` withholds scores before release, returns released scores + quotes + full conversation log + deterministic strengths/improvements (ignoring `N/A`) + own BFI-10 traits after release, returns `403` on other candidate or `PROFESSOR`.
  - [x] Implement controllers, `CandidateResultsService`, `DeterministicAdviceBuilder`, and sync `docs/api.md`.
  - [x] Verify GREEN with `cd backend && ./mvnw -B test` (including `ArchitectureTest`).
  - [x] Commit: `feat(evaluation): add professor matrix/review and candidate results endpoints`

- [x] **US-EV-06: Vue 3.5 + PrimeVue + ECharts Ranking Views (`views/ranking/`) and Candidate Results (`views/assessment/ResultsView.vue`)**
  - [x] Write RED Vitest tests: ECharts Skills Matrix renders hatched `N/A` and flagged border (zero client-side math), Evidence Drawer shows quote + override reason form, bulk-approve skips flagged items, Cohort Gaps renders SQL data, and `ResultsView.vue` shows pending state pre-release and full evidence + log + advice + neutral BFI-10 traits post-release.
  - [x] Implement views using PrimeVue + ECharts + Tailwind v4 tokens.
  - [x] Verify GREEN with `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`.
  - [x] Commit: `feat(frontend): add Skills Matrix, Evidence Drawer, Cohort Gaps, and Results views`

## Delivery

- [x] `git fetch origin && git merge origin/dev` (never rebase a pushed branch, no force pushes)
- [x] Run `cd backend && ./mvnw -B test`, `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all`, and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` (expect exit 0).
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive evaluation-service -y`, update `docs/feature_backlog.md` and `docs/feature_history.md`, commit `chore(specs): archive evaluation-service`.
- [x] Push and open PR/MR via `node scripts/forge.mjs`.
- [ ] Pass cold read-only review, CI checks, human merge approval, and mirror sync.

## Handoff Log

### US-EV-01
- Commits: `f70f563 feat(evaluation): add V7 UUID evaluation schema, repositories, and GDPR cleanup`
- Verification: `RED: ./mvnw -B test -Dtest=EvaluationSchemaAndCleanupTest → FAIL (cannot find symbol EvaluationCleanupListener/evaluation repositories; later caught SQL 3-valued logic bug in chk_na_score_null); GREEN: cd backend && ./mvnw -B test → exit 0 (192 tests)`
- Contracts/Migrations added: `V7__evaluation.sql`, `evaluation/repository/{FormulaVersion,EvaluationRun,CandidateEvaluationSummary,ResponseEvaluation,SkillScore}{Entity,Repository}.java`, `EvaluationCleanupListener.java`
- Decisions: 1.10, 1.19, 2.13, 4.4
- Open Questions: none

### US-EV-02
- Commits: `185ad23 feat(evaluation): implement deterministic verifier, confidence, Formula B, and release gate`
- Verification: `RED: ./mvnw -B test -Dtest=VerbatimQuoteVerifierTest,ConfidenceCalculatorTest,SkillFormulaCalculatorTest,ReleaseGateEvaluatorTest → FAIL (cannot find symbol VerbatimQuoteVerifier/ConfidenceCalculator/SkillFormulaCalculator/ReleaseGateEvaluator); GREEN: cd backend && ./mvnw -B test → exit 0 (209 tests)`
- Contracts/Migrations added: `VerbatimQuoteVerifier.java`, `ConfidenceCalculator.java`, `SkillFormulaCalculator.java`, `ReleaseGateEvaluator.java`
- Decisions: 2.2, 2.4, 2.10, 2.11, 2.12, 2.20, 2.22, 2.24
- Open Questions: none

### US-EV-03
- Commits: `721f64a feat(evaluation): add async two-run judge and AFTER_COMMIT event runner`
- Verification: `RED: ./mvnw -B test -Dtest=TwoRunResponseJudgeTest,AsyncEvaluationRunnerTest,EvaluationEventListenerRollbackTest → FAIL (cannot find symbol TwoRunResponseJudge/AsyncEvaluationRunner); GREEN: ./mvnw -B test -Dtest=TwoRunResponseJudgeTest,AsyncEvaluationRunnerTest,EvaluationEventListenerRollbackTest → exit 0 (7/7 tests; 216/216 total)`
- Contracts/Migrations added: `EvaluationAsyncConfig.java`, `TwoRunResponseJudge.java`, `AsyncEvaluationRunner.java`, `EvaluationEventListener.java`
- Decisions: 1.12, 1.18, 2.10, 2.20, 2.24, 2.25
- Open Questions: none

### US-EV-04
- Commits: `c38cdf9 feat(evaluation): implement review queue, audited overrides, cohort gaps, and query API`
- Verification: `RED: ./mvnw -B test -Dtest=EvaluationReviewServiceTest,CohortGapAndQueryIntegrationTest → FAIL (cannot find symbol EvaluationReviewService/CohortGapQueryService/EvaluationDtos); GREEN: ./mvnw -B test -Dtest=EvaluationReviewServiceTest,CohortGapAndQueryIntegrationTest,ContractStubsTest,ArchitectureTest → exit 0 (21/21 tests)`
- Contracts/Migrations added: `EvaluationDtos.java`, `FormulaRecalculationHelper.java`, `EvaluationReviewService.java`, `CohortGapQueryService.java`, `EvaluationQueryServiceImpl.java` (removed `EvaluationQueryServicePlaceholder.java`)
- Decisions: 1.18, 2.10, 2.13, 2.15, 2.22, 2.24
- Open Questions: none

### US-EV-04b
- Commits: `27799d2 feat(evaluation): add formula presets, versions and what-if preview`
- Verification: `RED: ./mvnw -B test -Dtest=FormulaPresetServiceTest,FormulaPresetControllerTest → FAIL (cannot find symbol FormulaPresetService/FormulaPresetController); GREEN: ./mvnw -B test -Dtest=FormulaPresetServiceTest,FormulaPresetControllerTest,ArchitectureTest → exit 0 (19/19 tests)`
- Contracts/Migrations added: `FormulaPresetService.java`, `FormulaPresetController.java`
- Decisions: 2.19, 2.22
- Open Questions: none

### US-EV-05
- Commits: `29db5ce feat(evaluation): add professor matrix/review and candidate results endpoints`
- Verification: `RED: ./mvnw -B test -Dtest=DeterministicAdviceBuilderTest,CandidateResultsServiceTest,RankingEvaluationControllerTest,CandidateResultsControllerTest → FAIL (cannot find symbol CandidateResultsService/DeterministicAdviceBuilder/RankingEvaluationController/CandidateResultsController); GREEN: cd backend && ./mvnw -B test → exit 0 (234 tests)`
- Contracts/Migrations added: `DeterministicAdviceBuilder.java`, `CandidateResultsService.java`, `RankingEvaluationController.java`, `CandidateResultsController.java`, `docs/api.md`
- Decisions: 2.10, 2.15, 2.16, 4.3
- Open Questions: none

### US-EV-06
- Commits: `1fb0fa5 feat(frontend): add Skills Matrix, Evidence Drawer, Cohort Gaps, and Results views`
- Verification: `RED: npx vitest run src/views/ranking/RankingViews.test.ts src/views/assessment/ResultsView.test.ts → FAIL (Failed to resolve import ./ResultsView.vue and ./SkillsMatrixPanel.vue); GREEN: cd frontend && npm run typecheck && npm run lint && npm run test && npm run build → exit 0 (85 tests)`
- Contracts/Migrations added: `frontend/src/api/evaluation.ts`, `frontend/src/views/ranking/{RankingView,SkillsMatrixPanel,EvidenceDrawer,CohortGapsPanel}.vue`, `frontend/src/views/assessment/ResultsView.vue`, updated ranking/assessment `routes.ts`
- Decisions: 1.14, 2.10, 2.15, 2.16, 4.3
- Open Questions: none

### Cold Review, Invariance & Mutation Hardening
- Commits: `fix(evaluation): address cold review findings and add invariance and mutation tests`
- Verification: `RED: ./mvnw -B test -Dtest=AsyncEvaluationRunnerIntegrationTest,EvaluationReviewServiceTest → FAIL (missing approveEvaluation + unverified quote/read-only TX findings) & npx vitest run src/views/ranking/RankingViews.test.ts → FAIL (expected [2.25, 0] to equal [2.25, null]); GREEN: cd backend && ./mvnw -B test → exit 0 (249 tests), cd frontend && npm run typecheck && npm run lint && npm run test && npm run build → exit 0 (85 tests), PITest 94% line / 80% test strength (MathMutator 97%, PrimitiveReturns 100%)`
- Contracts/Migrations added: `POST /api/v1/ranking/evaluations/{responseEvalId}/approve` (synced in `docs/api.md`, `RankingEvaluationController`, `EvaluationReviewService`, `EvidenceDrawer.vue`), `EvaluationInvariantPropertyTest.java`, `AsyncEvaluationRunnerIntegrationTest.java`
- Decisions: 2.10, 2.11, 2.16, 2.22, 2.24, 2.25
- Open Questions: none

### Task N (Review and property-test fixes)
- Commits: `08b20c4 fix(evaluation): keep invented entries out of coverage and guard zero weights`, `1975fe0 fix(evaluation): fail closed in the release gate and judge outside transactions`, `124660d fix(evaluation): queue evaluations before dispatch, retry failures and show waiting attempts`, `ebcb9bb test(evaluation): add spec invariant property tests`
- Verification: `RED: cd backend && ./mvnw -B test -Dtest=ReleaseGateEvaluatorTest,TwoRunResponseJudgeTest,SkillFormulaCalculatorTest,EvaluationSpecInvariantPropertyTest (old calculator, gate and judge) → FAIL (7 failures: invented entry counted toward coverage, zero-weight score 1.0 outside levels 2..4, fail-open gate, judge ran inside a transaction); lifecycle RED = compile error (no enqueue/executeRun/findLatestPerInvite); GREEN: cd backend && ./mvnw -B test → exit 0 (356 tests, incl. ArchitectureTest), cd frontend && npm run typecheck && npm run lint && npm run test && npm run build → exit 0 (91 tests), node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs → exit 0 (43 tests), OPENSPEC_TELEMETRY=0 openspec validate --all --strict → exit 0 (10 items)`
- Contracts/Migrations added: `V7__evaluation.sql` (edited, not yet merged: `attempt_count` default 0, new `updated_at`), `EvaluationRetryScheduler.java`, `EvaluationRunRepository.claim/findRetryableIds`, `CandidateEvaluationSummaryRepository.findLatestPerInvite`, `MatrixCandidateRow.evaluationStatus` (`docs/api.md`, `frontend/src/api/evaluation.ts`, `SkillsMatrixPanel.vue`), new `409` cases on approve, release and formula apply, config keys `nifty.evaluation.retry.*`, `EvaluationLifecycleIntegrationTest.java`, `EvaluationRetrySchedulerIntegrationTest.java`, `EvaluationSpecInvariantPropertyTest.java`
- Decisions: 2.10, 2.13, 2.22, 2.24 (design choices in `design.md` "Review Fixes"; no new decision recorded)
- Open Questions: none. Not done: a crash between the assessment commit and `enqueue` still loses the event, and a `RUNNING` run that is stale at its last try is never reclaimed (both are `shortcut:` notes in `design.md`)
