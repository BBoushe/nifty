# Proposal: Local Run Fixes (`local-run-fixes`)

## Context & Problem

The first manual run on the Docker stack (`localhost:3000`) found three problems:

1. Invite links point to `http://localhost:5173`. `docker-compose.yml` does not pass `NIFTY_FRONTEND_BASE_URL` to the backend, so the backend uses the Vite default.
2. Every evaluation run fails. ki:connect answers `404: The model 'OpenAI GPT OSS 120B' does not exist`. The config uses the display names, but the API wants the model IDs (`GET /models`): `GPT-OSS-120B`, `Qwen3.8 27B`, `mistralai-mistral-small-4`.
3. A reload sends a signed-in user to `/sign-in`. One browser holds one session. An invite link opened in the professor's browser turns that session into a candidate session, and the role guard then sends the professor tab to `/sign-in`.

## Scope

- Use the ki:connect model IDs in `application.yml`, `docker-compose.yml`, `.env.example`, `README.md` and the docs.
- Pass `NIFTY_FRONTEND_BASE_URL` to the backend in `docker-compose.yml` (default `http://localhost:3000`) and list it in `.env.example`.
- The route guard sends a signed-in user of the other role to that user's start page, not to `/sign-in` (REQ-NAV-09).
- `README.md`: test the candidate in a private window.
- Harness: the `tests-only` agent guard accepts paths inside a worktree of this repository.

## Out of Scope

- A re-run endpoint for evaluation runs that used all their tries (Decision 2.28 gap).
- Two sessions in one browser.
