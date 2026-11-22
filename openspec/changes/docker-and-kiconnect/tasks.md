# Tasks: Docker Build Reliability & ki:connect NRW Evaluator (`docker-and-kiconnect`)

- [x] 1.1 Create OpenSpec change `openspec/changes/docker-and-kiconnect/` (`proposal.md`, `specs/docker-and-kiconnect/spec.md`, `design.md`, `tasks.md`)
- [x] 1.2 Fix `frontend/Dockerfile` (`NODE_OPTIONS=--dns-result-order=ipv4first`, resilient `npm ci` retry/socket flags, BuildKit cache mount) and harden `backend/Dockerfile` (`preferIPv4Stack`, BuildKit `.m2` cache)
- [x] 1.3 Configure ki:connect NRW (`https://chat.kiconnect.nrw/api/v1`) evaluator variables (`KICONNECT_API_KEY`, `AI_EVALUATOR_BASE_URL`, `AI_EVALUATOR_MODEL`) in `.env.example`, local untracked `.env`, `docker-compose.yml`, and `backend/src/main/resources/application.yml`
- [x] 1.4 Update `docs/decisions.md`, `docs/architecture.md`, `README.md`, `CHANGELOG.md`, and `.claude/skills/backend-spring-check/SKILL.md`
- [x] 1.5 Verify with real commands: `docker compose build`, `docker compose config --quiet`, `cd backend && ./mvnw -o test`, `OPENSPEC_TELEMETRY=0 openspec validate --all`, and confirm `.env` is ignored by Git
- [x] 1.6 Wait for user confirmation before committing/pushing to `feat/setup` and updating PR #1
