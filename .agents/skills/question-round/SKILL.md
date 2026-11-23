---
name: question-round
description: Run a concise, grouped decision round with bold recommended defaults, strictly scoped to O1-O7 TBDs or genuine spec ambiguities.
---

# Skill: Question Round

## Scope Gate (Important)

Use this skill **ONLY** when blocked by:
- An unresolved team open decision (`O1`–`O7` in `docs/decisions.md`), **or**
- A genuine ambiguity or contradiction in `openspec/` EARS requirements that materially changes API contracts, schema, or UX.

Do **NOT** use this skill as a blanket gate on routine, unambiguous tasks.

## Procedure

1. Collect all open blocking questions into a **single** grouped message (maximum 5 questions).
2. Number every question clearly, list 2–3 concrete options, and mark your **recommended default in bold**.
3. Tell the user they can reply `"default"` to accept all bold recommendations or override specific numbers (e.g., `default, 2=B`).
4. Once answered:
   - If the answer resolves an `O1`–`O7` decision, invoke the `record-decision` skill so it is persisted across `docs/decisions.md` and the harness.
   - Otherwise, record the clarified choice in the active `openspec/changes/<change>/design.md` or `tasks.md` `## Handoff Log`.
