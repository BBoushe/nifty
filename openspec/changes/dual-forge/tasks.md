# Tasks: Dual Forge

- [x] Record Decision 1.8 via `scripts/record-decision.mjs`
- [x] `GIT_FORGE=both` in `scripts/check-setup.mjs` (+ tests in `scripts/harness-scripts.test.mjs`)
- [x] `scripts/forge.mjs` (`push`, `pr`, `sync`, `status`) (+ tests)
- [x] `scripts/apply-gitlab-protections.sh` (dry-run verified; not yet run against GitLab)
- [x] Update `pr-memo`, `AGENTS.md`, `spec-builder.md`, `README.md`, `docs/setup.md`, `docs/workflow.md`, `.env.example`, `CHANGELOG.md`
- [ ] Run `scripts/apply-gitlab-protections.sh` against GitLab (needs `glab` + Maintainer rights)
- [x] Add `Bash(node scripts/forge.mjs:*)` to `.claude/settings.json` `permissions.allow`
