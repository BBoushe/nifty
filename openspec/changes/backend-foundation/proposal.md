# Proposal: Backend Foundation (`backend-foundation`)

## Context & Problem

The five teammate tickets (`docs/tickets/01..05`) need shared backend groundwork before they start. The shared interfaces of the contract pack do not exist in code. The Spring tests run on H2, which cannot run JSONB or the immutability triggers. The pom still uses the deprecated web starter.

## Scope

- US-BF-01: `spring-boot-starter-webmvc` and the Spring AI 2 model property.
- US-BF-02: Testcontainers PostgreSQL for every Spring test, base class `de.fhdortmund.nifty.PostgresContainerBase`, no H2 in tests.
- US-BF-03: `docker:dind` for the GitLab `backend` job.
- US-BF-04: `FieldValidationException` mapped to `400` ProblemDetail with `errors[]`.
- US-BF-05: `AttemptCompletedEvent` and `AssessmentDeletedEvent` in `shared/config`.
- US-BF-06: compile-only stubs of every contract-pack §3 interface, record and enum, one placeholder `@Service` per interface, and unmapped `RoleAccessTest` probes.

## Out of Scope

- Any real slice implementation (teammate tickets, `auth-accounts`, `audit-log-service`).
- Frontend work (`frontend-foundation`).
