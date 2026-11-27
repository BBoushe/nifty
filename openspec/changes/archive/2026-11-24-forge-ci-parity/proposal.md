# Proposal: Forge CI Parity & GitLab Protections

## Context & Problem

Merges happen **only on GitHub**. GitLab receives them afterwards through `node scripts/forge.mjs sync` (Decision 1.8). Two gaps remain:

1. **CI is weaker on GitHub.** GitLab also builds both Docker images (`backend-image`, `frontend-image`). A Dockerfile regression can pass GitHub, merge, and fail on GitLab only after it is already in `dev` on both forges. Also, `Hook Tests (Node)` runs on GitHub but no ruleset requires it.
2. **GitLab `dev` and `alpha` are unprotected.** Anyone with Developer access can push or force-push there. This created the GitLab-only drift that needed a manual force reset on 2026-11-24. It is also the only server-side backstop for `git push --no-verify` on GitLab.

Decision 1.9 records the parity rule. Source: `docs/prompts/done/ci_parity_protections_guard_hardening.md` §1, §2, §4.

## Scope

- Two Docker image jobs on GitHub with the same commands as GitLab.
- Three more required checks in `.github/rulesets/{dev,alpha,main}.json`.
- A zero-dependency drift test that fails when the two CI files run different commands.
- Apply the GitHub rulesets and the GitLab protections (ops, after user confirmation).
- Finish and archive the `dual-forge` change.
- A CI parity table and the Maintainer-only `sync` note in `docs/workflow.md`.
- Ask the user about the local `archive/fe-mvp` tag.

## Out of Scope

- A container registry or image push (registry is off on GitLab; intentional on both forges).
- GitLab Premium approval rules (the script warns and skips them on the FH instance).
- Changes to the Bash guard (owned by `bash-guard-hardening`).
