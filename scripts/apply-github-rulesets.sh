#!/usr/bin/env bash
# Applies declarative GitHub Branch Protection Rulesets from .github/rulesets/*.json
# Requires: GitHub CLI (gh) authenticated with repository admin permissions.
# Note: On GitHub Free private repositories, Rulesets API may return HTTP 403
# (GitHub Pro/Team or public repository required).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RULESETS_DIR="${REPO_ROOT}/.github/rulesets"

if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: GitHub CLI (gh) is not installed. Install from https://cli.github.com/" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "ERROR: gh is not authenticated. Run 'gh auth login' first." >&2
  exit 1
fi

REPO="${1:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
echo "Applying GitHub Rulesets to ${REPO}..."

EXISTING_JSON="$(gh api "repos/${REPO}/rulesets" 2>&1)" || {
  echo "ERROR: Could not read rulesets for ${REPO}:" >&2
  echo "${EXISTING_JSON}" >&2
  exit 1
}

for file in "${RULESETS_DIR}/main.json" "${RULESETS_DIR}/alpha.json" "${RULESETS_DIR}/dev.json"; do
  name="$(node -e "console.log(JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')).name)" "$file")"
  existing_id="$(node -e "const list = JSON.parse(process.argv[1]); const match = list.find(r => r.name === process.argv[2]); console.log(match ? match.id : '');" "$EXISTING_JSON" "$name")"

  if [ -n "$existing_id" ]; then
    echo "Updating ruleset '${name}' (id=${existing_id})..."
    gh api --method PUT "repos/${REPO}/rulesets/${existing_id}" --input "$file" >/dev/null
  else
    echo "Creating ruleset '${name}'..."
    gh api --method POST "repos/${REPO}/rulesets" --input "$file" >/dev/null
  fi
  echo "  OK: ${name}"
done

echo "All rulesets applied cleanly to ${REPO}."
echo "Note: For optional docs:/hotfix: direct pushes to 'dev', repo admins may add a bypass actor in GitHub UI;"
echo "local .githooks/pre-push and CI 'Branch Promotion Policy' enforce the docs:/hotfix: prefix restriction."
