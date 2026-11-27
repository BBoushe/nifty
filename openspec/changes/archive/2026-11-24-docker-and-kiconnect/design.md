# Design: Docker Build Reliability & ki:connect NRW Evaluator (`docker-and-kiconnect`)

## 1. Root Cause & Fix for Docker `npm ci` `ETIMEDOUT`

During `docker compose up --build` on macOS, `RUN npm ci` in `frontend/Dockerfile` stalled at ~186 s downloading `zrender-5.6.1.tgz` (`ETIMEDOUT`).

**Mitigations applied:**
1. **`frontend/Dockerfile`:**
   - Set `ENV NODE_OPTIONS="--dns-result-order=ipv4first"` so Node 22 prefers IPv4 inside Docker Desktop's Linux VM networking stack.
   - Configure npm fetch resilience flags on `npm ci` / `npm install`: `--maxsockets=4 --fetch-retries=5 --fetch-retry-mintimeout=20000 --fetch-retry-maxtimeout=120000 --fetch-timeout=120000 --prefer-offline --no-audit --no-fund`.
   - Attach a BuildKit cache mount `--mount=type=cache,target=/root/.npm` so cached tarballs persist across rebuilds.
2. **`backend/Dockerfile`:**
   - Set `ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"` and `-Dmaven.resolver.transport=wagon` with `--mount=type=cache,target=/root/.m2` to prevent identical IPv6 resolver stalls and cut subsequent rebuild times dramatically.

## 2. ki:connect NRW (FH AI) Evaluator Configuration

- **Scope:** Strictly for the **LLM evaluator** (Pipeline Steps 2 & 4: structured evidence extraction and constrained rubric judging — never totals/rankings).
- **Provider & Hosting:** **ki:connect NRW** (FH AI), hosted in **Aachen / North Rhine-Westphalia (EU / GDPR-compliant)**, free for students.
- **Endpoint:** `https://chat.kiconnect.nrw/api/v1` (OpenAI-compatible; Spring AI 2.0.0's official `openai-java` SDK accepts `/v1` base URLs directly).
- **Supported Models:**
  - `OpenAI GPT OSS 120B`
  - `Qwen 3.8 27B`
  - `Mistral Small 4 119B`
- **Environment Contract (`.env.example` & local untracked `.env`):**
  - `KICONNECT_API_KEY` (empty/placeholder by default; falls back to `not-configured` in `application.yml` so Spring Boot boots cleanly without an external key)
  - `AI_EVALUATOR_PROVIDER=kiconnect-nrw`
  - `AI_EVALUATOR_BASE_URL=https://chat.kiconnect.nrw/api/v1`
  - `AI_EVALUATOR_MODEL=OpenAI GPT OSS 120B`
- **Verification:** Run `docker compose build`, `cd backend && ./mvnw -o test`, `openspec validate --all`, and `git status` (confirming `.env` is ignored and never staged).
