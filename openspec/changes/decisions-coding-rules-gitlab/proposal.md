# Proposal: Traceable Decisions, Living Coding Standards & Parallel GitLab Support

## Context & Problem

Course grading criteria require strict traceability of engineering decisions (design, architecture, team process, and direction shifts), disciplined maintainable code (minimal footprint, DRY/ETC, hotspot performance, living API docs, feature-grouped UI views), and parallel **GitLab (`glab`)** readiness alongside our existing GitHub (`gh`) setup.

## Scope (3 Workstreams on Branch `feat/new-harness-tools` / PR #2)

1. **Workstream 1 — Traceable Decision Recording (`docs/decisions/` + Script + Linked Skills + Code Pointers):**
   - Replace flat `docs/decisions.md` (turning it into a redirect stub) with hierarchical `docs/decisions/` (`index.md`, `1-architecture.md`, `2-design.md`, `3-team-process.md`, `4-direction.md`) using `X.Y` IDs (`// Decision 1.2`).
   - Add zero-dependency `scripts/record-decision.mjs` for deterministic ID increment, category append, and `index.md` sync.
   - Link `question-round` -> `record-decision`, mandate automatic recording on **prompter changes/redirections**, and require `// Decision X.Y` pointers in code and specs.
2. **Workstream 2 — Centralized Living Coding Standards & API Documentation:**
   - Create canonical `.agents/rules/coding-standards.md` (`trigger: always_on`) with all 6 mandatory rules and cross-tool auto-discovery (`AGENTS.md`, `CLAUDE.md` `@` import, `spec-builder.md`, cold `reviewer.md`).
   - Create living `docs/api.md` synced with live `springdoc-openapi` (`/v3/api-docs`, `/swagger-ui.html`).
   - Configure the cold read-only `reviewer` to block on clear DRY/duplication, hotspot Stream misuse, missing `docs/api.md` updates, un-grouped bespoke Vue views, or undocumented direction shifts, while marking subjective style as Convention Candidates.
3. **Workstream 3 — Parallel GitLab (`glab`) Support via `.env` Switch & Preflight Check:**
   - Add `GIT_FORGE=github|gitlab` and `GITLAB_HOST` to `.env.example` (default `github`, keeping all GitHub workflows/rulesets intact).
   - Add zero-dependency `scripts/check-setup.mjs` (`--forge` safe non-secret reader + full setup preflight check) so agents never read `.env` secrets.
   - Add parity `.gitlab-ci.yml`, `scripts/harness-scripts.test.mjs` (`node:test`, wired into both GitHub and GitLab CI), forge-aware `pr-memo`, and updated docs (`CLAUDE.md`, `docs/workflow.md`, `docs/setup.md`, `README.md`).

## Out of Scope

- No removal of GitHub (`gh`, `.github/workflows/ci.yml`, `.github/rulesets/`).
- No weakening of `.env` secret blocking in `.claude/hooks/` or `.claude/settings.json`.
- No duplication of `.agents/rules/coding-standards.md` into `.claude/rules/` or `.kiro/steering/`.
- No changes to Java/Vue production source code.
