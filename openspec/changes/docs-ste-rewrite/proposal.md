# Proposal: Rewrite Existing Docs in ASD-STE100

> **Queued.** Start only after `bash-guard-hardening`, `forge-ci-parity`, `backend-architecture-baseline`, `ai-context-docs` and `superpowers-loops` have merged. This change touches the same files as all of them.

## Context & Problem

Decision 3.5 adopts ASD-STE100 for new and changed prose only, so the parallel harness changes do not conflict. Existing docs still mix long passive sentences, marketing words and several ideas per sentence. Agents read these files in every session (`AGENTS.md`, rules, specs), so wordy prose costs input tokens each time and can be read in more than one way.

## Scope

Rewrite the prose of these files in ASD-STE100, without changing meaning:

- `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.kiro/steering/*.md`
- `README.md`
- `docs/setup.md`, `docs/workflow.md`, `docs/architecture.md`, `docs/api.md`, `docs/design_system.md`, `docs/discovery-canvas.md`
- `.agents/rules/*.md`
- `openspec/specs/*/spec.md`

## Out of Scope

- `docs/decisions/**` (records of what was decided at the time; never rewritten), `CHANGELOG.md`, `docs/feature_history.md` entries, `openspec/changes/archive/**`, quotes from external sources in `docs/ai_docs/`.
- Any change to rules, numbers, paths, commands, REQ IDs or EARS keywords.
- Code comments.
