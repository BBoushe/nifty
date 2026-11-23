# Delta Spec: Harness v2 Upgrade

## ADDED Requirements

### Requirement: REQ-H2-01 Cross-Platform Node Hooks & Secret Protection
**WHEN** a tool call or shell command targets `.env*` (excluding `.env.example`), stages secrets in `git diff --cached`, chains `git add` with `git commit`, or writes to a protected branch **THE SYSTEM SHALL** block the action with a clear stderr message and non-zero exit code using zero-dependency Node `.mjs` scripts.

#### Scenario: Reading or editing `.env` is blocked while `.env.example` is allowed
- **WHEN** `guard-files.mjs` receives a `Read`, `Edit`, `Write`, `Grep`, or `Glob` payload targeting `.env` or `backend/.env.local`
- **THEN** it exits with code `2` and explains why on stderr
- **AND** when the target is `.env.example`, it exits with code `0`.

#### Scenario: Leaked secret in staged diff or protected branch push is blocked
- **WHEN** `guard-bash.mjs` evaluates a `git commit` whose staged diff contains a real API key (`sk-*`, `sk-ant-*`, `AIza*`, `ghp_*`, `github_pat_*`, private key block, or non-placeholder `KICONNECT_API_KEY`) or evaluates `git push` targeting `master`/`main`/`alpha` or non-`docs:`/`hotfix:` `dev`
- **THEN** it exits with code `2` and blocks the operation.

#### Scenario: Scoped agent write enforcement works in readonly and tests-only modes
- **WHEN** `guard-agent.mjs` runs in `readonly` mode on any file edit/write or mutating bash command, or in `tests-only` mode on a non-test file outside `backend/src/test/**` and `frontend/src/**/*.test.*` / `**/*.spec.*`
- **THEN** it exits with code `2` and refuses the action.

#### Scenario: Built-in hook unit test suite passes locally and in CI
- **WHEN** `node --test .claude/hooks/hooks.test.mjs` is executed
- **THEN** all test cases pass with exit code `0` in under 2 seconds without external dependencies.

### Requirement: REQ-H2-02 Fast Path-Scoped Build Gate & Git Hooks Bridge
**WHEN** `.claude/hooks/test-gate.mjs` runs **THE SYSTEM SHALL** inspect touched files and run only fast compile/build checks (`frontend/`: `npm run typecheck && npm run build` with `CI=true`; `backend/`: `./mvnw -q test-compile` or `mvnw.cmd -q test-compile` on Windows), skip slow Testcontainers, and honor `NIFTY_SKIP_GATE=1`.

#### Scenario: Git hooks enforce secret checks on commit and fast build gate on push
- **WHEN** a contributor or any AI harness commits or pushes with `core.hooksPath=.githooks`
- **THEN** `.githooks/pre-commit` delegates to `node .claude/hooks/guard-bash.mjs --pre-commit` and `.githooks/pre-push` runs branch protection plus `node .claude/hooks/test-gate.mjs`.

### Requirement: REQ-H2-03 Cold Verification Roles, Shared SDD Skills & Handoff Log
**THE SYSTEM SHALL** define three focused agent roles (`spec-builder`, cold read-only `reviewer`, and spec-driven `test-writer`) in `.claude/agents/` with cross-tool parity in `AGENTS.md`, three shared skills (`record-decision`, `question-round`, `pr-memo`) in `.agents/skills/` symlinked into `.claude/skills/`, and a `## Handoff Log` convention in `openspec/config.yaml` and `spec-implementer`.

#### Scenario: Cold reviewer audits strictly against diff and OpenSpec EARS requirements
- **WHEN** a review is executed using `.claude/agents/reviewer.md`
- **THEN** the reviewer operates read-only, receives only `git diff dev...HEAD` and relevant `openspec/` specs, and outputs a <=30-line `Verdict / Blocking / Convention Candidates` report separating hard invariant/spec blockers from non-blocking style suggestions.

#### Scenario: Decision and ambiguity skills stay lean and scoped
- **WHEN** a team open decision `O1`–`O7` is resolved via `record-decision` or clarified via `question-round`
- **THEN** `record-decision` updates `docs/decisions.md`, `openspec/config.yaml`, `CLAUDE.md`, and affected skills in the same PR, while `question-round` remains scoped strictly to `O1`–`O7` or genuine spec ambiguities.

### Requirement: REQ-H2-04 Cross-Layer Architectural Guardrails
**THE SYSTEM SHALL** codify Spring AI 2.0 provider portability (without artificial offline stubs), strict Controller DTO + `@RestControllerAdvice` boundaries, Vue `loading/empty/success/error` states + zero client-side scoring math, a Cross-Layer Enum Table in `docs/architecture.md`, and updated ticket/ownership rules in `docs/workflow.md` and `AGENTS.md`.

#### Scenario: Rules stay canonical without cross-tool drift
- **WHEN** rules are updated for backend, frontend, workflow, or cross-tool hook parity
- **THEN** `.agents/rules/*.md` and `AGENTS.md` remain the single source of truth with zero duplicate rule files in `.claude/rules/`.

### Requirement: REQ-H2-05 Kiro Crew Thin Steering Bridge
**THE SYSTEM SHALL** provide `.kiro/steering/nifty-core.md` (`inclusion: always`, <=25 lines) pointing Kiro IDE and Kiro Crew to `AGENTS.md`, `openspec/`, and `.githooks/` and document Kiro Crew multi-agent subscription reuse in `README.md` and `docs/workflow.md`.

#### Scenario: Kiro Crew reuses canonical OpenSpec and AGENTS.md without duplication
- **WHEN** Kiro IDE or Kiro Crew opens the repository
- **THEN** `.kiro/steering/nifty-core.md` loads automatically and directs Kiro to `AGENTS.md`, `openspec/specs/`, `openspec/changes/`, and `.githooks/` without creating `.kiro/specs/` or copying rules.

### Requirement: REQ-H2-06 Stack-Agnostic SDD/ADD Harness Guide (`docs/setup.md`)
**THE SYSTEM SHALL** provide `docs/setup.md` explaining exclusively the SDD/ADD and multi-agent harness (independent of application tech stack) in two top-level sections: `## For Teammates` (concise human guide) and `## For Agents` (canonical inventory, modification rules, and replication checklist).

#### Scenario: Both humans and AI agents can onboard or replicate the harness from `docs/setup.md`
- **WHEN** a teammate reads `## For Teammates` or an AI agent reads `## For Agents`
- **THEN** teammates get the minimal daily loop and design rationale without jargon walls, and agents get an exact canonical-vs-pointer file inventory and step-by-step replication checklist free of app-stack coupling.
