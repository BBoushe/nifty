# Tasks: Forge CI Parity & GitLab Protections

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `chore/forge-ci-parity`
- **Worktree:** `/Users/alek/Developer/Programming/nifty-forge-ci-parity`
- **Current:** Delivery: GitHub rulesets applied (3 new required checks verified live + on PR #10)
- **Next action:** cold review (reviewer agent) → GitLab MR pipeline green → ask user, then merge PR #10 on GitHub
- **Uncommitted changes:** no
- **PR / MR:** GitHub https://github.com/BBoushe/nifty/pull/10 · GitLab https://git.inf.fh-dortmund.de/01/alpac001/nifty/-/merge_requests/9
- **Open review findings:** —

## Tasks

Owned files: `.github/workflows/ci.yml`, `.github/rulesets/*.json`, `.gitlab-ci.yml` (only if parity needs it), `docs/workflow.md`, the `CI parity` describe in `scripts/harness-scripts.test.mjs`, `openspec/changes/dual-forge/`. Decision 1.9 is already recorded; do not re-record it. Read `design.md` first.

- [x] **1. Drift test (RED)** — REQ-CP-03
  - [x] Add `describe('CI parity')` with `it('runs the same build, test and image commands on GitHub and GitLab')` per design §3, and `it('names the missing command')`, which feeds two small inline YAML strings to the same helper and asserts the message.
  - [x] Run `node --test scripts/harness-scripts.test.mjs`. Expect: FAIL naming `docker build --pull -t "nifty-backend:<SHA>" backend` and the frontend line as missing on GitHub.
  - [x] Commit `test(harness): add CI parity drift test`.
- [x] **2. GitHub image jobs (GREEN)** — REQ-CP-01
  - [x] Add the two jobs per design §1.
  - [x] Run `node --test scripts/harness-scripts.test.mjs`. Expect: exit 0.
  - [x] Locally (if Docker is running): `docker build --pull -t nifty-backend:local backend` and `docker build --pull --target prod -t nifty-frontend:local frontend`. Expect: exit 0.
  - [x] Commit `ci(github): build backend and frontend images like GitLab`.
- [x] **3. Required checks** — REQ-CP-02
  - [x] Add the three contexts to `dev.json`, `alpha.json`, `main.json` (design §2).
  - [x] Run `node -e "for (const f of ['dev','alpha','main']) JSON.parse(require('fs').readFileSync('.github/rulesets/'+f+'.json'))"`. Expect: exit 0.
  - [x] Commit `ci(github): require image and hook-test checks in rulesets`.
- [x] **4. Docs**
  - [x] `docs/workflow.md` per design §5. Add a `CHANGELOG.md` `[Unreleased]` entry.
  - [x] Commit `docs(workflow): add CI parity table and Maintainer-only sync note`.
- [x] **5. Apply GitLab protections (ops — ask the user first)** — REQ-GP-01, REQ-GP-02
  - [x] Confirm with the user that their GitLab account is a **Maintainer** of project 6748. If yes, run this task before merge (a Maintainer can still run `forge.mjs sync` to protected `dev`). If not, stop and ask the user how to proceed.
  - [x] `scripts/apply-gitlab-protections.sh --dry-run`; show the output to the user.
  - [x] On confirmation, run it for real (design §4). Record the Premium `WARN` line.
  - [x] Verify with the two read-only `glab api` commands in design §4. Expect: `dev`, `alpha`, `master`, `main` listed with `allow_force_push: false`; `true` for the pipeline setting.
  - [x] Check off the open task in `openspec/changes/dual-forge/tasks.md` and add a Handoff Log entry there with the outcome.
  - [x] Commit `chore(specs): complete dual-forge protections task`.
- [x] **6. Housekeeping question (no code)**
  - [x] Ask the user about the local tag `archive/fe-mvp` → `97cf21b`: (a) push it for a remote backup to both remotes, or (b) leave it local. Never delete it. Record the answer in the Handoff Log.
  - [x] If `docker compose ps` shows fe-mvp containers, ask before `docker compose down` (`-v` only if the user wants the DB volume gone).

## Delivery

- [x] `git fetch origin && git rebase origin/dev`. On conflict in `CHANGELOG.md`, keep both entries; in `scripts/harness-scripts.test.mjs`, keep both describe blocks.
- [x] Re-run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
- [x] Archive both changes: `OPENSPEC_TELEMETRY=0 openspec archive forge-ci-parity -y --skip-specs` and `OPENSPEC_TELEMETRY=0 openspec archive dual-forge -y --skip-specs`. Continue in `openspec/changes/archive/*-forge-ci-parity/tasks.md`. Commit `chore(specs): archive forge-ci-parity and dual-forge`.
- [x] If `docs/feature_history.md` exists after the rebase, add this change's entry (format in its header; the harness test enforces it) and amend it into the archive commit.
- [x] `node scripts/forge.mjs push`
- [x] PR/MR body via `pr-memo`, then `node scripts/forge.mjs pr --title "ci: gate GitHub on the same jobs as GitLab" --body-file <file>`. Record the links in Status.
- [x] **Apply GitHub rulesets (ops — ask the user first)** once the two image jobs have reported on this PR: `scripts/apply-github-rulesets.sh`. Verify: `gh pr checks <n>` lists `Backend Image (Docker)`, `Frontend Image (Docker)` and `Hook Tests (Node)` as required.
- [ ] Cold review session: verdict has no Blocking items.
- [ ] **GitLab first:** MR pipeline green (`glab api projects/6748/merge_requests/<iid>/pipelines`), then `gh pr checks <n>` green.
- [ ] Merge on GitHub only: `gh pr merge <n> --merge`.
- [ ] `node scripts/forge.mjs sync dev` (Maintainer), then `node scripts/forge.mjs status` prints 0/0 for every branch. The GitLab MR flips to merged by itself.
- [ ] After `git merge-base --is-ancestor chore/forge-ci-parity origin/dev`: delete the branch on both remotes and locally; `git worktree remove ../nifty-forge-ci-parity`. Only `master`, `alpha`, `dev` remain on both forges.

## Handoff Log

### Task 1 — Drift test (RED)
- **Commits:** `test(harness): add CI parity drift test`
- **Verification:** RED `node --test scripts/harness-scripts.test.mjs` → FAIL (13 pass, 1 fail: missing on GitHub `docker build --pull -t "nifty-backend:<SHA>" backend` + frontend line); GREEN → pending Task 2.
- **Contracts/Migrations added:** none
- **Decisions:** none (1.9 already recorded); helpers stay local to the test file.
- **Open Questions:** none

### Task 2 — GitHub image jobs (GREEN)
- **Commits:** `ci(github): build backend and frontend images like GitLab`
- **Verification:** RED `node --test scripts/harness-scripts.test.mjs` → FAIL (Task 1); GREEN `node --test scripts/harness-scripts.test.mjs` → exit 0; local `docker build --pull -t nifty-backend:local backend` and `docker build --pull --target prod -t nifty-frontend:local frontend` → exit 0.
- **Contracts/Migrations added:** none
- **Decisions:** none; user approved pushing after every task; user is GitLab Maintainer of 6748; `archive/fe-mvp` stays local.
- **Open Questions:** none

### Task 3 — Required checks
- **Commits:** `ci(github): require image and hook-test checks in rulesets`
- **Verification:** RED n/a (config-only; the three contexts were absent before); GREEN `node -e "for (const f of ['dev','alpha','main']) JSON.parse(...)"` → exit 0; each file now lists `Backend Image (Docker)`, `Frontend Image (Docker)`, `Hook Tests (Node)`.
- **Contracts/Migrations added:** none (rulesets not applied; Delivery step, after the image jobs report on the PR)
- **Decisions:** `strict` left unchanged per file.
- **Open Questions:** none

### Task 4 — Docs
- **Commits:** `docs(workflow): add CI parity table and Maintainer-only sync note`
- **Verification:** RED n/a (docs-only); GREEN `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0.
- **Contracts/Migrations added:** none
- **Decisions:** none
- **Open Questions:** none

### Task 5 — Apply GitLab protections (ops)
- **Commits:** `chore(specs): complete dual-forge protections task`
- **Verification:** RED n/a (ops); dry-run → exit 0 (shown to user, confirmed); real run → exit 0; `glab api projects/6748/protected_branches` → `master`/`main`/`alpha`/`dev`, push+merge 40, `allow_force_push: false`; `only_allow_merge_if_pipeline_succeeds` → `true`.
- **Contracts/Migrations added:** GitLab project 6748 settings (server-side)
- **Decisions:** user is Owner (access 50) of 6748, so applied before merge. Premium WARN logged: approval rules skipped for master x4, main x4, alpha x2.
- **Open Questions:** none

### Task 6 — Housekeeping (no code)
- **Commits:** none (no code; recorded in the archive commit)
- **Verification:** `docker compose ps` → 0 services; `docker ps -a` → no nifty/fe-mvp containers, so nothing to stop.
- **Contracts/Migrations added:** none
- **Decisions:** user: leave tag `archive/fe-mvp` → `97cf21b` local (not pushed, not deleted).
- **Open Questions:** none

### Delivery — rulesets applied (ops)
- **Commits:** `chore(specs): record forge-ci-parity delivery status`
- **Verification:** `scripts/apply-github-rulesets.sh BBoushe/nifty` → exit 0 (updated ids 27855088/27855090/27855091); `gh pr checks 10 --required` lists `Backend Image (Docker)`, `Frontend Image (Docker)`, `Hook Tests (Node)` (all pass).
- **Contracts/Migrations added:** live GitHub rulesets (server-side)
- **Decisions:** user approved applying after both image jobs reported green on PR #10.
- **Open Questions:** none
