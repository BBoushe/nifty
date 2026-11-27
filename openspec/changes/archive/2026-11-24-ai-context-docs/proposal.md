# Proposal: AI Context Docs & ASD-STE100

## Context & Problem

The course toolkit names three context files (`CLAUDE.md`, `architecture.md`, `features_history.md`) plus a backlog and `ai_docs/`. We already have `CLAUDE.md`, `AGENTS.md`, `docs/architecture.md`, `CHANGELOG.md` and the decision registry. Three gaps remain:

1. **No feature history.** `CHANGELOG.md` holds release notes, not "what was built, why, and where". Before 2026-11-24, finished OpenSpec changes stayed active, so even the archive told nothing.
2. **No backlog.** The 7 domain specs and the planned modules in `docs/api.md` §5 have no order, status, dependencies or blast radius. Agents and humans cannot see what to pick next, or what can run in parallel.
3. **No current library docs.** Spring Boot 4, Spring AI 2.0 and Timefold 2 are newer than most models' training data. Agents guess APIs (for example, the removed `@MockBean`).

ASD-STE100 (Simplified Technical English) makes new docs short and unambiguous, so they cost fewer tokens when agents read them later. Caveman is rejected (Decision 3.5).

Decisions 3.5 and 3.6 record this. Source: `docs/prompts/done/harness_upgrade.md`.

## Scope

- `docs/feature_history.md`, backfilled, with a harness test that keeps it complete.
- `docs/feature_backlog.md`.
- `docs/ai_docs/` with a README and three library notes.
- The vetted `asd-ste100` skill and an STE rule for new or changed prose.
- AGENTS.md dispatch rows for the new files and their lifecycle.

## Out of Scope

- Rewriting existing docs in STE (queued as `docs-ste-rewrite`, after all parallel changes merge).
- Frontend library notes (they wait for open decision O1, the UI kit).
- A CI linter job for STE (the Convention Candidate level does not justify a CI gate yet).
