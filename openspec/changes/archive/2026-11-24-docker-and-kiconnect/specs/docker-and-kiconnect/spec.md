# Delta Spec: Docker Build Reliability & ki:connect NRW Evaluator Configuration

## ADDED Requirements

### Requirement: REQ-DK-01 Resilient Docker Frontend & Backend Builds
**WHEN** a developer runs `docker compose build` on macOS, Windows, or Linux **THE SYSTEM SHALL** resolve npm and Maven registries using IPv4-first networking with bounded concurrency, retries, and BuildKit cache mounts so `frontend` and `backend` images build reliably without `ETIMEDOUT` stalls.

#### Scenario: Docker Compose builds both backend and frontend cleanly
- **WHEN** `docker compose build` is executed from the repository root
- **THEN** both `backend` and `frontend` images build and exit with code `0` without npm `ETIMEDOUT` failures.

### Requirement: REQ-DK-02 ki:connect NRW Evaluator Environment Configuration
**THE SYSTEM SHALL** expose dedicated environment variables for the **ki:connect NRW / FH AI** evaluator endpoint (`https://chat.kiconnect.nrw/api/v1`), API key (`KICONNECT_API_KEY`), and evaluator model selection (`OpenAI GPT OSS 120B`, `Qwen 3.8 27B`, `Mistral Small 4 119B`) in `.env.example`, `docker-compose.yml`, and `backend/src/main/resources/application.yml`.

#### Scenario: Backend boots cleanly with placeholder ki:connect key
- **WHEN** `KICONNECT_API_KEY` is unset or left at its placeholder value
- **THEN** the Spring Boot backend starts and passes `./mvnw test` without making external network calls or failing startup.

#### Scenario: Evaluator scope stays documented and isolated from secrets
- **WHEN** a developer copies `.env.example` to `.env` and inserts their personal `ki:connect` API key
- **THEN** `.env` remains untracked by Git and documentation explicitly notes that `ki:connect` is configured for the LLM evaluator using EU-hosted (Aachen / NRW) infrastructure.
