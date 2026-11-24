# Proposal: Bash Guard Gaps

## Context & Problem

`bash-guard-hardening` (PR #7) merged without a cold review. The review ran after the merge, on 2026-11-24. It found command forms that the guard still allows, although REQ-BG-02 and REQ-BG-04 say it must block them. These probes pass `evaluateBashCommand` today:

| Class | Allowed today |
|---|---|
| `-c` after other shell options | `bash -euo pipefail -c "git push origin master"`, `bash -Ec "…"`, `bash -O extglob -c "…"`, `sh -c -- "…"` |
| Attached or `=` command strings | `env -S"…"`, `env -iS "…"`, `env --split-string="…"`, `rtk run --command="…"`, **`rtk run -c"cat .env"`** |
| Shortened or indirect hook skip | `git push --no-veri origin dev`, `git -c core.hooksPath=/dev/null push origin dev` |
| `HEAD` / `@` as the destination | `git push origin HEAD` on `master`; `git push --no-verify origin HEAD` on `dev` |
| Force push forms | `git push -fu origin x`, `--force-with-lease=x`, `--forc`, `+feat/x` |
| Shell structure | `true & git push origin master`, `(git push origin master)`, `{ git push origin master; }`, `if git push origin master; then :; fi`, `echo $(git push origin master)` |
| Shell reads code from stdin | `echo "git push origin master" \| sh` |
| Other shells and wrappers | `dash -c`, `ksh -c`, `exec`, `timeout 5`, `nohup` |
| Chaining inside a string | `git add . && sh -c "git commit -m x"` |
| `.env` globs | `cat .en?`, `cat .e*`, `cat .[e]nv` |

The `.env` rows leak secrets into the model context. GitHub rejects direct pushes to protected branches, but Maintainers can push to GitLab `dev`, and only this guard stops `.env` reads.

## Scope

- Fix every class in the table in `.claude/hooks/lib.mjs` and `.claude/hooks/guard-bash.mjs`.
- Keep one definition of the command-string flags (the review found two copies).
- Correct the guard claims in `README.md` and `docs/setup.md`, and list the limits that remain.

## Out of Scope

- Interpreter one-liners (`python -c`, `node -e`), `find -exec`, and the content of scripts run by path. These stay in the `shortcut:` comment in `lib.mjs`.
- A full shell parser. The guard stays a token-level check; GitHub rulesets and GitLab protections stay the server gate.
- Any change to `guard-files.mjs` or `guard-agent.mjs` behaviour.
