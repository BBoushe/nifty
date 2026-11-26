# Tasks: Evaluation Review Hardening (`evaluation-review-hardening`)

## Status
- **Branch:** `fix/evaluation-review-and-tests` (PR #33; worktree `../nifty-review-evaluation-fix`, based on PR head 9477fd9 plus a merge of `origin/dev` 8345ffb)
- **Current:** Tasks 1–4 done; full checks run (see Handoff Log); archive step in progress.
- **Next action:** push, PR update, human merge approval
- **Uncommitted changes:** no
- **PR / MR:** GitHub #33
- **Open review findings:** none

## Tasks

- [x] **Task 1: Limit REQ-SM-03 to attempts that are not auto-released**
  - [x] Delta spec `specs/skills-matrix-review/spec.md` (`## MODIFIED Requirements`) repeats the full REQ-SM-03 with four scenarios.
  - [x] Prose check: `python3 .agents/skills/asd-ste100/scripts/ste-lint.py <file>` → 0 violations.
  - [x] Verify: after archive, the main spec holds the corrected REQ-SM-03 and REQ-SM-05 is unchanged.

- [x] **Task 2: Lock the summary row on release (Decision 2.10)**
  - [x] RED: `EvaluationReleaseRaceTest` (two threads, Testcontainers) and `EvaluationReviewServiceTest.releaseReadsTheSummaryThroughTheRowLock` (Mockito verify).
  - [x] GREEN: `CandidateEvaluationSummaryRepository.findByAttemptIdForUpdate` (`PESSIMISTIC_WRITE`) used by `releaseAttempt`.
  - [x] Update the release stubs in `EvaluationReviewServiceTest` to the locking lookup.
  - [x] `docs/api.md`: the release row names the lock and the single `409`.

- [x] **Task 3: RED evidence for the PR's existing code**
  - [x] Remove the re-release guard, run `EvaluationReviewServiceTest` → FAIL, restore.
  - [x] Replace the fail-closed branch, run `SkillFormulaCalculatorTest` → FAIL, restore.
  - [x] `git diff --quiet -- backend/src/main` after each restore.

- [x] **Task 4: Full verification**
  - [x] `cd backend && ./mvnw -B test`
  - [x] `cd frontend && npm ci && npm run typecheck && npm run lint && npm run test && npm run build`
  - [x] `OPENSPEC_TELEMETRY=0 openspec validate --all --strict`
  - [x] `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`

## Delivery

- [x] `git fetch origin && git merge origin/dev`
- [x] Full checks, archive, `docs/feature_history.md`
- [ ] Push, PR update, review, human merge approval, `forge.mjs sync dev`

## Handoff Log

### Spec fix (REQ-SM-03)
- Commits: SPEC_SHA docs(specs): limit REQ-SM-03 to attempts that are not auto-released
- Verification: RED: n/a (no behaviour, spec text); GREEN: `OPENSPEC_TELEMETRY=0 openspec validate --all --strict` → exit 0; `ste-lint.py` on the delta → 0 violations
- Contracts/Migrations added: delta spec `skills-matrix-review` (MODIFIED REQ-SM-03); archive applies it to the main spec
- Decisions: applies 2.30, 2.10
- Open Questions: none

### Release lock
- Commits: c18b7b9 fix(evaluation): lock the summary row when releasing an attempt
- Verification: RED: `./mvnw -B test -Dtest='EvaluationReviewServiceTest,EvaluationReleaseRaceTest'` → FAIL (`EvaluationReleaseRaceTest.concurrentReleasesWriteOneAuditRowAndOneGets409`, `EvaluationReviewServiceTest.releaseReadsTheSummaryThroughTheRowLock`); GREEN: same command → exit 0 (15 run)
- Contracts/Migrations added: `CandidateEvaluationSummaryRepository.findByAttemptIdForUpdate`; no migration; `docs/api.md` release row (still `409` on the second call)
- Decisions: applies 2.10 (`// Decision 2.10` pointers in `EvaluationReviewService` and the repository)
- Open Questions: approve and override take no summary lock (out of scope, see proposal)

### PR #33 changes already on the branch (bulk removal, re-release guard, fail-closed status, ledger view)
- Commits: a487eab docs(decisions): record Decision 2.30 for individual-only professor approval; 5ae41f3 fix(evaluation): remove bulk approval, guard re-release, and harden tests; 9477fd9 fix(frontend): remove bulk approval and show full conversation ledger in EvidenceDrawer
- Verification: the authoring session logged no RED. RED (mutation): `./mvnw -B test -Dtest=EvaluationReviewServiceTest` with the re-release guard removed → FAIL (`releaseRefusesAlreadyReleasedAttemptWithConflict`); RED (mutation): `./mvnw -B test -Dtest=SkillFormulaCalculatorTest` with the fail-closed branch replaced by `auto_released` → FAIL (`unknownReviewStatusFailsClosedToPendingNeverAutoReleased`); both restored, `git diff --quiet -- backend/src/main` → exit 0; bulk removal and ledger view: RED not captured (code removal and UI change, covered by `RankingViews.test.ts` and `RankingEvaluationControllerTest`); GREEN: see the final verification entry
- Contracts/Migrations added: removed `POST /api/v1/ranking/evaluations/approve` (bulk approve) and its DTOs; `docs/api.md` updated; none else
- Decisions: 2.30 (new), applies 2.10, 2.28
- Open Questions: none

### Final verification
- Commits: ARCHIVE_SHA chore(specs): archive evaluation-review-hardening
- Verification: FINAL_VERIFICATION
- Contracts/Migrations added: none
- Decisions: none
- Open Questions: none
