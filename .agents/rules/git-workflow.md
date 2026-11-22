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
   - NEVER push directly to `dev` unless every commit being pushed is a `docs:` or `hotfix:` commit.
3. **Conventional Commits:**
   - Format: `type(scope): imperative summary` (types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`).
   - Keep commits atomic and logically revertible per milestone.
4. **Pull Requests:**
   - PRs into `dev`: 0 approvals required (CI must pass).
   - PRs into `alpha`: >= 2 approvals required (CI must pass).
   - PRs into `master`/`main`: >= 4 approvals required (CI must pass).
   - PR descriptions must be short, plain-language summaries focused on **What** changed and **Why** it helps the team.
