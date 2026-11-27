# Proposal: Superpowers Loops, Resumable Tasks & Archive Gate

## Context & Problem

The course toolkit uses Superpowers skills (brainstorming, test-driven development, systematic debugging, code review) to raise first-time quality. Our harness already has the roles (spec builder, cold reviewer, test writer), but:

1. **Superpowers is optional per developer.** `.claude/settings.json` does not declare the plugin, so a teammate's Claude Code never offers it.
2. **Nothing proves RED before GREEN.** The Handoff Log records verification, but not that the new test failed first. The professor's slide says: "Your Role: Engineering Manager — verify Claude never skips RED!"
3. **Progress lives in chat.** When a session hits a usage limit, the next tool or subscription cannot see which step was in progress, or whether the PR is open.
4. **Finished changes linger.** Four completed changes stayed in `openspec/changes/` until 2026-11-24, and nothing would stop it happening again.

Decisions 3.7, 3.8 and 3.9 record this.

## Scope

- Declare the Superpowers plugin in `.claude/settings.json`.
- Role prompts and `spec-implementer`: the brainstorm → TDD → debug → verify loop, RED/GREEN evidence, Status updates, commit after each task.
- `openspec/config.yaml` rules for the resumable `tasks.md` layout (Status, nested steps, Delivery, Handoff Log).
- `.claude/hooks/archive-gate.mjs` with three entry points: the Claude `Stop` hook, `.githooks/pre-push`, and a repo-invariant test.

## Out of Scope

- A hook that tries to detect whether TDD happened (unreliable; the cold reviewer checks RED evidence instead).
- Changes to `lib.mjs` or the guard-bash tests (owned by `bash-guard-hardening`).
- Edits to the generated OpenSpec skills (`.agents/skills/openspec-*`), which `openspec update` may overwrite.
