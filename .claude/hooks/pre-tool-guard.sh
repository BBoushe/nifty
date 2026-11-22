#!/usr/bin/env sh
# Claude Code PreToolUse safety guard (portable POSIX sh)
# Blocks editing .env secret files and direct pushes to protected branches (master/main/alpha).
# Exits 2 with an explanation on stderr to block the tool call; exits 0 to allow.

set -eu

PAYLOAD=$(cat)

# Block writing/editing real .env files (allow .env.example)
if printf '%s' "$PAYLOAD" | grep -Eq '"file_path"[[:space:]]*:[[:space:]]*"[^"]*/\.env(\.[^"/]+)?"|"file_path"[[:space:]]*:[[:space:]]*"\.env(\.[^"/]+)?"'; then
  if ! printf '%s' "$PAYLOAD" | grep -Eq '"file_path"[[:space:]]*:[[:space:]]*"[^"]*\.env\.example"'; then
    echo "BLOCKED: Agents must not edit .env secret files directly. Update .env.example instead and ask the developer to edit .env locally." >&2
    exit 2
  fi
fi

# Block git push directly to master, main, or alpha (including --force)
if printf '%s' "$PAYLOAD" | grep -Eq 'git[[:space:]]+push([[:space:]]+[^"]*)?[[:space:]]+(origin[[:space:]]+)?(([^":[:space:]]+:)?(refs/heads/)?(master|main|alpha))([[:space:]]|"|$)'; then
  echo "BLOCKED: Direct pushes to master/main/alpha are forbidden. Push to a feature branch and open a Pull Request." >&2
  exit 2
fi

exit 0
