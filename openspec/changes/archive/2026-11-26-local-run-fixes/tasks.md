# Tasks: Local Run Fixes (`local-run-fixes`)

## Status
- **Branch:** `fix/local-run-fixes` (branch off `dev` 94a1eee)
- **Worktree:** `../nifty-local-run-fixes`
- **Current:** Tasks 1–3 done; live check on the Docker stack: the first requeued evaluation run COMPLETED with `GPT-OSS-120B`.
- **Next action:** push, PR/MR, review, human merge approval
- **Uncommitted changes:** no
- **PR / MR:** —
- **Open review findings:** —

## Tasks

- [x] **Task 1: Wrong-role route (REQ-NAV-09)**
  - [x] RED: `frontend/src/views/auth/guard.test.ts` — candidate on `/ranking` goes to `/assessment`; professor on `/assessment` goes to `/ranking/studio`.
  - [x] GREEN: `guard.ts` returns the role start page when a session of the other role exists.
  - [x] Verify: `cd frontend && npm run test -- src/views/auth`.

- [x] **Task 2: ki:connect model IDs and frontend base URL**
  - [x] Evidence: `GET {AI_EVALUATOR_BASE_URL}/models` lists `GPT-OSS-120B`, `Qwen3.8 27B`, `mistralai-mistral-small-4`.
  - [x] Change `application.yml`, `docker-compose.yml`, `.env.example`, `README.md`, `docs/architecture.md`, `docs/ai_docs/spring-ai-2-kiconnect.md`.
  - [x] Pass `NIFTY_FRONTEND_BASE_URL` in `docker-compose.yml`; add it to `.env.example`.
  - [x] Verify: `docker compose config` shows both values; `cd backend && ./mvnw -B test`.

- [x] **Task 3: Agent guard accepts worktree paths (harness)**
  - [x] RED: `.claude/hooks/hooks.test.mjs` "guard-agent.mjs worktrees" — a test path in a worktree of this repo is blocked.
  - [x] GREEN: `guard-agent.mjs` measures an absolute path from its own worktree root when that worktree belongs to this repo.
  - [x] Verify: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`.

## Delivery

- [x] `git fetch origin && git merge origin/dev`
- [ ] Full checks, archive, `docs/feature_history.md`, push, PR/MR, review, human merge approval, `forge.mjs sync dev`

## Handoff Log

### Task 1 (REQ-NAV-09)
- Commits: c400376 fix(frontend): send a signed-in user of the other role to their start page
- Verification: RED: `npx vitest run src/views/auth/guard.test.ts` → FAIL (2 failed: candidate on `/ranking` and professor on `/assessment/results` went to `/sign-in`); GREEN: `npx vitest run src/views/auth src/router` → exit 0 (21 run)
- Contracts/Migrations added: `startPageFor(role)` in `guard.ts`, reused by the `/` start page
- Decisions: applies 1.15
- Open Questions: none

### Task 2 (model IDs, frontend base URL)
- Commits: 5f73afc fix(config): use ki:connect model IDs and pass the frontend URL to the backend
- Verification: RED (live): `evaluation_run.error_message` = `404: The model 'OpenAI GPT OSS 120B' does not exist` on both runs; `GET {AI_EVALUATOR_BASE_URL}/models` lists `GPT-OSS-120B`, `Qwen3.8 27B`, `mistralai-mistral-small-4`. GREEN (live): backend restarted with `AI_EVALUATOR_MODEL=GPT-OSS-120B`, run requeued → `COMPLETED` (3 response evaluations); `docker compose config` → `AI_EVALUATOR_MODEL: GPT-OSS-120B`, `NIFTY_FRONTEND_BASE_URL: http://localhost:3000`
- Contracts/Migrations added: `NIFTY_FRONTEND_BASE_URL` in `docker-compose.yml` and `.env.example`
- Decisions: applies 1.13 (model IDs corrected, provider unchanged)
- Open Questions: a local `.env` copied from the old `.env.example` still sets the display name and must be edited by hand

### Task 3 (agent guard worktree paths)
- Commits: 1a96056 fix(harness): let the tests-only agent guard accept worktree paths
- Verification: RED: `node --test .claude/hooks/hooks.test.mjs` → FAIL (1: worktree test path blocked); GREEN: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0 (46 run); live hook allows `nifty-<change>/backend/src/test/**` and blocks `backend/src/main/**`
- Contracts/Migrations added: none
- Decisions: none
- Open Questions: none

