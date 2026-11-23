# Proposal: Harness v2 Upgrade (SDD/ADD Tooling, Cold Verification, Kiro Crew & Setup Guide)

## Context & Problem

Our initial repository setup (`feat/setup`) established OpenSpec (`openspec/`), root `AGENTS.md`, scoped `.agents/rules/`, basic POSIX `.claude/hooks/*.sh`, and portable `.githooks/`. Now that `.env` holds real `KICONNECT_API_KEY` credentials and the 5-person team is ramping up for the 1-week block week across macOS, Windows, and Linux using mixed harnesses (Claude Code, Antigravity `agy`, Codex, Kiro Crew, T3 Code), several harness gaps need closing:

1. **Cross-platform hook reliability & secret leak coverage:** POSIX `sh` + regex hooks behave inconsistently on Windows and do not block `Read`/`Grep`/`Glob` on `.env` or scan staged diffs (`git diff --cached`) for leaked secrets.
2. **Real separation between building and verifying:** Self-review lets domain invariant violations (LLM math, silent `0`/`1` instead of `N/A`, unverified quotes, missing audit logs) slip through.
3. **Cross-layer contract drift:** Backend DTO/error boundaries, Vue async view states, enum literals across Java/TS/JSONB, and day-to-day ticket/review loops need explicit guardrails.
4. **Kiro Crew orchestration reuse:** Developers with existing Claude Code / Codex subscriptions need a thin steering bridge so Kiro Crew can orchestrate parallel branches without duplicating specs into `.kiro/specs/`.
5. **Onboarding & replication clarity:** Teammates need a ~2-minute human guide to our SDD/ADD setup, and AI agents need a stack-agnostic canonical inventory and replication blueprint (`docs/setup.md`).

## Scope (5 Workstreams)

1. **Workstream 1 — Tested, Zero-Dependency Node `.mjs` Hooks + Permissions + `.githooks/` Bridge:** Replace `.claude/hooks/*.sh` with tested `.mjs` hooks (`lib.mjs`, `guard-files.mjs`, `guard-bash.mjs`, `guard-agent.mjs`, `test-gate.mjs`, `hooks.test.mjs`), harden `.claude/settings.json` (`permissions.allow`/`deny` + hooks), add `.githooks/pre-commit` and hook `test-gate.mjs` into `.githooks/pre-push`, and add `Hook Tests` to CI.
2. **Workstream 2 — Cold Verifier, Independent Test-Writer, Lean SDD Skills & Handoff Log:** Add `.claude/agents/{spec-builder,reviewer,test-writer}.md`, add role parity to `AGENTS.md`, add shared skills (`record-decision`, `question-round`, `pr-memo`) in `.agents/skills/` symlinked into `.claude/skills/`, and add `## Handoff Log` to `openspec/config.yaml` and `spec-implementer`.
3. **Workstream 3 — Architectural Guardrails & Cross-Layer Contract Lock-In:** Extend `.agents/rules/backend-java.md` and `.agents/rules/frontend-vue.md`, add Cross-Layer Enum & State Lock-In Table to `docs/architecture.md`, tighten `docs/workflow.md`, and add Cross-Tool Rule & Hook Parity table to `AGENTS.md`.
4. **Workstream 4 — Kiro Crew Integration:** Add thin `.kiro/steering/nifty-core.md` (`inclusion: always`) and document Kiro Crew multi-agent orchestration in `README.md` and `docs/workflow.md`.
5. **Workstream 5 — Stack-Agnostic `docs/setup.md`:** Create a two-part SDD/ADD guide (`## For Teammates` and `## For Agents`) explaining our AI harness and how to modify or replicate it cleanly.

## Explicitly Out of Scope

- No separate `plans/` or `.claude/plans/` directory (`openspec/changes/` is our single spec & execution plan tree).
- No duplicate `.claude/rules/*.md` files (`AGENTS.md` and `.agents/rules/*.md` remain canonical).
- No offline LLM stub gateway (`StubLlmEvaluatorGateway`); Spring AI 2.0 already provides provider portability across `ki:connect` NRW and future Gemini/Claude/OpenAI APIs, and tests mock/replay `ChatClient` directly.
- No slow Testcontainers execution inside `pre-commit` or `pre-push` hooks.
- No semester-long lab ceremony (`lab-check`, per-area agents, 12-folder `docs/` split, `AI-assisted:` comments).
