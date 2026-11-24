# Proposal: Fix Docker Build Timeout & Configure ki:connect NRW Evaluator LLM (`docker-and-kiconnect`)

## Problem & Motivation

1. **Docker Frontend Build Failure (`ETIMEDOUT`):** Running `docker compose up --build` fails during `[frontend build 1/3] RUN npm ci` after ~186 seconds with `ETIMEDOUT` downloading `zrender-5.6.1.tgz`. High concurrent socket usage and IPv6 resolution inside Docker Desktop on macOS cause npm registry downloads to stall, and `backend/Dockerfile` also lacks BuildKit dependency caching (414 s rebuilds).
2. **EU LLM Evaluator Provider Decided (`ki:connect` / FH AI):** The team decided to use **ki:connect NRW** (FH AI — hosted in Aachen / North Rhine-Westphalia, GDPR-compliant, free for students) specifically for the **LLM evaluator** (evidence extraction and constrained level-boundary judging).
   - Endpoint: `https://chat.kiconnect.nrw/api/v1`
   - Available models: `OpenAI GPT OSS 120B`, `Qwen 3.8 27B`, `Mistral Small 4 119B`

## Scope (In Scope)

- Fix `frontend/Dockerfile` (and harden `backend/Dockerfile`) to prevent `ETIMEDOUT` stalls using IPv4-first DNS, resilient npm retry/socket flags, and BuildKit cache mounts, and verify a real `docker compose build` succeeds cleanly.
- Add dedicated **evaluator** LLM environment variables (`KICONNECT_API_KEY`, `AI_EVALUATOR_BASE_URL`, `AI_EVALUATOR_MODEL`, plus clear model list comments) to `.env.example`, create local untracked `.env` ready for the user's API key, and wire them through `docker-compose.yml` and `backend/src/main/resources/application.yml` (booting cleanly even before a key is filled in).
- Update `docs/decisions.md` (resolve O2 for evaluator LLM), `docs/architecture.md`, `README.md`, `CHANGELOG.md`, and `.claude/skills/backend-spring-check/SKILL.md`.
- Keep all changes local on `feat/setup` until the user confirms everything looks good, then push and update PR #1.

## Out of Scope

- Implementing live LLM calls in the evidence pipeline or interviewer modules (separate feature changes).
- Committing `.env` or any real API key to Git.
