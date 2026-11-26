# Proposal: Studio Assessment Creation (`studio-assessment-creation`)

## Summary

Implement the `studio` vertical slice (`de.fhdortmund.nifty.studio` + `frontend/src/views/studio/`) for one-shot immutable assessment creation (Decision 2.21). Professors fill out settings, skills with 5-level hidden rubrics, weighted core questions, and grouping knobs in a browser `localStorage` form, then submit once with an `Idempotency-Key` header.

## Scope

- Flyway `V3__studio.sql` (`assessment_config`, `skill`, `core_question`, `core_question_skill`, plus PostgreSQL immutability triggers honoring `nifty.allow_purge = 'on'`).
- Private JPA entities and repositories strictly inside `studio/repository/` (ArchUnit Decision 1.10).
- `StudioQueryService` narrow read-only hand-off views (`exists`, `interviewerView`, `evaluatorView`, `scoringView`, `teamView`) with rubric-leakage reflection guard (Decision 1.19).
- `PROFESSOR`-only endpoints under `/api/v1/ranking/studio/configs/**` (`POST` with `Idempotency-Key`, `GET` list, `GET` detail, `DELETE` GDPR purge publishing `AssessmentDeletedEvent`; `PUT`/`PATCH` return `405`).
- Vue 3.5 + PrimeVue form (`SettingsSection`, `SkillsSection`, `QuestionsSection`, `ReviewStep`, `AssessmentList`, `AssessmentDetail`) in `frontend/src/views/studio/`.

## Out of Scope

- Backend draft/edit/version endpoints and "Duplicate as new" (Post-MVP, Decision 2.21).
- `SKILL.weight` column (removed by Decision 2.22).
- Invite link creation (owned by `auth` in `V4__auth_invites.sql`).
- LLM-generated core questions (Post-MVP, Decision 4.2).
