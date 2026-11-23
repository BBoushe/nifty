---
name: spec-implementer
description: Implement an OpenSpec change task-by-task with EARS traceability, atomic Conventional Commits, and real verification.
---

# Skill: Spec Implementer

Use this skill when implementing any change under `openspec/changes/<change>/`.

## Procedure

1. Read `AGENTS.md`, `openspec/config.yaml`, `openspec/changes/<change>/proposal.md`, `requirements.md`, `design.md`, and `tasks.md`.
2. Identify the next unchecked task in `tasks.md`.
3. Check the corresponding domain spec in `openspec/specs/<module>/spec.md` so no EARS invariant is broken.
4. Implement the minimal, clean code and tests required for the task.
5. Run real verification commands (`./mvnw test`, `npm run typecheck && npm run lint && npm run build`, `node --test .claude/hooks/hooks.test.mjs`, or `docker compose config`) and verify exit code `0`.
6. Mark the task `[x]` in `tasks.md`, append a compact 5-line entry to `## Handoff Log` (`Commits`, `Verification`, `Contracts/Migrations added`, `Decisions`, `Open Questions`), and commit with a Conventional Commit message.
7. Once all tasks pass and are merged, sync updated requirements into `openspec/specs/<module>/spec.md`.
