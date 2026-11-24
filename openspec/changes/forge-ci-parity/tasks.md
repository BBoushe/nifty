# Tasks: Forge CI Parity & GitLab Protections

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `chore/forge-ci-parity` (not created)
- **Worktree:** —
- **Current:** not started
- **Next action:** Task 1, first step
- **Uncommitted changes:** no
- **PR / MR:** —
- **Open review findings:** —

## Tasks

Owned files: `.github/workflows/ci.yml`, `.github/rulesets/*.json`, `.gitlab-ci.yml` (only if parity needs it), `docs/workflow.md`, the `CI parity` describe in `scripts/harness-scripts.test.mjs`, `openspec/changes/dual-forge/`. Decision 1.9 is already recorded; do not re-record it. Read `design.md` first.

- [ ] **1. Drift test (RED)** — REQ-CP-03
  - [ ] Add `describe('CI parity')` with `it('runs the same build, test and image commands on GitHub and GitLab')` per design §3, and `it('names the missing command')`, which feeds two small inline YAML strings to the same helper and asserts the message.
  - [ ] Run `node --test scripts/harness-scripts.test.mjs`. Expect: FAIL naming `docker build --pull -t "nifty-backend:<SHA>" backend` and the frontend line as missing on GitHub.
  - [ ] Commit `test(harness): add CI parity drift test`.
- [ ] **2. GitHub image jobs (GREEN)** — REQ-CP-01
  - [ ] Add the two jobs per design §1.
  - [ ] Run `node --test scripts/harness-scripts.test.mjs`. Expect: exit 0.
  - [ ] Locally (if Docker is running): `docker build --pull -t nifty-backend:local backend` and `docker build --pull --target prod -t nifty-frontend:local frontend`. Expect: exit 0.
  - [ ] Commit `ci(github): build backend and frontend images like GitLab`.
- [ ] **3. Required checks** — REQ-CP-02
  - [ ] Add the three contexts to `dev.json`, `alpha.json`, `main.json` (design §2).
  - [ ] Run `node -e "for (const f of ['dev','alpha','main']) JSON.parse(require('fs').readFileSync('.github/rulesets/'+f+'.json'))"`. Expect: exit 0.
  - [ ] Commit `ci(github): require image and hook-test checks in rulesets`.
- [ ] **4. Docs**
  - [ ] `docs/workflow.md` per design §5. Add a `CHANGELOG.md` `[Unreleased]` entry.
  - [ ] Commit `docs(workflow): add CI parity table and Maintainer-only sync note`.
- [ ] **5. Apply GitLab protections (ops — ask the user first)** — REQ-GP-01, REQ-GP-02
  - [ ] Confirm with the user that their GitLab account is a **Maintainer** of project 6748. If yes, run this task before merge (a Maintainer can still run `forge.mjs sync` to protected `dev`). If not, stop and ask the user how to proceed.
  - [ ] `scripts/apply-gitlab-protections.sh --dry-run`; show the output to the user.
  - [ ] On confirmation, run it for real (design §4). Record the Premium `WARN` line.
  - [ ] Verify with the two read-only `glab api` commands in design §4. Expect: `dev`, `alpha`, `master`, `main` listed with `allow_force_push: false`; `true` for the pipeline setting.
  - [ ] Check off the open task in `openspec/changes/dual-forge/tasks.md` and add a Handoff Log entry there with the outcome.
  - [ ] Commit `chore(specs): complete dual-forge protections task`.
- [ ] **6. Housekeeping question (no code)**
  - [ ] Ask the user about the local tag `archive/fe-mvp` → `97cf21b`: (a) push it for a remote backup to both remotes, or (b) leave it local. Never delete it. Record the answer in the Handoff Log.
  - [ ] If `docker compose ps` shows fe-mvp containers, ask before `docker compose down` (`-v` only if the user wants the DB volume gone).

## Delivery

- [ ] `git fetch origin && git rebase origin/dev`. On conflict in `CHANGELOG.md`, keep both entries; in `scripts/harness-scripts.test.mjs`, keep both describe blocks.
- [ ] Re-run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
- [ ] Archive both changes: `OPENSPEC_TELEMETRY=0 openspec archive forge-ci-parity -y --skip-specs` and `OPENSPEC_TELEMETRY=0 openspec archive dual-forge -y --skip-specs`. Continue in `openspec/changes/archive/*-forge-ci-parity/tasks.md`. Commit `chore(specs): archive forge-ci-parity and dual-forge`.
- [ ] If `docs/feature_history.md` exists after the rebase, add this change's entry (format in its header; the harness test enforces it) and amend it into the archive commit.
- [ ] `node scripts/forge.mjs push`
- [ ] PR/MR body via `pr-memo`, then `node scripts/forge.mjs pr --title "ci: gate GitHub on the same jobs as GitLab" --body-file <file>`. Record the links in Status.
- [ ] **Apply GitHub rulesets (ops — ask the user first)** once the two image jobs have reported on this PR: `scripts/apply-github-rulesets.sh`. Verify: `gh pr checks <n>` lists `Backend Image (Docker)`, `Frontend Image (Docker)` and `Hook Tests (Node)` as required.
- [ ] Cold review session: verdict has no Blocking items.
- [ ] **GitLab first:** MR pipeline green (`glab api projects/6748/merge_requests/<iid>/pipelines`), then `gh pr checks <n>` green.
- [ ] Merge on GitHub only: `gh pr merge <n> --merge`.
- [ ] `node scripts/forge.mjs sync dev` (Maintainer), then `node scripts/forge.mjs status` prints 0/0 for every branch. The GitLab MR flips to merged by itself.
- [ ] After `git merge-base --is-ancestor chore/forge-ci-parity origin/dev`: delete the branch on both remotes and locally; `git worktree remove ../nifty-forge-ci-parity`. Only `master`, `alpha`, `dev` remain on both forges.

## Handoff Log
