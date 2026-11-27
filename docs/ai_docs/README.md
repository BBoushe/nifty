# AI Docs — Current Library Notes

Version: index (no library)
Sources: `backend/pom.xml`, `frontend/package.json`
Retrieved: 2026-11-24

Some pinned libraries are newer than the training data of most AI models. Agents then guess APIs that no longer exist (for example, `@MockBean` in Spring Boot 4). These notes give the current facts that this project needs.

Read the matching note before you use a library API from the pinned stack (`AGENTS.md` §3).

## Notes

| Note | Library | Pinned in |
|---|---|---|
| [`spring-boot-4.md`](spring-boot-4.md) | Spring Boot 4.0.x (tests, errors, Testcontainers, Jackson 3) | `backend/pom.xml` |
| [`spring-ai-2-kiconnect.md`](spring-ai-2-kiconnect.md) | Spring AI 2.0.x with ki:connect NRW | `backend/pom.xml` |

Frontend notes follow the UI kit decision: PrimeVue (Decision 1.14).

## Rules

1. Write one file per library.
2. Start each file with three lines: `Version:`, `Sources:` (official URLs only) and `Retrieved:` (date).
3. Keep each file at 150 lines or fewer.
4. Write the prose in ASD-STE100 (see `.agents/rules/spec-driven.md` rule 6).
5. Add only facts that this project needs. Cite a primary source for each fact.
6. When a PR changes the major or minor version of a library, refresh its note in the same PR.
