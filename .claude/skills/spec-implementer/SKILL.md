---
name: spec-implementer
description: Implement an OpenSpec change task-by-task with EARS traceability, atomic Conventional Commits, and real verification.
---

# Skill: Spec Implementer

Use this skill when implementing any change under `openspec/changes/<change>/`.

## Procedure

1. Read `AGENTS.md`, `openspec/config.yaml`, `openspec/changes/<change>/proposal.md`, `specs/<capability>/spec.md`, `design.md`, and `tasks.md`.
2. Verify that the `ponytail:` skills are available. If not, install the plugin as rule 9 of `.claude/agents/spec-builder.md` says (Decision 3.15). Identify the next unchecked task in `tasks.md`.
3. Check the corresponding domain spec in `openspec/specs/<module>/spec.md` so no EARS invariant is broken.
4. Follow the Build Loop in `.claude/agents/spec-builder.md` for each step: brainstorm unclear points, write the failing test and run it (RED), write the minimal code with `ponytail:ponytail` (GREEN), use systematic debugging on any failure.
5. Run real verification commands (`./mvnw test`, `npm run typecheck && npm run lint && npm run build`, `node --test .claude/hooks/hooks.test.mjs`, or `docker compose config`) and verify exit code `0`.
6. Tick each step and the task `[x]` in `tasks.md`, append a compact 5-line entry to `## Handoff Log` (`Commits`, `Verification` as `RED: <cmd> → FAIL (<reason>); GREEN: <cmd> → exit 0`, `Contracts/Migrations added`, `Decisions`, `Open Questions`), commit with the task's Conventional Commit, and push the branch. Overwrite `## Status` before every stop. After the last task, run `## Delivery` in order (archive before push).
7. Once all tasks pass and are merged, sync updated requirements into `openspec/specs/<module>/spec.md`.
