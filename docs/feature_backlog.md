# Feature Backlog

Planned features in pick order. Use this table to choose the next change and to find work that can run in parallel.

- **Status:** `queued` (you can start it), `in progress` (a change exists, its name is in the Feature column), `blocked` (an open decision in "Blocked by" must close first).
- **Blocked by:** open items from [`docs/decisions/index.md`](decisions/index.md). Close them with the `question-round` skill.
- **Slice:** the backend package and frontend views from the [Feature Slice Map](architecture.md#feature-slice-map). Two rows with different slices and no dependency between them can run in parallel.
- **Migrations:** each new table comes as a new Flyway file after `V1__init.sql`. Two parallel changes must not use the same `V<n>` number.

Lifecycle (see `AGENTS.md` §3): when you propose a change, set its row to `in progress` and add the change name. When you archive the change, remove its row and add an entry to [`feature_history.md`](feature_history.md).

| # | Feature (spec) | Status | Slice (Feature Slice Map) | Blocked by | Depends on | Migrations |
|---|---|---|---|---|---|---|
| 1 | [`interviewer-studio`](../openspec/specs/interviewer-studio/spec.md) | blocked | `studio`, `views/studio/` | O1, O3 | — | interviewer config (versioned), skill/tag taxonomy |
| 2 | [`assessment-chat`](../openspec/specs/assessment-chat/spec.md) | blocked | `assessment`, `views/assessment/` | O1, O2, O7 | 1 (approved config) | session, `assessment-transcript/v1` (JSONB) |
| 3 | [`evidence-pipeline`](../openspec/specs/evidence-pipeline/spec.md) | queued | `evidence` (backend only) | — | 2 (transcript); can start on synthetic transcripts | evidence, quote verification result |
| 4 | [`formula-engine`](../openspec/specs/formula-engine/spec.md) | blocked | `formula`, `views/ranking/` (formula editor) | O1, O5 | 1 (skills and weights) | formula preset |
| 5 | [`skills-matrix-review`](../openspec/specs/skills-matrix-review/spec.md) | blocked | `matrix`, `views/ranking/` | O1 | 3, 4 | score, override, audit log (immutable) |
| 6 | [`candidate-results`](../openspec/specs/candidate-results/spec.md) | blocked | `results`, `views/assessment/` (results page) | O1, O6 | 5 (approved scores) | dispute |
| 7 | [`team-builder`](../openspec/specs/team-builder/spec.md) | blocked | `teams`, `views/teams/` | O1, O4 | 5 (approved scores) | team proposal, placement sign-off |
| 8 | `harness-review-followups` (no change yet; items in Notes) | queued | — (harness, CI, `shared`) | — | — | none |

## Notes

- **`assessment-chat`:** the first `@Entity` (the JSONB transcript) must add PostgreSQL Testcontainers for `@DataJpaTest`, because H2 cannot run JSONB. The same change must add the Docker-in-Docker service to GitLab CI so these tests run there (Decision 1.10).
- **Shared `views/ranking/`:** rows 4 and 5 use the same frontend folder. If they run in parallel, agree on file ownership first.
- **Shared `views/assessment/`:** rows 2 and 6 use the same frontend folder. Row 6 starts after row 5, so they do not overlap in practice.
- **`harness-review-followups`:** non-blocking findings from the 2026-11-24 harness reviews (see the archived `tasks.md` Status blocks):
  - CI drift test (`scripts/harness-scripts.test.mjs`): it ignores other toolchains (pnpm, `docker compose build`) and skips multi-line `run: |` blocks.
  - `ArchitectureTest`: the composite sub-rules (layout packages/persistence/services, repository→service/web) have no violating test classes of their own.
  - `GlobalExceptionHandler`: exceptions marked `@ResponseStatus` become 500. Use `DomainException`, or let the handler honour the annotation.
  - Bash guard (third review of PR #13, gaps older than that PR): quoted redirection targets (`cat <".env"`, `$(<'.env')`) and `git commit -F .env` are allowed; `git commit -m x <path>` stages a path without a separate `git add`.
  - Docs: `docs/workflow.md` Handoff step lacks the RED/GREEN Verification format; the `docs/setup.md` `.githooks/` row omits the archive gate; the Delivery template runs tests before the archive step.
