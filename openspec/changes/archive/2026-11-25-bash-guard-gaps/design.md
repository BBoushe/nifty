# Design: Bash Guard Gaps

All changes stay in `.claude/hooks/lib.mjs` (shared token helpers) and `.claude/hooks/guard-bash.mjs` (rules). Signatures that other hooks use stay unchanged: `stripLeadingEnvAssignments(tokens) → tokens`, `getGitSubcommand(tokens) → { subcommand, args } | null` (it gains a `globals` field; existing callers ignore it), `isBlockedEnvPath(input) → boolean`, `evaluateBashCommand(command, options) → { blocked, reason? }`.

## 1. Command-string flags, defined once (REQ-BG-07)

`lib.mjs` exports `commandStringFlag(value, wrapper) → { inline: string | null } | null`:

| Wrapper | Flag forms | `inline` |
|---|---|---|
| `env` | `-S`, `--split-string` | `null` (string is the next token) |
| `env` | `-S<str>`, `-[iv0]*S<str>`, `--split-string=<str>` | `<str>` (or `null` when empty) |
| `rtk run` | `-c`, `--command` | `null` |
| `rtk run` | `-c<str>`, `--command=<str>` | `<str>` |

`stripLeadingEnvAssignments` stops on any of these and leaves the flag first. `extractCommandString` in `guard-bash.mjs` calls the same helper with `wrapper = 'any'` (accepts both rows), so the flag list lives in one place. The `COMMAND_STRING_FLAGS` set in `guard-bash.mjs` is removed.

## 2. Shell `-c` search (REQ-BG-06, REQ-BG-12)

`SHELLS = sh, bash, zsh, dash, ksh`. Walk the tokens after the shell name:

```text
sawC = false
for each token t:
  if t == '--': return sawC ? next token : null (script follows)
  if t in {'-o','+o','-O','+O','--rcfile','--init-file'}: skip next token; continue
  if t starts with '--': continue                     # --norc, --login, …
  if t matches /^[-+][A-Za-z]+$/:
     if t starts with '-' and contains 'c': sawC = true
     if t ends with 'o' or 'O' (and is not exactly handled above): skip next token  # -euo pipefail
     continue
  return sawC ? t : SCRIPT                            # first operand
return sawC ? null : STDIN
```

`extractCommandString` returns the string, or one of two markers. `STDIN` (no string, no script) blocks with "shell reads commands from stdin; run the command directly" (REQ-BG-12). `SCRIPT` is allowed (scripts by path stay out of scope).

## 3. Push parsing (REQ-BG-08, REQ-BG-09, REQ-BG-10)

In `parseGitPushTargets`:

- **Force:** `/^-[A-Za-z]*f[A-Za-z]*$/` (combined short flags); long name (text before `=`) of length ≥ 5 that is a prefix of `--force`, `--force-with-lease` or `--force-if-includes` (`--fo` is ambiguous with `--follow-tags`, so 5 is the floor); any refspec that starts with `+`.
- **Hook skip:** long name of length ≥ 9 that is a prefix of `--no-verify` (`--no-ve` is ambiguous with `--no-verbose`). Also `globals` from `getGitSubcommand` contain `-c` followed by a value whose key, lower-cased, is `core.hookspath`.
- **Destinations:** `HEAD` and `@` map to `currentBranch` (also as the destination after `:`).

## 4. Shell structure (REQ-BG-11)

- `splitShellSegments` also splits on a single `&` that is not part of `&&`, `>&`, `<&` or `&>`.
- `stripLeadingEnvAssignments` also peels: leading `(` and `{` characters stuck to the first token, the tokens `(`, `{`, `!`, the keywords `if then else elif do while until`, and the wrappers `exec` (value flag `-a`), `nohup`, `timeout` (value flags `-s`, `--signal`, `-k`, `--kill-after`, then one duration operand).
- `evaluateBashCommand` evaluates each `$( … )` and backtick substitution of a segment as a nested command (same depth cap as `-c`). It reuses `extractCommandSubstitutions`.
- Chaining state: `options._chain = { add, commit }` is created at depth 0 and passed to nested calls by reference. Only depth 0 applies the "no chained add and commit" rule, after all segments.

## 5. `.env` globs (REQ-BG-13)

In `segmentTouchesBlockedEnv`, an **unquoted** argument that contains `*`, `?` or `[` is turned into an anchored regex (`*` → `[^/]*`, `?` → `[^/]`, `[…]` kept). Each path segment is tested against `.env` and `.env.local`. A match blocks, unless the literal argument ends in `.env.example`. Quoted arguments are not globbed by the shell, so `grep ".*" file` stays allowed.

## 6. Message heredocs: an allow-list (after the second to fourth reviews)

A scanner that tries to find every quoted heredoc (a deny-list) kept leaking: fake `<<'X'` markers in quotes, comments, unquoted heredoc bodies, `${…}` and backticks hid real commands. So the guard treats a heredoc body as text only in two known forms, and checks every other heredoc line as a command (as before this change).

- `stripTextHeredocs(command) → { text, bodies }` in `lib.mjs`:
  - `git|gh|glab … -m|-F|--message|--body|--title|--description|--notes "$(cat <<'X'⏎…⏎X⏎)"`, only when everything before it in the command is plain arguments of that one command (no substitution, separator, comment, redirection or open quote), and no body line equals the delimiter;
  - `git|gh|glab … -F -|--file -|--body-file - … <<'X'` as the whole first line (`git commit -F - <<'EOF'`), when the text before `<<` is plain arguments with no trailing `\`.
  - `<<-` is never accepted, because bash ends it at a tab-indented delimiter.
- Cost: any other heredoc (for example a `python3 - <<'EOF'` script whose text has backticks) is checked line by line as commands, so it can be over-blocked. Use a script file instead.
- `evaluateBashCommand` always scans the removed bodies for the secret env file.
- `splitShellSegments` ends a backtick at the next unescaped backtick, as bash does.

## 7. Docs

`README.md` (Optional: RTK) and `docs/setup.md` (bullet, Guard Layers, RTK invariant): list the shells and wrappers, and replace "sees through `-c` strings" with the exact forms covered plus the remaining limits (interpreter one-liners, `find -exec`, scripts by path, other shell syntax). Update the `shortcut:` comment in `lib.mjs`, and replace its `Decision: design.md §1-§2` pointer with the archived design paths of `bash-guard-hardening` and `bash-guard-gaps`.
