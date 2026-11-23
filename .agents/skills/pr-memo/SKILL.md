---
name: pr-memo
description: Generate a concise plain-language Pull Request (GitHub `gh`) and/or Merge Request (GitLab `glab`) body following .github/PULL_REQUEST_TEMPLATE.md / .gitlab/merge_request_templates/Default.md targeting dev, and open it on the forge(s) selected by the .env GIT_FORGE switch (github, gitlab, or both).
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
   - `GIT_FORGE=both` -> GitHub (`origin`) is primary, GitLab (`gitlab`) secondary (Decision 1.8). Never open only one side by hand; use `node scripts/forge.mjs` (steps 6-7).
2. Inspect `git log dev..HEAD --oneline` and `git diff --stat dev...HEAD`.
3. Identify the impacted module(s) from the **Module Ownership Table** in `docs/workflow.md` to suggest a reviewer.
4. Fill the **unified repository PR/MR template** (`.github/PULL_REQUEST_TEMPLATE.md` / `.gitlab/merge_request_templates/Default.md` — Decision 3.4), keeping every section short and plain-language (readable in under 60 seconds):

```markdown
## What changed?
- <1–2 plain-language bullets>

## Why?
- <problem solved + key Decision X.Y IDs>

## Spec / Issue Reference
- OpenSpec change (if applicable): `openspec/changes/<name>/`
- Related issue / Jira key: <key or N/A>
- Suggested reviewer (`docs/workflow.md` Module Ownership Table): <owner role>

## Verification
- Commands run: `<exact commands executed with passing exit codes>`
- [x] <check only applicable verified items; mark N/A items clearly>
- [x] Domain invariants (`AGENTS.md` §2) & living coding standards (`.agents/rules/coding-standards.md`) verified
```

5. Never invent an ad-hoc format that drops the template sections, and never include fabricated test results — list only commands actually executed with passing exit codes.
6. **Push (all modes):** `node scripts/forge.mjs push` pushes the current branch to the configured forge(s), GitHub first. If GitHub's push fails it stops, so GitLab never gets ahead. Add `--dry-run` to preview.
7. **Open the PR / MR:** save the body from step 4 to a temp file, then run
   ```sh
   node scripts/forge.mjs pr --title "<conventional-commit style title>" --body-file <file> [--base dev]
   ```
   With `GIT_FORGE=both` this creates the GitHub PR and the GitLab MR with identical text; a failure on one side does not stop the other, and the exit code is non-zero if either failed.
8. **After merge (`both` only):** merge on GitHub only (merge commit, not squash), then run `node scripts/forge.mjs sync` to fast-forward GitLab's `dev`/`alpha`/`master`; `node scripts/forge.mjs status` shows any drift. Do not merge the MR on GitLab.
