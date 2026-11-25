# Ticket #5: `teams` — Group Creation / Team Builder (Teammate C)

- **OpenSpec Change:** [`openspec/changes/team-builder/`](../../openspec/changes/team-builder/)
- **Branch / Worktree:** `feat/team-builder` (`../nifty-team-builder`)
- **Owned Folders:** `backend/src/main/java/de/fhdortmund/nifty/teams/`, `frontend/src/views/teams/`
- **Reserved Migration:** `V8__teams.sql`
- **Wave:** Wave 5 (start pure-JVM optimizer + mocked-upstream stories immediately in parallel!)

## Key Convergence Fixes Applied to Original Draft
1. **Explicit `N/A` handling (Blocking, Invariant 2 & Decision 1.20):** NEVER reject team generation when a candidate has `N/A` for one or more hard skills, and NEVER coerce `N/A` to `0` or `1`! Exclude `N/A` entries per skill dimension when computing team averages and intra-team pairwise diversity, and report `naCount` in diagnostics.
2. **Engine locked (Decision 1.20):** Implement the deterministic seeded pure-Java **Simulated Annealing + 2-opt swap** optimizer (replacing Timefold in MVP) using primitive `double[][]` + `boolean[][]` arrays with zero Java Streams in inner loops.
3. **Single-source BFI-10 (Decision 4.3):** Consume already reverse-scored `1.0..5.0` trait means (`conscientiousness`, `agreeableness`, `neuroticism`, `extraversion`, `openness`) from `AssessmentQueryService.getSoftSkillProfilesForConfig(configId)` — **NEVER double-invert!**
4. **LLM-generated questions removed from MVP (Decision 4.2):** Stay strictly on team formation; question authoring lives in `studio`.
5. **Professor approval + GDPR + `UUID` IDs:** Proposals start as `DRAFT` and move to `PUBLISHED` via `POST /api/v1/ranking/teams/{proposalId}/publish` (writing `TEAMS_PUBLISHED` through `AuditService`); listen to `AssessmentDeletedEvent` to delete `team_proposal` and `team_member` rows.

## Exposes / Consumes
- **Exposes:** `PROFESSOR`-only `/api/v1/ranking/configs/{configId}/teams/generate`, `/api/v1/ranking/teams/{proposalId}/recalculate`, `/api/v1/ranking/teams/{proposalId}/publish`, `GET /api/v1/ranking/configs/{configId}/teams`.
- **Consumes:** `StudioQueryService.teamView`, `EvaluationQueryService.getCandidateProfilesForConfig`, `AssessmentQueryService.getSoftSkillProfilesForConfig`, `AuditService`, `AssessmentDeletedEvent`.

## Ready-to-Paste Agent Launch Prompt

```text
You are implementing the `teams` vertical slice (`team-builder`) for Nifty.
Setup (do this before anything else):
1. Prerequisite check: run `git fetch origin && git show origin/dev:backend/src/main/java/de/fhdortmund/nifty/audit/service/AuditService.java > /dev/null && git show origin/dev:frontend/package.json | grep -q '"test":'`. If it fails, Wave 0 (`backend-foundation` with the US-BF-06 contract stubs, and `frontend-foundation`) is not on `dev` yet. Stop and tell the human.
2. From the main checkout, run `git worktree add ../nifty-team-builder -b feat/team-builder origin/dev`. Start every Bash command with `cd /absolute/path/to/nifty-team-builder && pwd`, and pass only absolute worktree paths to Edit/Write. `forge.mjs` reads `.env` from the main checkout, so do not copy it. Never read `.env` yourself.
3. The shared interfaces, records and enums of `docs/tickets/00-parallel-waves-and-contracts.md` §3 already exist as stubs on `dev`. Implement only the ones your slice owns. Mock the others. Never create a second copy of a shared type.

Read these files first (do NOT scan the whole source tree):
1. `AGENTS.md`, `.agents/rules/coding-standards.md`, `.agents/rules/backend-java.md`, `.agents/rules/frontend-vue.md`
2. `docs/architecture.md`, `docs/api.md`, `docs/tickets/00-parallel-waves-and-contracts.md`
3. `openspec/specs/team-builder/spec.md`
4. `openspec/changes/team-builder/{proposal.md,design.md,tasks.md,specs/team-builder/spec.md}`

Non-negotiable rules:
- Use the `spec-implementer` skill for the tasks and the `pr-memo` skill for the PR/MR body. Commit after each task, but push only after the human says yes. Never merge. Ask the human before you push or open the PR/MR. Update `tasks.md` `## Status` before every stop, so another session can resume.
- Follow TDD strictly (RED failing test -> GREEN minimal code -> REFACTOR) for US-TB-01 through US-TB-06 in `tasks.md`.
- All `@Entity` and `JpaRepository` classes live privately in `de.fhdortmund.nifty.teams.repository` (`UUID` IDs in `V8__teams.sql`).
- Implement `TeamObjectiveCalculator` and `SimulatedAnnealingTeamOptimizer` in pure Java using primitive arrays and plain loops (ZERO Java Streams in hot paths, ZERO LLM calls).
- Enforce Decision 1.20 (deterministic seeded Simulated Annealing + 2-opt swaps keeping team sizes in {floor(N/G), ceil(N/G)}; explicit `N/A` exclusion per dimension without rejecting cohorts or coercing to 0/1), Decision 2.23 (`HARD_SKILLS | SOFT_SKILLS | COMBINED`, `ENGINEERING_DEFAULT` `[0.30, 0.25, 0.20, 0.15, 0.10]`), and Decision 4.3 (consume already reverse-scored `1.0..5.0` BFI-10 means from `AssessmentQueryService` — never reverse-score again; show traits to `PROFESSOR` only).
- Mock `StudioQueryService`, `EvaluationQueryService`, `AssessmentQueryService`, and `AuditService` in unit tests so you can build immediately in parallel.
- Add `TeamCleanupListener` on `AssessmentDeletedEvent`, sync `docs/api.md`, build Vue UI in `frontend/src/views/teams/` (PrimeVue + ECharts, drag-and-drop recalculation, Publish approval), verify with `./mvnw -B test` + frontend checks, archive before pushing, and open PR/MR via `node scripts/forge.mjs`.
```
