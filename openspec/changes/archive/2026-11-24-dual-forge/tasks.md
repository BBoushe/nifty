# Tasks: Dual Forge

- [x] Record Decision 1.8 via `scripts/record-decision.mjs`
- [x] `GIT_FORGE=both` in `scripts/check-setup.mjs` (+ tests in `scripts/harness-scripts.test.mjs`)
- [x] `scripts/forge.mjs` (`push`, `pr`, `sync`, `status`) (+ tests)
- [x] `scripts/apply-gitlab-protections.sh` (dry-run verified; not yet run against GitLab)
- [x] Update `pr-memo`, `AGENTS.md`, `spec-builder.md`, `README.md`, `docs/setup.md`, `docs/workflow.md`, `.env.example`, `CHANGELOG.md`
- [x] Run `scripts/apply-gitlab-protections.sh` against GitLab (needs `glab` + Maintainer rights)
- [x] Add `Bash(node scripts/forge.mjs:*)` to `.claude/settings.json` `permissions.allow`

## Handoff Log

### 2026-11-24 — GitLab protections applied (via forge-ci-parity Task 5)
- **Commits:** `chore(specs): complete dual-forge protections task`
- **Verification:** `scripts/apply-gitlab-protections.sh --dry-run` → exit 0; real run → exit 0; `glab api projects/6748/protected_branches` → `master`, `main`, `alpha`, `dev` with push/merge 40 (Maintainer) and `allow_force_push: false`; `only_allow_merge_if_pipeline_succeeds` → `true`.
- **Contracts/Migrations added:** none
- **Decisions:** none. Expected WARN logged: `approval rules unavailable (GitLab Free?); skipping` for master x4, main x4, alpha x2.
- **Open Questions:** none
