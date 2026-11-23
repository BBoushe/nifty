# Delta Spec: Traceable Decisions, Living Coding Standards & Parallel GitLab Support

## ADDED Requirements

### Requirement: REQ-DCR-01 Hierarchical Traceable Decision Registry & Script
**THE SYSTEM SHALL** store architectural, design, team-process, and product-direction decisions under `docs/decisions/` (`index.md`, `1-architecture.md`, `2-design.md`, `3-team-process.md`, `4-direction.md`) using hierarchical `X.Y` IDs and provide zero-dependency `scripts/record-decision.mjs` to append structured entries and update `index.md` deterministically.

#### Scenario: Existing D1-D14 and O1-O7 decisions migrate cleanly with backwards-compatible links
- **WHEN** a contributor or agent opens `docs/decisions.md` or `docs/decisions/index.md`
- **THEN** `docs/decisions.md` redirects cleanly to `docs/decisions/index.md`
- **AND** all prior `D1`–`D14` decisions and `O1`–`O7` open decisions appear in `docs/decisions/` with hierarchical `X.Y` IDs and preserved legacy ID aliases.

#### Scenario: Recording a new decision increments X.Y IDs and updates index deterministically
- **WHEN** `node scripts/record-decision.mjs --category 2 --topic "..." --trigger prompter-change ...` is run
- **THEN** the script computes the next sequential `2.<n>` ID, appends the structured entry to `docs/decisions/2-design.md`, adds a row to `docs/decisions/index.md`, and exits with code `0`.

### Requirement: REQ-DCR-02 Linked Decision Triggers & Code Pointers
**WHEN** a `question-round` is answered OR the prompter changes, redirects, or overrides a design, architecture, coding, team, or direction choice **THE SYSTEM SHALL** instruct agents via `record-decision`, `question-round`, `AGENTS.md`, and `spec-builder.md` to record a `docs/decisions/` entry and cite `// Decision X.Y` pointers in code and specs.

#### Scenario: Cold reviewer flags undocumented architectural or direction shifts
- **WHEN** the cold read-only `reviewer` audits `git diff dev...HEAD`
- **THEN** it verifies that non-obvious architectural/design choices cite a valid `docs/decisions/` ID and flags undocumented direction shifts.

### Requirement: REQ-DCR-03 Centralized Living Coding Standards & API Documentation
**THE SYSTEM SHALL** maintain `.agents/rules/coding-standards.md` (`trigger: always_on`) as the single canonical home for team coding rules (ETC/DRY, small testable functions, no Streams in hot paths, living `docs/api.md`, minimal reusable classes, frontend reuse/HOFs/feature-grouped views) loaded across all AI tools without duplication, alongside living `docs/api.md`.

#### Scenario: All harnesses discover coding-standards.md without rule duplication
- **WHEN** Antigravity, Claude Code, Codex, Kiro Crew, or T3 Code loads the repo
- **THEN** `.agents/rules/coding-standards.md` is loaded via `trigger: always_on` (Antigravity), `@.agents/rules/coding-standards.md` import in `CLAUDE.md` + agent prompts (Claude Code), and `AGENTS.md` (Codex/Kiro/T3) with zero copies in `.claude/rules/`.

#### Scenario: Cold reviewer enforces blocking vs. non-blocking coding rules
- **WHEN** `.claude/agents/reviewer.md` audits a diff
- **THEN** it blocks on clear DRY/duplicate classes, Java Streams in hot-path shared functions, API changes missing `docs/api.md` updates, or un-grouped bespoke Vue views, while placing subjective function-splitting/HOF suggestions under `Convention Candidates`.

### Requirement: REQ-DCR-04 Parallel GitLab Support & Secret-Safe Forge Switch
**THE SYSTEM SHALL** support switching between GitHub (`gh`) and GitLab (`glab`) via `GIT_FORGE=github|gitlab` and `GITLAB_HOST` in `.env` / `.env.example`, provide `scripts/check-setup.mjs` to read only non-secret forge settings and verify setup health without ever exposing `.env` secrets, and maintain parity `.gitlab-ci.yml`.

#### Scenario: Forge helper reads GIT_FORGE without leaking secrets
- **WHEN** `node scripts/check-setup.mjs --forge` runs against a `.env` file containing `GIT_FORGE=gitlab`, `KICONNECT_API_KEY`, and `POSTGRES_PASSWORD`
- **THEN** stdout contains only `GIT_FORGE`, `GITLAB_HOST`, and `CLI=glab`
- **AND** zero secret values appear in stdout or stderr.

#### Scenario: Parity GitLab CI pipeline and forge-aware pr-memo work alongside GitHub
- **WHEN** `.gitlab-ci.yml` and `.agents/skills/pr-memo/SKILL.md` are inspected
- **THEN** `.gitlab-ci.yml` mirrors all 5 CI jobs (`backend`, `frontend`, `harness-tests`, `conventional-commits`, `branch-policy`) while `.github/workflows/ci.yml` remains intact, and `pr-memo` adapts PR (`gh`) vs. MR (`glab`) output using `check-setup.mjs --forge`.
