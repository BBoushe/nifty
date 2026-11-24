---
trigger: model_decision
description: Apply when creating branches, writing commit messages, pushing code, or opening Pull Requests.
---

# Git Workflow & Governance Rules

1. **Branching Model:**
   - Branch new work off `dev`: `feat/<slug>`, `fix/<slug>`, `refactor/<slug>`, `chore/<slug>`, `docs/<slug>`, `hotfix/<slug>`.
   - Promotion path: `feat/* -> dev -> alpha -> master` (rules also cover `main`).
2. **Protected Branches:**
   - NEVER push directly to `master`, `main`, or `alpha`.
   - NEVER push directly to `dev`. Every change, including `docs:` and `hotfix:` commits, reaches `dev` through a PR (Decision 3.10). The GitHub `dev` ruleset rejects direct pushes.
3. **Conventional Commits:**
   - Format: `type(scope): imperative summary` (types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`).
   - Keep commits atomic and logically revertible per milestone.
4. **Pull Requests / Merge Requests:**
   - PRs/MRs into `dev`: 0 approvals required (CI must pass).
   - PRs/MRs into `alpha`: >= 2 approvals required (CI must pass).
   - PRs/MRs into `master`/`main`: >= 4 approvals required (CI must pass).
   - **Mandatory Template (Decision 3.4):** Every PR/MR body MUST follow `.github/PULL_REQUEST_TEMPLATE.md` (mirrored in `.gitlab/merge_request_templates/Default.md`) using the `pr-memo` skill — keep it concise (<60s read) while preserving `What changed?`, `Why?`, `Spec / Issue Reference`, and `Verification` checklist sections.
   - Check active forge via `node scripts/check-setup.mjs --forge` (`gh`, `glab`, or `node scripts/forge.mjs` when `GIT_FORGE=both`).
