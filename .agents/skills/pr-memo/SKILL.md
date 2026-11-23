---
name: pr-memo
description: Generate a concise <=10-line plain-language Pull Request (GitHub `gh`) or Merge Request (GitLab `glab`) summary targeting dev based on the active .env GIT_FORGE switch.
---

# Skill: PR / MR Memo (Forge-Aware)

Use this skill before opening or updating a Pull Request (GitHub) or Merge Request (GitLab) targeting `dev`.

## Procedure

1. **Detect Active Forge Safely (Never Read `.env` Directly):**
   ```sh
   node scripts/check-setup.mjs --forge
   ```
   - `GIT_FORGE=github` -> use `gh pr create --base dev ...` / `gh pr edit`
   - `GIT_FORGE=gitlab` -> use `glab mr create --target-branch dev ...` / `glab mr update`
2. Inspect `git log dev..HEAD --oneline` and `git diff --stat dev...HEAD`.
3. Identify the impacted module(s) from the **Module Ownership Table** in `docs/workflow.md` to suggest a reviewer.
4. Produce a **<= 10-line** plain-language PR/MR body readable in under 60 seconds using this exact structure:

```markdown
- **What changed:** <1–2 plain-language sentences>
- **Why:** <problem solved, OpenSpec change link, and key Decision X.Y IDs>
- **How to test:** `<exact commands run and verified>`
- **Invariants & coding rules to check:** <relevant AGENTS.md §2 invariants & coding-standards.md checks>
- **Suggested reviewer:** <owner role from docs/workflow.md Module Ownership Table>
```

5. Never include fabricated test results — list only commands actually executed with passing exit codes.
