# Design: Forge CI Parity & GitLab Protections

## 1. GitHub Image Jobs (`.github/workflows/ci.yml`)

| Job key | `name:` (= status-check context) | Command |
|---|---|---|
| `backend-image` | `Backend Image (Docker)` | `docker build --pull -t "nifty-backend:${{ github.sha }}" backend` |
| `frontend-image` | `Frontend Image (Docker)` | `docker build --pull --target prod -t "nifty-frontend:${{ github.sha }}" frontend` |

- `runs-on: ubuntu-latest` (Docker preinstalled). Steps: `actions/checkout@v4`, then the command. No `docker/setup-buildx-action`, no registry login, no push.
- Do not copy GitLab runner tags (`docker`, `dind`) into GitHub.
- Required-check contexts match the job `name:`, not the key. A typo makes the ruleset wait forever.

## 2. Rulesets

Add `Backend Image (Docker)`, `Frontend Image (Docker)` and `Hook Tests (Node)` to `required_status_checks` in `dev.json`, `alpha.json` and `main.json`. Keep `strict` as it is in each file.

`scripts/apply-github-rulesets.sh` replaces rulesets by name. Run it **only after** the new jobs have reported at least once on this PR. Otherwise this PR becomes unmergeable.

## 3. Drift Test (`scripts/harness-scripts.test.mjs`, new `describe('CI parity')`)

Zero dependencies; read both files as text, line by line.

1. Keep lines (after trimming a leading `- `, `run:` or `script:`) that contain `docker build`, `./mvnw`, `npm ci`, `npm run`, or `node --test`.
2. Normalise each line:
   - strip a leading `cd <dir> &&`;
   - map `${{ github.sha }}` and `$CI_COMMIT_SHORT_SHA` to `<SHA>`;
   - drop the GitLab-only flags `--cache .npm --prefer-offline`;
   - collapse whitespace.
3. Compare as sets. On a difference, `assert.fail` names each command missing per file.

Export nothing new. The helper functions stay local to the test file.

## 4. GitLab Protections (ops)

- `scripts/apply-gitlab-protections.sh --dry-run`. Show the printed API calls to the user. On confirmation, run it without `--dry-run`. It needs `glab` with **Maintainer** rights, `GITLAB_HOST=git.inf.fh-dortmund.de` (no `/01`), and project id `6748` (API path `alpac001/nifty`; never URL-encode the git path).
- Expect a `WARN … skipping` line for the Premium-only approval rules. This is fine; log it.
- Run it before merge only when the operator is a GitLab **Maintainer** (Maintainers can still push to protected `dev`, so `forge.mjs sync` keeps working). Otherwise run it after the merge and sync.
- Verify read-only: `GITLAB_HOST=git.inf.fh-dortmund.de glab api projects/6748/protected_branches` and `GITLAB_HOST=git.inf.fh-dortmund.de glab api projects/6748 | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>console.log(JSON.parse(s).only_allow_merge_if_pipeline_succeeds))"` → `true`.
- Future emergency resets of `dev` need a temporary unprotect in the GitLab UI. That friction is intended.

## 5. Docs (`docs/workflow.md`, "Working With Both Forges")

- CI parity table: GitHub job name ↔ GitLab job name (`Backend (Java 21 & Maven)` ↔ `backend`, `Frontend (Vue 3.5 & Vite 8)` ↔ `frontend`, `Hook Tests (Node)` ↔ `harness-tests`, `Conventional Commits` ↔ `conventional-commits`, `Branch Promotion Policy` ↔ `branch-policy`, `Backend Image (Docker)` ↔ `backend-image`, `Frontend Image (Docker)` ↔ `frontend-image`).
- Rule: *"Any job added to one CI file must be added to the other in the same PR."* (the drift test enforces the commands).
- Note: only a GitLab **Maintainer** can run `forge.mjs sync`, because it pushes to protected branches.
