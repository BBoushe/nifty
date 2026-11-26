# Tasks: Local Run Fixes (`local-run-fixes`)

## Status
- **Branch:** `fix/local-run-fixes` (branch off `dev`)
- **Worktree:** `../nifty-local-run-fixes`
- **Current:** in progress
- **Next action:** Task 1
- **Uncommitted changes:** yes
- **PR / MR:** —
- **Open review findings:** —

## Tasks

- [ ] **Task 1: Wrong-role route (REQ-NAV-09)**
  - [ ] RED: `frontend/src/views/auth/guard.test.ts` — candidate on `/ranking` goes to `/assessment`; professor on `/assessment` goes to `/ranking/studio`.
  - [ ] GREEN: `guard.ts` returns the role start page when a session of the other role exists.
  - [ ] Verify: `cd frontend && npm run test -- src/views/auth`.

- [ ] **Task 2: ki:connect model IDs and frontend base URL**
  - [ ] Evidence: `GET {AI_EVALUATOR_BASE_URL}/models` lists `GPT-OSS-120B`, `Qwen3.8 27B`, `mistralai-mistral-small-4`.
  - [ ] Change `application.yml`, `docker-compose.yml`, `.env.example`, `README.md`, `docs/architecture.md`, `docs/ai_docs/spring-ai-2-kiconnect.md`.
  - [ ] Pass `NIFTY_FRONTEND_BASE_URL` in `docker-compose.yml`; add it to `.env.example`.
  - [ ] Verify: `docker compose config` shows both values; `cd backend && ./mvnw -B test`.

- [ ] **Task 3: Agent guard accepts worktree paths (harness)**
  - [ ] RED: `.claude/hooks/hooks.test.mjs` "guard-agent.mjs worktrees" — a test path in a worktree of this repo is blocked.
  - [ ] GREEN: `guard-agent.mjs` measures an absolute path from its own worktree root when that worktree belongs to this repo.
  - [ ] Verify: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`.

## Delivery

- [ ] `git fetch origin && git merge origin/dev`
- [ ] Full checks, archive, `docs/feature_history.md`, push, PR/MR, review, human merge approval, `forge.mjs sync dev`

## Handoff Log
