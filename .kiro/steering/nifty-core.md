---
inclusion: always
---

# Nifty — Kiro Steering Bridge

This file is a thin pointer for **Kiro IDE** and **Kiro Crew**. Do **not** duplicate rules or specs here.

1. **Universal Rules:** Follow [`AGENTS.md`](../../AGENTS.md) and scoped rules in [`.agents/rules/`](../../.agents/rules/) as the single source of truth for domain invariants, stack pins, and Git conventions.
2. **Authoritative Specs & Plans:** Read and update specs in [`openspec/specs/`](../../openspec/specs/) and active changes in [`openspec/changes/`](../../openspec/changes/) (`proposal.md`, `specs/`, `design.md`, `tasks.md` + `## Handoff Log`). Never create a parallel `.kiro/specs/` tree.
3. **Agent Roles:** Mirror the `spec-builder`, cold read-only `reviewer`, and `test-writer` split defined in [`.claude/agents/`](../../.claude/agents/) and `AGENTS.md`.
4. **Active Guards:** Ensure `git config core.hooksPath .githooks` is enabled so `.githooks/` (`pre-commit` secret scan, `commit-msg`, `pre-push` branch + compile gate) protects every branch.
5. **Harness Reference:** See [`docs/setup.md`](../../docs/setup.md) (`## For Agents`) for the canonical file inventory and modification rules.
