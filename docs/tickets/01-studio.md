# Ticket #1: `studio` — Assessment Creation Service (Teammate R)

- **OpenSpec Change:** [`openspec/changes/studio-assessment-creation/`](../../openspec/changes/studio-assessment-creation/)
- **Branch / Worktree:** `feat/studio-assessment-creation` (`../nifty-studio-assessment-creation`)
- **Owned Folders:** `backend/src/main/java/de/fhdortmund/nifty/studio/`, `frontend/src/views/studio/`, `frontend/src/stores/assessmentForm.ts`
- **Reserved Migration:** `V3__studio.sql` (single file or `V3_1`..`V3_4` — never touch `V1`, `V2`, `V4`, `V6`, `V7`, `V8`)
- **Wave:** Wave 2. You can start now: Wave 0 is on `dev`, and the `StudioQueryService` stubs already exist.

## Before You Start (Teammate R, do these steps yourself)

### 1. Machine setup (once)

1. Install Node 22+, Java 21, Docker Desktop and Claude Code. Start Docker. The backend tests need it, because they run PostgreSQL in Testcontainers.
2. Install and sign in to `gh` (GitHub) and `glab` (GitLab, host `git.inf.fh-dortmund.de`).
3. Get the repo and the latest `dev`. Skip the clone if you already have the repo:
   ```
   git clone https://github.com/BBoushe/nifty.git
   cd nifty
   git checkout dev
   git pull
   ```
4. Copy `.env.example` to `.env` and set `GIT_FORGE=both` and `GITLAB_HOST=git.inf.fh-dortmund.de`. Keep the other defaults. Your agent never reads this file.
5. Run `node scripts/check-setup.mjs` and fix every item that it reports. This also turns on the git hooks.
6. Install the two Claude Code plugins for this project:
   ```
   claude plugin install superpowers@claude-plugins-official --scope project
   claude plugin marketplace add DietrichGebert/ponytail
   claude plugin install ponytail@ponytail --scope project
   ```

### 2. Launch the agent

1. Open a terminal in the main `nifty` folder (not in a worktree) and run `claude`. Your agent creates its own worktree `../nifty-studio-assessment-creation`.
2. Paste exactly this prompt:
   ```text
   Read docs/tickets/01-studio.md. Run the "Ready-to-Paste Agent Launch Prompt" in that file exactly as written, starting with Setup step 1. Also obey the "Known gaps" section of that file. Ask me before you push or open a PR/MR, and never merge.
   ```
3. Approve the push and the PR/MR when the agent asks. It commits after every task, so you can stop at any time. To continue later, start `claude` in the main folder again and paste: `Resume docs/tickets/01-studio.md from the Status block in openspec/changes/studio-assessment-creation/tasks.md.`

### 3. Known gaps (your agent must obey these)

- **Auth and audit are not built yet.** `auth-accounts` (professor login, `AuthContextService`) and `audit-log-service` (`AuditService`) are separate shared changes. Do not build them in this ticket. On `dev`, both interfaces have placeholder beans that throw `UnsupportedOperationException`.
  - Unit tests: mock both interfaces with Mockito.
  - Spring tests (`@SpringBootTest`, `@WebMvcTest`): replace both with `@MockitoBean`, because the placeholders throw.
  - Production code: inject the interfaces. Never refer to the placeholder classes by name.
- **No login yet.** You cannot open the studio pages against a real backend in the browser. Test the frontend with Vitest and a mocked `fetch`.
- **No foreign key to `professor_account`.** That table belongs to `auth` (`V2`). Store `professor_id UUID NOT NULL` without a foreign key (contract pack §1).
- **Use what Wave 0 built:** `apiFetch` and `ApiProblem` from `frontend/src/api/client.ts`, PrimeVue (themed by `src/theme/preset.ts`), and `PostgresContainerBase` for every Spring test. Add your routes only in `frontend/src/views/studio/routes.ts`.
- **US-ST-01:** the `StudioQueryService` records already exist as stubs. Check them against the contract pack §3 and add the reflection guard test. Never write a second copy. Delete `StudioQueryServicePlaceholder` when `StudioQueryServiceImpl` lands in US-ST-04.

### 4. When the agent finishes

The agent archives the change, pushes, opens the PR/MR, and stops. Then tell Teammate A (repo maintainer). Teammate A runs a cold review, checks CI on both forges, merges on GitHub, and syncs GitLab. Only a GitLab Maintainer can run that sync.

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
