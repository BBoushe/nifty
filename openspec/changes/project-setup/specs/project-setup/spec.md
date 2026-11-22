# Delta Spec: Project Setup (`project-setup`)

## ADDED Requirements

### Requirement: Repository Multi-Agent & Full-Stack Foundation
**THE SYSTEM SHALL** provide a tool-agnostic, spec-driven monorepo (`backend/`, `frontend/`, `openspec/`, `docs/`, `.agents/`, `.claude/`, `.githooks/`, `.github/`, `docker-compose.yml`, `.env.example`) verified with real Maven, npm, OpenSpec, and Docker Compose checks before merging into `dev`.

#### Scenario: Full project setup verification passes
- **WHEN** a contributor clones the repository and runs backend tests, frontend typecheck/lint/build, `openspec validate --all`, and `docker compose config`
- **THEN** all checks exit with code `0` without harness lock-in or committed secrets.
