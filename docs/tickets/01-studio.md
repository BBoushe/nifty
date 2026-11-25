# Ticket #1: `studio` — Assessment Creation Service (Teammate R)

- **OpenSpec Change:** [`openspec/changes/studio-assessment-creation/`](../../openspec/changes/studio-assessment-creation/)
- **Branch / Worktree:** `feat/studio-assessment-creation` (`../nifty-studio-assessment-creation`)
- **Owned Folders:** `backend/src/main/java/de/fhdortmund/nifty/studio/`, `frontend/src/views/studio/`, `frontend/src/stores/assessmentForm.ts`
- **Reserved Migration:** `V3__studio.sql` (single file or `V3_1`..`V3_4` — never touch `V1`, `V2`, `V4`, `V6`, `V7`, `V8`)
- **Wave:** Wave 2 (start `US-ST-01` hand-off records + fixtures immediately on Day 1!)

## Key Convergence Fixes Applied to Original Draft
1. **ArchUnit fix (Blocking):** Put ALL `@Entity` classes inside `studio/repository/` (NOT `studio/domain/`). `ArchitectureRules.java` rejects `domain/`.
2. **SecurityConfig fix (Blocking):** Mount controller at **`/api/v1/ranking/studio/configs`** (NOT `/api/v1/studio/configs`) so `SecurityConfig`'s `PROFESSOR` path guard covers it automatically.
3. **Validation envelope fix:** Throw `shared/exception/FieldValidationException` → **`400` RFC 9457 `ProblemDetail`** with `errors: [{ field, message, code }]` (NOT `422` and NEVER import `studio` inside `shared/`).
4. **Retained converged fields:** Keep `max_attempts` (default `1`), `invented_questions` (default `false`), `grouping_mode` (`HARD_SKILLS | SOFT_SKILLS | COMBINED`), `soft_preset`, `combined_hard_weight`, `alpha`, and `DELETE /api/v1/ranking/studio/configs/{configId}` (emits `AssessmentDeletedEvent` + `SET LOCAL nifty.allow_purge = 'on'`, Decision 4.4). Store `min_coverage`, `confidence_threshold`, `combined_hard_weight`, `alpha` as `0..1` decimals in DB/API (UI renders `%`).
5. **`SOFT_SKILLS`-only exemption:** Allow `0` skills and `0` core questions ONLY when `grouping_mode == SOFT_SKILLS && bfi10_enabled == true` (Decision 2.23). Keep no `SKILL.weight` column (Decision 2.22).

## Exposes / Consumes
- **Exposes:** `StudioQueryService` (`exists`, `interviewerView`, `evaluatorView`, `scoringView`, `teamView`), `POST/GET/DELETE /api/v1/ranking/studio/configs/**`, `AssessmentDeletedEvent(configId, actorId)`.
- **Consumes:** `AuthContextService.currentProfessorId()` (`auth`), `AuditService.record(...)` (`audit`). Mock both in unit tests until Wave 1 merges.

## Ready-to-Paste Agent Launch Prompt

```text
You are implementing the `studio` vertical slice (`studio-assessment-creation`) for Nifty.
Setup (do this before anything else):
1. Prerequisite check: run `git fetch origin && git show origin/dev:backend/src/main/java/de/fhdortmund/nifty/audit/service/AuditService.java > /dev/null && git show origin/dev:frontend/package.json | grep -q '"test":'`. If it fails, Wave 0 (`backend-foundation` with the US-BF-06 contract stubs, and `frontend-foundation`) is not on `dev` yet. Stop and tell the human.
2. From the main checkout, run `git worktree add ../nifty-studio-assessment-creation -b feat/studio-assessment-creation origin/dev`. Start every Bash command with `cd /absolute/path/to/nifty-studio-assessment-creation && pwd`, and pass only absolute worktree paths to Edit/Write. `forge.mjs` reads `.env` from the main checkout, so do not copy it. Never read `.env` yourself.
3. The shared interfaces, records and enums of `docs/tickets/00-parallel-waves-and-contracts.md` §3 already exist as stubs on `dev`. Implement only the ones your slice owns. Mock the others. Never create a second copy of a shared type.

Read these files first (do NOT scan the whole source tree):
1. `AGENTS.md`, `.agents/rules/coding-standards.md`, `.agents/rules/backend-java.md`, `.agents/rules/frontend-vue.md`
2. `docs/architecture.md`, `docs/api.md`, `docs/tickets/00-parallel-waves-and-contracts.md`
3. `openspec/specs/interviewer-studio/spec.md`
4. `openspec/changes/studio-assessment-creation/{proposal.md,design.md,tasks.md,specs/interviewer-studio/spec.md}`

Non-negotiable rules:
- Use the `spec-implementer` skill for the tasks and the `pr-memo` skill for the PR/MR body. Commit after each task, but push only after the human says yes. Never merge. Ask the human before you push or open the PR/MR. Update `tasks.md` `## Status` before every stop, so another session can resume.
- Follow TDD strictly (RED failing test -> GREEN minimal code -> REFACTOR) for US-ST-01 through US-ST-06 in `tasks.md`.
- Put ALL `@Entity` classes and package-private `Repository<T, UUID>` interfaces in `de.fhdortmund.nifty.studio.repository` (never `studio.domain`).
- Mount all routes under `/api/v1/ranking/studio/configs` (`PROFESSOR` only). Return `400` RFC 9457 `ProblemDetail` via `shared/exception/FieldValidationException` (never import `studio` in `shared/`).
- Use `UUID` IDs everywhere, `0..1` decimals for ratios/thresholds, no `SKILL.weight` column, and exact `StudioQueryService` signatures from `docs/tickets/00-parallel-waves-and-contracts.md`.
- Update `docs/api.md` when adding endpoints, leave `// Decision X.Y` pointers, and record RED/GREEN verification in `tasks.md` `## Handoff Log`.
- Run `./mvnw -B test` (including `ArchitectureTest`) and `npm run typecheck && npm run lint && npm run test && npm run build`, archive the change before pushing, and open PR/MR into `dev` via `node scripts/forge.mjs`.
```
