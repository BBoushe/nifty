# Design: Bash Guard Hardening

## 1. Wrapper Peeling (`lib.mjs`)

Keep the name and signature `stripLeadingEnvAssignments(tokens) → tokens`. Do not add a second helper (coding standard #5). It loops until no rule matches:

| Wrapper (basename) | Flags skipped | Flags that also consume the next token |
|---|---|---|
| `NAME=value` | — | — |
| `env` | any `-…` | `-u`, `--unset`, `-C`, `--chdir` (`-S`/`--split-string`: see §2) |
| `command` | any `-…` | — |
| `nice` | any `-…` | `-n`, `--adjustment` |
| `time` | any `-…` | — |
| `sudo` | any `-…` | `-u -g -C -h -p -r -t -U -D` |
| `xargs` | any `-…` | `-I -n -P -L -s -d -E -a` |
| `rtk` | global flags (`-v…`, `--skip-env`, `-h`, `-V`) | — |
| `rtk proxy|bypass|err|test|summary` | — | — |
| `rtk run` | `--`, `--skip-env` | `--shell` (`-c`/`--command`: see §2) |

The `--flag=value` and `-n5` forms never consume a second token. The `rtk git|gh|glab` forms need no rule, because peeling `rtk` leaves `git …`.

## 2. Command Strings (`guard-bash.mjs`)

`evaluateBashCommand(command, options)` keeps its public signature and reads an internal `options._depth` (default 0). For each segment, after peeling:

- `sh|bash|zsh` with a flag token that matches `^-[a-z]*c[a-z]*$`: the next token's **unquoted value** (from `tokenizeSegment`, never from the raw string) is a command string.
- `rtk run` with `-c` or `--command`: the next token is a command string.
- `env -S|--split-string`: the next token is a command string.
- `eval`: the remaining tokens joined with spaces form the command string.

Each command string goes to `evaluateBashCommand(inner, { ...options, _depth: depth + 1 })`. If the inner result blocks, the outer result blocks with the inner reason. If `depth + 1 > 3`, block with the reason `command nesting exceeds depth 3`.

## 3. `--no-verify` Push

In the existing push branch of `evaluateBashCommand`, if the args contain `--no-verify` and `parseGitPushTargets(args, currentBranch)` returns a destination in `{dev, alpha, master, main}`, block. `-n` is `--dry-run` and stays allowed. `node scripts/forge.mjs sync` is unaffected, because the guard only sees the `node …` command.

## 4. Remaining Limits (the new `shortcut:` comment)

`shortcut: interpreter one-liners (python -c, node -e), here-docs into a shell, find -exec, and scripts run by path are not inspected; extend the command-string table in guard-bash.mjs if agents start using them.`

## 5. Docs

- `README.md` "Optional: RTK" and `docs/setup.md`:
  - RTK 1.2.1 rewrites bare `mvn` but **not** `./mvnw` (probed with `rtk hook claude`). The `exclude_commands` entry stays as a harmless safeguard; state this real reason.
  - RTK auto-allows the commands it rewrites, so the guard is the only check that runs before execution.
  - Guards see through wrappers and `-c` strings up to depth 3.
  - Prefer absolute paths over `cd <dir> && …`, because the RTK rewrite may drop the `cd`.
- New `docs/setup.md` subsection "Guard Layers":
  1. Claude PreToolUse guard (agents only, before execution);
  2. git hooks (all clients, skippable with `--no-verify`);
  3. server rules (GitHub rulesets, GitLab protected branches).
