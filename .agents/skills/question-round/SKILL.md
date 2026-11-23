---
name: question-round
description: Run a concise, grouped decision round with bold recommended defaults (scoped to O1-O7 TBDs or genuine spec ambiguities) and persist every answer via record-decision.
---

# Skill: Question Round

## Scope Gate (Important)

Use this skill **ONLY** when blocked by:
- An unresolved team open decision (`O1`–`O7` in `docs/decisions/index.md`), **or**
- A genuine ambiguity or contradiction in `openspec/` EARS requirements that materially changes API contracts, schema, UX, or architecture.

Do **NOT** use this skill as a blanket gate on routine, unambiguous tasks.

## Procedure

1. Collect all open blocking questions into a **single** grouped message (maximum 5 questions).
2. Number every question clearly, list 2–3 concrete options, and mark your **recommended default in bold**.
3. Tell the user they can reply `"default"` to accept all bold recommendations or override specific numbers (e.g., `default, 2=B`).
4. **Mandatory Recording Step (Linked to `record-decision`):**
   - As soon as the user answers, **always** invoke the `record-decision` skill using `node scripts/record-decision.mjs --trigger question-round ...` (passing `--resolves O<k>` if it resolves an open decision) **before** implementing code.
   - Record the resulting `Decision X.Y` ID(s) in the active `openspec/changes/<change>/design.md`, `tasks.md` `## Handoff Log`, and inline code comments (`// Decision X.Y: ...`).
