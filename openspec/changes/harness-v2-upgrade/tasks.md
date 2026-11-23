# Tasks: Harness v2 Upgrade

Execute incrementally on branch `feat/new-harness-tools` (branched off `dev`) with atomic Conventional Commits. Check off items only after verifying real command exit codes.

## Phase 0 — OpenSpec Change & Validation
- [x] Create `openspec/changes/harness-v2-upgrade/` (`proposal.md`, `specs/harness-v2-upgrade/spec.md`, `design.md`, `tasks.md`, `.openspec.yaml`)
- [x] Verify `OPENSPEC_TELEMETRY=0 openspec validate --all` exits `0`

## Workstream 1 — Cross-Platform Node `.mjs` Hooks, Permissions & `.githooks/` Bridge
- [x] Create `.claude/hooks/lib.mjs`, `guard-files.mjs`, `guard-bash.mjs`, `guard-agent.mjs`, and `test-gate.mjs`, and remove obsolete `.claude/hooks/pre-tool-guard.sh` and `post-tool-validate.sh`
- [x] Create `.claude/hooks/hooks.test.mjs` (`node:test`) covering `.env` blocks, `.env.example` allow, chained bash commands, secret-in-diff scanning, protected branches, and `guard-agent.mjs` modes
- [x] Update `.claude/settings.json` with `permissions.allow`, `permissions.deny`, and `PreToolUse` `.mjs` hooks; validate JSON
- [x] Add `.githooks/pre-commit` (chmod `+x`) delegating to `guard-bash.mjs --pre-commit` and integrate `test-gate.mjs` into `.githooks/pre-push`
- [x] Add `Hook Tests` (`node --test .claude/hooks/hooks.test.mjs`) job/step to `.github/workflows/ci.yml`
- [x] Verify `node --test .claude/hooks/hooks.test.mjs` exits `0` and test `.githooks/pre-commit`, `commit-msg`, and `pre-push` on sample inputs

## Workstream 2 — Cold Verifier, Independent Test-Writer, Shared Skills & Handoff Log
- [x] Create `.claude/agents/spec-builder.md`, `.claude/agents/reviewer.md`, and `.claude/agents/test-writer.md`
- [x] Create `.agents/skills/record-decision/SKILL.md`, `.agents/skills/question-round/SKILL.md`, and `.agents/skills/pr-memo/SKILL.md`, and symlink all three into `.claude/skills/`
- [x] Add `## Handoff Log` standard to `openspec/config.yaml` and `.claude/skills/spec-implementer/SKILL.md`
- [x] Update `CLAUDE.md` First-Run Bootstrap to reference `.mjs` hooks, new agents, and shared skills

## Workstream 3 — Architectural Guardrails & Cross-Layer Contract Lock-In
- [x] Extend `.agents/rules/backend-java.md` with Spring AI 2.0 provider portability (no artificial offline stub) and strict Controller DTO + `@RestControllerAdvice` error boundary rules
- [x] Extend `.agents/rules/frontend-vue.md` with mandatory `loading/empty/success/error` states and zero client-side score/fit/ranking calculation rule
- [x] Add Cross-Layer Enum & State Lock-In Table to `docs/architecture.md`
- [x] Update `docs/workflow.md` with Day-to-Day Ticket Loop, 5-row Module Ownership Table, Blocking vs. Convention Candidate rule, and Link Never Copy rule
- [x] Update `AGENTS.md` with "Adopting Agent Roles" (3-row table) and "Cross-Tool Rule & Hook Parity" (6-row table)

## Workstream 4 — Kiro Crew Integration
- [x] Create `.kiro/steering/nifty-core.md` (`inclusion: always`, <=25 lines) pointing to `AGENTS.md`, `openspec/`, and `.githooks/`
- [x] Document Kiro Crew multi-agent orchestration (Conductor, parallel Builders, cold Reviewer, subscription reuse) in `README.md` and `docs/workflow.md`

## Workstream 5 — Stack-Agnostic SDD/ADD Guide (`docs/setup.md`)
- [x] Create `docs/setup.md` with exactly two top-level sections (`## For Teammates` and `## For Agents`) free of application tech-stack coupling, and link it from `README.md` and `AGENTS.md`

## Final Verification & Cold Review
- [x] Run `OPENSPEC_TELEMETRY=0 openspec validate --all` -> exit `0`
- [x] Run `node --test .claude/hooks/hooks.test.mjs` -> exit `0`
- [x] Run `cd backend && ./mvnw test` -> exit `0`
- [x] Run `cd frontend && npm run typecheck && npm run lint && npm run build` -> exit `0`
- [x] Run cold `reviewer` subagent audit on `git diff dev...HEAD` against `openspec/` specs and verify zero rule drift across `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.agents/rules/`, `.kiro/steering/nifty-core.md`, and `docs/setup.md`
- [x] Update `CHANGELOG.md` and record final entry in `## Handoff Log` below

## Handoff Log

- **Commits:** `d227b36` (`chore(specs)`), `dc13838` (`feat(hooks)`), `bda21fa` (`feat(agents)`), `737efe3` (`chore(rules)`), `ad7f093` (`docs(setup)`), plus cold-review hardening commit (`fix(hooks)`).
- **Verification:** `node --test .claude/hooks/hooks.test.mjs` (14/14 pass), `OPENSPEC_TELEMETRY=0 openspec validate --all` (10/10 pass, 0 warnings), `cd backend && ./mvnw -o test` (2/2 pass), `cd frontend && npm run typecheck && npm run lint && npm run build` (exit 0), live `.githooks/pre-commit` + `.githooks/commit-msg` verified on all commits.
- **Contracts/Migrations added:** Cross-Layer Enum & State Lock-In Table in `docs/architecture.md`; `.claude/hooks/*.mjs` + `.githooks/pre-commit`; `.claude/agents/{spec-builder,reviewer,test-writer}.md`; `.agents/skills/{record-decision,question-round,pr-memo}/`; `.kiro/steering/nifty-core.md`; `docs/setup.md`.
- **Decisions:** Kept single `openspec/changes/` plan tree (no `plans/`), canonical `AGENTS.md` + `.agents/rules/` (no `.claude/rules/`), Spring AI 2.0 provider portability without artificial offline demo stub.
- **Open Questions:** `O1`–`O7` remain open in `docs/decisions.md` for Day-1 team resolution.
