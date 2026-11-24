# Design: AI Context Docs & ASD-STE100

## 1. `docs/feature_history.md`

```markdown
# Feature History

Newest first. One entry per folder in `openspec/changes/archive/` (enforced by `scripts/harness-scripts.test.mjs`).

## 2026-11-24 — docker-and-kiconnect
- **What:** …  (1–3 STE sentences)
- **Why:** …
- **PR/MR:** GitHub #…, GitLab !…  (or "direct commits" with SHAs)
- **Specs:** REQ-…   **Decisions:** 1.6, …
```

The heading text after the em dash is the archive folder name without its date prefix. The test matches `^## \d{4}-\d{2}-\d{2} — <name>$` for each `openspec/changes/archive/<date>-<name>/`. Backfill the 4 changes archived on 2026-11-24 from their `proposal.md`, `tasks.md` Handoff Log and `git log`. Before opening this PR, rebase and backfill any change archived since then.

## 2. History Test (`scripts/harness-scripts.test.mjs`, new `describe('feature history')`)

`it('has a history entry for every archived change')`: list the directories in `openspec/changes/archive/` (ignore `.gitkeep`), strip the `YYYY-MM-DD-` prefix, and assert that a matching heading exists. On failure, name every missing folder. Keep the helper local to the test file.

## 3. `docs/feature_backlog.md`

| # | Feature (spec) | Status | Slice (Feature Slice Map) | Blocked by | Depends on | Migrations |
|---|---|---|---|---|---|---|

- Rows: `docs-ste-rewrite` (first, queued until the harness changes merge), then the 7 domain specs in pipeline order (`interviewer-studio` → `assessment-chat` → `evidence-pipeline` → `formula-engine` → `skills-matrix-review` → `candidate-results` → `team-builder`), using the open O1–O7 items from `docs/decisions/index.md` as blockers.
- Add a note under `assessment-chat`: the first `@Entity` (JSONB transcript) must add PostgreSQL Testcontainers for `@DataJpaTest`, plus the GitLab dind service config in CI (Decision 1.10).
- Link the slice column to `docs/architecture.md#feature-slice-map` (owned by `backend-architecture-baseline`; if that change has not merged yet, the link target arrives with it).
- Lifecycle (put in AGENTS.md): proposing a change sets its row to `in progress` with the change name. Archiving removes the row and adds a history entry.

## 4. `docs/ai_docs/`

- `README.md`: purpose (current API facts for library versions newer than model training); rules:
  - one file per library;
  - each file starts with `Version:`, `Sources:` (official URLs), `Retrieved:` (date);
  - ≤150 lines; STE prose; only facts this project needs;
  - refresh the note in the same PR that bumps the library.
- Initial notes. Research them from primary sources with the `mattpocock-skills:research` skill, and check versions against `backend/pom.xml`:
  - `spring-boot-4.md` (4.0.x): modular test starters (`spring-boot-starter-webmvc-test`), `@MockitoBean` replacing `@MockBean`, `ProblemDetail` / `ResponseEntityExceptionHandler`, Testcontainers `@ServiceConnection`, Jackson 3 notes.
  - `spring-ai-2-kiconnect.md` (2.0.x): `ChatClient` fluent API, structured output (`.entity(...)`), OpenAI-compatible `base-url` for ki:connect NRW and its model names (from `application.yml`), mocking `ChatModel` in tests.
  - `timefold-solver.md` (2.8.x): planning entity/solution annotations, `ConstraintProvider`, `ConstraintVerifier` tests, solver config for small fixed-time runs.

## 5. ASD-STE100 Skill

1. Fetch the skill source into the scratchpad first (`git clone` or the npm tarball), **not** into the repo. Read every file. Reject it (and fall back to a written rule only) if it makes network calls at runtime, runs install or postinstall scripts, or ships binaries.
2. If it passes: `npx skills add danyuchn/asd-ste100-skill -a claude-code`. Move the installed folder to `.agents/skills/asd-ste100/` and symlink it from `.claude/skills/asd-ste100` (house pattern, like `pr-memo`).
3. Run its linter on one existing doc and note the command in the skill README section of `docs/setup.md`.

## 6. Rules

- `.agents/rules/spec-driven.md`: new rule 6, "Plain Writing (Decision 3.5)". New or changed spec and doc prose follows ASD-STE100: sentences ≤20 words for instructions and ≤25 for descriptions, active voice, one instruction per sentence, defined terms. EARS keywords stay as they are. Do not rewrite unchanged text. Reviewer level: Convention Candidate.
- `AGENTS.md` §3 dispatch table, new rows:
  - "Using a library API from the pinned stack" → `docs/ai_docs/<lib>.md`;
  - "Proposing or archiving an OpenSpec change" → `docs/feature_backlog.md` + `docs/feature_history.md` lifecycle;
  - "Writing or editing docs/specs" → `asd-ste100` skill.
