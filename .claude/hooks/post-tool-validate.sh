#!/usr/bin/env sh
# Claude Code PostToolUse lightweight validator (portable POSIX sh)
# Runs fast non-destructive checks when backend or frontend source files change.

set -eu

PAYLOAD=$(cat)

# Frontend TypeScript / Vue changes: run fast typecheck if node_modules exists
if printf '%s' "$PAYLOAD" | grep -Eq '"file_path"[[:space:]]*:[[:space:]]*"[^"]*frontend/src/[^"]*\.(ts|tsx|vue)"'; then
  if [ -d "frontend/node_modules" ] && command -v npm >/dev/null 2>&1; then
    (cd frontend && npm run --silent typecheck) || echo "WARNING: frontend typecheck reported issues. Fix before committing." >&2
  fi
fi

# Backend Java changes: remind or verify pom syntax / compilation lightly (never run slow full tests on every keystroke)
if printf '%s' "$PAYLOAD" | grep -Eq '"file_path"[[:space:]]*:[[:space:]]*"[^"]*backend/src/[^"]*\.java"'; then
  if [ -x "backend/mvnw" ]; then
    echo "Reminder: Java source updated. Run 'cd backend && ./mvnw test' before committing." >&2
  fi
fi

exit 0
