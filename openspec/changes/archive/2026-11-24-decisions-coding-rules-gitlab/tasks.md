# Tasks: Traceable Decisions, Living Coding Standards & Parallel GitLab Support

Execute incrementally on branch `feat/new-harness-tools` (updating PR #2 into `dev`) with atomic Conventional Commits. Check off items only after verifying real command exit codes.

## Phase 0 — OpenSpec Change
- [x] Create `openspec/changes/decisions-coding-rules-gitlab/` (`proposal.md`, `specs/`, `design.md`, `tasks.md`, `.openspec.yaml`)
- [x] Verify `OPENSPEC_TELEMETRY=0 openspec validate --all` exits `0` with 0 warnings

## Workstream 1 — Traceable Decision Recording (`docs/decisions/` + Script + Linked Skills + Code Pointers)
- [x] Create `docs/decisions/{index,1-architecture,2-design,3-team-process,4-direction}.md`, migrate `D1`–`D14` and `O1`–`O7`, and convert `docs/decisions.md` into a concise redirect stub
- [x] Create zero-dependency `scripts/record-decision.mjs` (hierarchical `X.Y` IDs, category append, `index.md` sync, `--resolves`, `--dry-run`, `--root`)
- [x] Update `.agents/skills/record-decision/SKILL.md` and `.agents/skills/question-round/SKILL.md` (link skills, prompter-change trigger, script usage)
- [x] Add `// Decision X.Y` code-pointer & prompter-change rules to `AGENTS.md`, `.claude/agents/spec-builder.md`, and `.claude/agents/reviewer.md`

## Workstream 2 — Centralized Living Coding Standards & API Documentation
- [x] Create canonical `.agents/rules/coding-standards.md` (`trigger: always_on`) with all 6 mandatory rules + living rule growth protocol
- [x] Create living `docs/api.md` synced with live `/api/v1/health`, `/actuator/health`, and `springdoc-openapi`
- [x] Wire cross-tool discovery (`AGENTS.md`, `CLAUDE.md` `@` import, `.agents/rules/{backend-java,frontend-vue}.md`, `.claude/agents/{spec-builder,reviewer}.md`) with zero duplication

## Workstream 3 — Parallel GitLab (`glab`) Support via `.env` Switch & Preflight Check
- [x] Add `GIT_FORGE=github` and `GITLAB_HOST=gitlab.com` to `.env.example`
- [x] Create zero-dependency `scripts/check-setup.mjs` (`--forge` non-secret reader + full setup preflight check)
- [x] Create `scripts/harness-scripts.test.mjs` (`node:test`) testing `record-decision.mjs` and `check-setup.mjs` (including strict secret non-leak verification)
- [x] Create parity `.gitlab-ci.yml` mirroring all 5 CI jobs and update `.github/workflows/ci.yml` to run `scripts/harness-scripts.test.mjs`
- [x] Update `.claude/settings.json` (`permissions.allow`), `.agents/skills/pr-memo/SKILL.md` (forge-aware PR/MR), `CLAUDE.md`, `docs/workflow.md`, `docs/setup.md`, `README.md`, and `CHANGELOG.md`

## Final Verification, Cold Review & PR #2 Update
- [x] Run `OPENSPEC_TELEMETRY=0 openspec validate --all` -> exit `0`
- [x] Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` -> exit `0`
- [x] Run `node scripts/check-setup.mjs --forge` and verify `.gitlab-ci.yml` valid YAML & `.claude/settings.json` valid JSON
- [x] Run `cd backend && ./mvnw -o test` and `cd frontend && npm run typecheck && npm run lint && npm run build` -> exit `0`
- [x] Run cold read-only `reviewer` audit on `git diff dev...HEAD` -> `Verdict: APPROVE`
- [x] Push commits to `feat/new-harness-tools`, update PR #2 description via `pr-memo`, and record final `## Handoff Log`

## Handoff Log

- **Commits:** `c036fa7` (`chore(specs)`), `34e510c` (`feat(decisions)`), `6c22e63` (`feat(rules)`), `6b0a873` (`feat(forge)`), plus cold-review fix commit (`fix(harness)`).
- **Verification:** `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` (20/20 pass), `OPENSPEC_TELEMETRY=0 openspec validate --all` (11/11 pass, 0 warnings), `node scripts/check-setup.mjs` (0 failures), `.gitlab-ci.yml` YAML valid, `cd backend && ./mvnw -o test` (2/2 pass), `cd frontend && npm run typecheck && npm run lint && npm run build` (exit 0).
- **Contracts/Migrations added:** `docs/decisions/{index,1-architecture,2-design,3-team-process,4-direction}.md`, `scripts/record-decision.mjs`, `scripts/check-setup.mjs`, `scripts/harness-scripts.test.mjs`, `.agents/rules/coding-standards.md`, `docs/api.md`, `.gitlab-ci.yml`.
- **Decisions:** `1.7` (parallel GitHub/GitLab `.env` switch), `2.9` (living `docs/api.md`), `3.1`–`3.3` (cold review, canonical living coding standards, mandatory decision recording & code pointers).
- **Open Questions:** `O1`–`O7` migrated cleanly into `docs/decisions/index.md` for Day-1 team resolution.
