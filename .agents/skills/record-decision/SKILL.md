---
name: record-decision
description: Promote a resolved O1-O7 open decision into D15+ in docs/decisions.md and synchronize openspec/config.yaml, CLAUDE.md, and affected skills in the same PR.
---

# Skill: Record Decision

Use this skill whenever the team finalizes an open decision (`O1`–`O7` in `docs/decisions.md`) or adds a new architectural decision (`D15+`).

## Procedure

1. **Assign Next ID:** Read `docs/decisions.md`, find the highest `D<n>` entry, and allocate `D<n+1>`.
2. **Update `docs/decisions.md`:**
   - Add the new `D<n+1>` row to Section 1 (`Decided Architecture & Product Rules`) with concise Topic, Decision, and Rationale.
   - Remove or mark the corresponding `O<k>` row in Section 2 (`Open Team Decisions`) as resolved with a pointer to `D<n+1>`.
3. **Synchronize Downstream References (Same PR):**
   - `openspec/config.yaml` — replace any `TBD` text in `context:` if affected (e.g., `O1` UI kit or `O2` Interviewer LLM provider).
   - `AGENTS.md` & `CLAUDE.md` — update any `[TBD]` references (e.g., First-Run Bootstrap items or Monorepo Map).
   - `.agents/rules/*.md` and `.claude/skills/*/SKILL.md` — replace matching `[TBD — see docs/decisions.md O<k>]` placeholders (for example `frontend-vue-check` for `O1` or `backend-spring-check` for `O2`).
4. **Verify Consistency:**
   - Run `OPENSPEC_TELEMETRY=0 openspec validate --all`.
   - Grep for the old `O<k>` TBD text across the repo to ensure no stale placeholder remains.
5. **Commit Atomically:** Use `docs(decisions): record D<n+1> <short-topic>` (or include in the feature PR that implements the decision).
