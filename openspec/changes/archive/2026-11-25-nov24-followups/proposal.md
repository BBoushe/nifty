# Proposal: Follow-ups to the Nov 24 Reconciliation (`nov24-followups`)

## Context & Problem

After the `nov24-reconciliation` merge, the prompter settled four items:

- An answer submitted at timeout is evaluated, because no static check can tell a partial answer from a complete one (Decision 2.20). The specs still make every timed-out question `N/A`.
- The system sets no cost limit per interview (Decision 4.5). The canvas still lists it as open.
- The professor does all review work, so the `REVIEWER` role goes now. `Role.java` and `SecurityConfig` still have it (Decision 1.15, REQ-AA-01).
- The last Delivery checkboxes of the archived `nov24-reconciliation` change stay open until a later change.

The prompter also asked to check the Spring Boot 4 / Spring AI 2 dependency names in backlog story US-BF-01..04. All of them exist in Maven Central at the pinned versions, and `docs/ai_docs/` cites their sources.

## Scope

- Delta specs: REQ-AC-08, REQ-EP-06 (renamed "Unanswered Questions Produce N/A"), REQ-FE-03.
- Code: `Role` has only `PROFESSOR` and `CANDIDATE`. `SecurityConfig` allows `/api/v1/ranking/**` only for `PROFESSOR` and `/api/v1/assessment/**` only for `CANDIDATE`. New test dependency `spring-boot-starter-security-test` and `RoleAccessTest`.
- Docs: `docs/architecture.md`, `docs/api.md`, `docs/discovery-canvas.md`, `docs/feature_backlog.md`, the 2026-11-25 AI log, `CHANGELOG.md`.
- Close the Delivery checkboxes in `openspec/changes/archive/2026-11-25-nov24-reconciliation/tasks.md`.

## Out of Scope

- Professor accounts and sign-in (backlog row 3, US-AU-02..03). This change does only the role and route part of US-AU-01.
- The wave-0 dependency changes themselves (backlog rows 1 and 2).
