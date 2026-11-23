#!/usr/bin/env bash
# Applies the GitLab equivalent of .github/rulesets/*.json to the `gitlab` remote's project (Decision 1.8).
# Requires: GitLab CLI (glab) authenticated with Maintainer/Owner rights on the project.
# Usage: scripts/apply-gitlab-protections.sh [--dry-run]
#
# GitHub ruleset            -> GitLab setting
#   no force push / delete  -> protected branch (allow_force_push=false; protected branches cannot be deleted)
#   PR required             -> push/merge limited to Maintainers (pushes by the mirror `forge.mjs sync` need push access)
#   CI required             -> project setting "pipelines must succeed"
#   approvals (4 / 2 / 0)   -> approval rules for master+main (4) and alpha (2)  [Premium/Ultimate only]
# Re-running is safe: protections are replaced and approval rules updated by name.

set -euo pipefail

DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && DRY_RUN=1

URL="$(git remote get-url gitlab)" || { echo "ERROR: remote 'gitlab' is missing (git remote add gitlab <url>)." >&2; exit 1; }
# https://[user@]host[:port]/group/sub/repo(.git) or git@host:group/sub/repo(.git) -> host + path
HOST_AND_PATH="$(printf '%s' "$URL" | sed -E 's#^https?://([^@/]+@)?##; s#^[^@/]+@([^:]+):#\1/#; s#\.git/?$##')"
HOST="${HOST_AND_PATH%%/*}"
PROJECT_PATH="${HOST_AND_PATH#*/}"
PROJECT="projects/$(node -e "console.log(encodeURIComponent(process.argv[1]))" "$PROJECT_PATH")"

if [ "$DRY_RUN" -eq 0 ]; then
  command -v glab >/dev/null 2>&1 || { echo "ERROR: glab is not installed (https://gitlab.com/gitlab-org/cli)." >&2; exit 1; }
  glab auth status --hostname "$HOST" >/dev/null 2>&1 || { echo "ERROR: run 'glab auth login --hostname ${HOST}' first." >&2; exit 1; }
fi

api() { # api METHOD ENDPOINT [-F key=value ...]
  if [ "$DRY_RUN" -eq 1 ]; then echo "[dry-run] glab api --hostname $HOST --method $*" >&2; return 0; fi
  glab api --hostname "$HOST" --method "$@"
}

json() { node -e "const d=JSON.parse(process.argv[1]); console.log($2)" "$1" "${3:-}"; }

echo "Applying GitLab protections to ${HOST}/${PROJECT_PATH}..."

# CI must pass before merging (GitHub: required_status_checks).
api PUT "$PROJECT" -F only_allow_merge_if_pipeline_succeeds=true >/dev/null
echo "  OK: pipelines must succeed before merge"

# branch:required approvals (mirrors main.json=4, alpha.json=2, dev.json=0)
for entry in master:4 main:4 alpha:2 dev:0; do
  branch="${entry%%:*}"; approvals="${entry##*:}"

  # Replace any existing (e.g. imported default) protection with ours; 404 just means "was not protected".
  api DELETE "$PROJECT/protected_branches/$branch" >/dev/null 2>&1 || true
  created="$(api POST "$PROJECT/protected_branches" -F "name=$branch" -F push_access_level=40 -F merge_access_level=40 -F allow_force_push=false)"
  echo "  OK: protected '$branch' (push/merge: Maintainers, no force push)"

  [ "$approvals" -gt 0 ] || continue
  [ "$DRY_RUN" -eq 1 ] && { echo "[dry-run] approval rule '$branch' x$approvals" >&2; continue; }
  branch_id="$(json "$created" 'd.id')"
  rule_name="Nifty: $branch approvals"
  rules="$(api GET "$PROJECT/approval_rules" 2>/dev/null)" || { echo "  WARN: approval rules unavailable (GitLab Free?); skipping '$branch' x$approvals" >&2; continue; }
  rule_id="$(json "$rules" "(d.find(r => r.name === process.argv[2]) || {}).id || ''" "$rule_name" 2>/dev/null || true)"
  if [ -n "$rule_id" ]; then
    api PUT "$PROJECT/approval_rules/$rule_id" -F "approvals_required=$approvals" -F "protected_branch_ids[]=$branch_id" >/dev/null
  else
    api POST "$PROJECT/approval_rules" -F "name=$rule_name" -F "approvals_required=$approvals" -F "protected_branch_ids[]=$branch_id" >/dev/null
  fi
  echo "  OK: '$branch' needs $approvals approvals"
done

echo "Done. Note: GitLab approval rules need Premium/Ultimate; on Free only the branch protections and the pipeline rule apply."
echo "Docs:/hotfix:-only direct pushes to 'dev' are still enforced by local .githooks/pre-push and the CI 'branch-policy' job."
