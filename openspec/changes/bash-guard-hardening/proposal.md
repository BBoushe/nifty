# Proposal: Bash Guard Hardening

## Context & Problem

`.claude/hooks/guard-bash.mjs` only recognises a guarded command when it is the first token after `stripLeadingEnvAssignments()` (`.claude/hooks/lib.mjs`). That helper removes env assignments and the `env`, `command` and `rtk [flags] [proxy|bypass]` wrappers. Any other wrapper hides the real command. Probes on 2026-11-24 showed that these commands are **allowed** today:

| Class | Allowed today |
|---|---|
| RTK subcommands that run a command | `rtk err git push origin master`, `rtk test …`, `rtk summary …`, `rtk run git push origin master`, `rtk run -- git push …`, `rtk run -c "git push origin master"` |
| Shell `-c` strings | `sh -c "git push origin master"`, `bash -c "git commit -m x"`, **`sh -c "cat .env"`**, **`rtk run -c "cat .env"`** |
| Generic prefixes | `eval git push …`, `xargs git push …`, `nice git push …`, `time git push …`, `sudo git push …`, `env -u VAR git push …` |
| Hook skipping | `git push --no-verify origin dev` (skips `.githooks/pre-push`) |

The `.env` rows leak `KICONNECT_API_KEY` into the model context, and no other layer catches them. RTK's own hook also returns `permissionDecision: allow` for the commands it rewrites, so the guard is the only check that sees agent intent before execution.

Source: `docs/prompts/done/ci_parity_protections_guard_hardening.md` §3 (Workstream C).

## Scope

- Peel more wrappers in `stripLeadingEnvAssignments` (one shared helper, coding standard #5).
- Evaluate command strings recursively (`sh|bash|zsh -c`, `rtk run -c|--command`, `env -S`, `eval`), with a depth cap that blocks.
- Block `git push --no-verify` to `dev`, `alpha`, `master` or `main` on any remote.
- Correct the RTK and guard-layer text in `README.md` and `docs/setup.md`.

## Out of Scope

- Interpreter one-liners (`python -c`, `node -e`), here-docs into a shell, `find -exec`, and the content of scripts run by path. The new `shortcut:` comment in `lib.mjs` names these limits.
- Server-side protection (owned by `forge-ci-parity`).
- Any change to `guard-files.mjs` or `guard-agent.mjs` behaviour. `guard-agent.mjs` also calls the helper, so its tests must stay green.

## Deviation From the Source Prompt

The source REQ-BG-04 also blocks `git push -n`. For `git push`, `-n` means `--dry-run` (`git push -h`), not `--no-verify`. This change blocks only `--no-verify` and keeps `git push -n` allowed.
