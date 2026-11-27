# Ticket #1: `audit` + `auth` + `studio` — Shared Access Layer and Assessment Creation (Teammate R)

- **OpenSpec Changes (run in this order):** [`audit-log-service`](../../openspec/changes/audit-log-service/), [`auth-accounts`](../../openspec/changes/auth-accounts/), [`studio-assessment-creation`](../../openspec/changes/studio-assessment-creation/), [`invite-links`](../../openspec/changes/invite-links/)
- **Branch / Worktree:** one per change: `feat/<change>` in `../nifty-<change>`
- **Owned Folders:** `backend/.../{audit,auth,studio}/`, `frontend/src/views/{auth,studio}/`, `frontend/src/stores/{auth,assessmentForm}.ts`, the auth parts of `shared/config/SecurityConfig.java` and `frontend/src/api/client.ts`
- **Reserved Migrations:** `V2__auth_accounts.sql`, `V3__studio.sql` (single file or `V3_1`..`V3_4`), `V4__auth_invites.sql`. Never touch `V1`, `V6`, `V7`, `V8`.
- **Waves:** 1 (audit, auth), 2 (studio), 3 (invites). You can start now: Wave 0 is on `dev`, and every shared stub already exists.

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

1. Open a terminal in the main `nifty` folder (not in a worktree) and run `claude`. Your agent creates one worktree per change, for example `../nifty-audit-log-service`.
2. Paste exactly this prompt:
   ```text
   Read docs/tickets/01-studio.md. Work through its "Run Order" section from the top: for each change, run the "Ready-to-Paste Agent Launch Prompt" with that change's names, starting with Setup step 1, and obey the "Known gaps" section. Ask me before you push or open a PR/MR, and never merge.
   ```
3. Approve the push and the PR/MR when the agent asks. It commits after every task, so you can stop at any time. To continue later, start `claude` in the main folder again and paste: `Resume docs/tickets/01-studio.md: find the first change in its Run Order that is not archived, and continue from the Status block of its tasks.md.`

### 3. Run Order (your agent works through these four changes)

| # | Change | Branch / worktree | Start when |
|---|---|---|---|
| 1 | [`audit-log-service`](../../openspec/changes/audit-log-service/) | `feat/audit-log-service` / `../nifty-audit-log-service` | now |
| 2 | [`auth-accounts`](../../openspec/changes/auth-accounts/) | `feat/auth-accounts` / `../nifty-auth-accounts` | after the PR/MR of #1 is open |
| 3 | [`studio-assessment-creation`](../../openspec/changes/studio-assessment-creation/) | `feat/studio-assessment-creation` / `../nifty-studio-assessment-creation` | after the PR/MR of #2 is open |
| 4 | [`invite-links`](../../openspec/changes/invite-links/) | `feat/invite-links` / `../nifty-invite-links` | after #2 **and** #3 are merged on `dev` (it needs the session code and `ownedBy`) |

- Each change gets its own worktree, branch and PR/MR. Base #1–#3 on the local branch `docs/ticket-01-prestep`, because the change plans live there until the first PR merges. Base #4 on `origin/dev`. Do not wait for a merge before #2 or #3. When an earlier change merges, run `git merge origin/dev` in the later branches.
- Each change folder holds `proposal.md`, `design.md`, `specs/` and `tasks.md`. Read them before you code, and follow `tasks.md` step by step.
- Run `git merge origin/dev` before each PR. On a conflict in a shared file (`docs/api.md`, `CHANGELOG.md`, `docs/feature_backlog.md`, `docs/feature_history.md`, `SecurityConfig.java`), keep both sides.

### 4. Known gaps (your agent must obey these)

- **Until #1 and #2 are merged on `dev`:** `AuditService` and `AuthContextService` are placeholder beans that throw `UnsupportedOperationException`.
  - In studio unit tests, mock both interfaces with Mockito.
  - In Spring tests (`@SpringBootTest`, `@WebMvcTest`), replace both with `@MockitoBean`.
  - In production code, inject the interfaces. Never refer to a placeholder class by name.
- **Sessions and CSRF:** after #2, every state-changing request needs the CSRF token. In MockMvc tests, add `.with(csrf())`. In the frontend, use `apiFetch` and `postSse` from `frontend/src/api/client.ts`, which send the token (Decision 1.23).
- **Professor account for manual tests:** set `NIFTY_BOOTSTRAP_PROFESSOR_EMAIL` and `NIFTY_BOOTSTRAP_PROFESSOR_PASSWORD` in `.env` (Decision 1.24). Before #2 merges, test the frontend with Vitest and a mocked `fetch` only.
- **No cross-slice foreign keys:** `professor_id` and `config_id` are plain `UUID NOT NULL` columns (contract pack §1).
- **Use what Wave 0 built:** PrimeVue (themed by `src/theme/preset.ts`), `ScoreBadge`, `PostgresContainerBase` for every Spring test. Add routes only in your own `views/<feature>/routes.ts`.
- **Contract change you own:** studio adds `ownedBy(configId, professorId)` to `StudioQueryService` (US-ST-04). Update its placeholder in the same commit. `invite-links` uses it.
- **Placeholders:** delete `AuditServicePlaceholder` (#1), `AuthContextServicePlaceholder` (#2) and `StudioQueryServicePlaceholder` (#3) when your real implementation lands.

### 5. When the agent finishes

For each change, the agent archives it, asks you, pushes, opens the PR/MR, and moves on to the next change in the Run Order. Tell Teammate A (repo maintainer) about each new PR/MR. Teammate A runs a cold review, checks CI on both forges, merges on GitHub, and syncs GitLab. Only a GitLab Maintainer can run that sync.

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
You are implementing one change of the Run Order in docs/tickets/01-studio.md for Nifty. Below, <change> is that change's name (audit-log-service, auth-accounts, studio-assessment-creation or invite-links).
Setup (do this before anything else):
1. Prerequisite check: run `git fetch origin && git show origin/dev:backend/src/main/java/de/fhdortmund/nifty/audit/service/AuditService.java > /dev/null && git show origin/dev:frontend/package.json | grep -q '"test":'`. If it fails, Wave 0 (`backend-foundation` with the US-BF-06 contract stubs, and `frontend-foundation`) is not on `dev` yet. Stop and tell the human.
2. From the main checkout, run `git worktree add ../nifty-<change> -b feat/<change> <base>` (<base> is `docs/ticket-01-prestep` while `git merge-base --is-ancestor docs/ticket-01-prestep origin/dev` fails, else `origin/dev`; that branch holds these change plans until the first PR merges). Start every Bash command with `cd /absolute/path/to/nifty-<change> && pwd`, and pass only absolute worktree paths to Edit/Write. `forge.mjs` reads `.env` from the main checkout, so do not copy it. Never read `.env` yourself.
3. The shared interfaces, records and enums of `docs/tickets/00-parallel-waves-and-contracts.md` §3 already exist as stubs on `dev`. Implement only the ones your slice owns. Mock the others. Never create a second copy of a shared type.

Read these files first (do NOT scan the whole source tree):
1. `AGENTS.md`, `.agents/rules/coding-standards.md`, `.agents/rules/backend-java.md`, `.agents/rules/frontend-vue.md`
2. `docs/architecture.md`, `docs/api.md`, `docs/tickets/00-parallel-waves-and-contracts.md`
3. The domain spec of <change>: `openspec/specs/interviewer-studio/spec.md` (studio) or `openspec/specs/auth-access/spec.md` (auth-accounts, invite-links). audit-log-service has only its change-local spec.
4. `openspec/changes/<change>/{proposal.md,design.md,tasks.md}` and every file under `openspec/changes/<change>/specs/`

Non-negotiable rules:
- Use the `spec-implementer` skill for the tasks and the `pr-memo` skill for the PR/MR body. Commit after each task, but push only after the human says yes. Never merge. Ask the human before you push or open the PR/MR. Update `tasks.md` `## Status` before every stop, so another session can resume.
- Follow TDD strictly (RED failing test -> GREEN minimal code -> REFACTOR) for every task in `openspec/changes/<change>/tasks.md`.
- Put ALL `@Entity` classes and package-private repository interfaces in `de.fhdortmund.nifty.<slice>.repository` (`audit`, `auth` or `studio`; never `<slice>.domain`).
- Mount routes exactly as `docs/api.md` §5 and the change's `design.md` say (`/api/v1/ranking/studio/configs/**` for studio and invites, `/api/v1/auth/**` for sign-in). Return `400` RFC 9457 `ProblemDetail` via `shared/exception/FieldValidationException`. `shared/` never imports a feature slice.
- Use `UUID` IDs (except the merged `V1` `audit_log`), `0..1` decimals for ratios and thresholds, no `SKILL.weight` column, and the exact signatures of `docs/tickets/00-parallel-waves-and-contracts.md` §3.
- Update `docs/api.md` when adding endpoints, leave `// Decision X.Y` pointers, and record RED/GREEN verification in `tasks.md` `## Handoff Log`.
- Run `./mvnw -B test` (including `ArchitectureTest`) and `npm run typecheck && npm run lint && npm run test && npm run build`, archive the change before pushing, and open PR/MR into `dev` via `node scripts/forge.mjs`.
```
