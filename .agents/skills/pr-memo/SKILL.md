---
name: pr-memo
description: Generate a concise <=10-line plain-language Pull Request summary targeting dev (What changed, Why, How to test, Invariants to check, Suggested reviewer).
---

# Skill: PR Memo

Use this skill before opening or updating a Pull Request targeting `dev`.

## Procedure

1. Inspect `git log dev..HEAD --oneline` and `git diff --stat dev...HEAD`.
2. Identify the impacted module(s) from the **Module Ownership Table** in `docs/workflow.md` to suggest a reviewer.
3. Produce a **<= 10-line** plain-language PR body readable in under 60 seconds using this exact structure:

```markdown
- **What changed:** <1–2 plain-language sentences>
- **Why:** <problem solved or OpenSpec change link>
- **How to test:** `<exact commands run and verified>`
- **Invariants to check:** <relevant AGENTS.md §2 invariants, or "Harness/docs only — no scoring pipeline code touched">
- **Suggested reviewer:** <owner role from docs/workflow.md Module Ownership Table>
```

4. Never include fabricated test results — list only commands actually executed with passing exit codes.
