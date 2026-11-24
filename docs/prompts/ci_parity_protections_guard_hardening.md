# Prompt & Execution Plan: CI Parity, GitLab Protections & Bash-Guard Hardening

> **Purpose:** Drop this prompt into Claude Code, Antigravity (`agy`), Codex, or Kiro Crew to finish the dual-forge housekeeping before any further harness upgrades. It closes three gaps found on 2026-11-24 while consolidating PR #5 / MR !4: (1) GitHub CI is weaker than GitLab CI, (2) GitLab branches are unprotected, and (3) the agent Bash guard can be bypassed through command wrappers, including `.env` reads.
>
> **Core Rule:** Spec first. Create the OpenSpec change `openspec/changes/ci-parity-guard-hardening/` (`proposal.md`, `specs/ci-parity-guard-hardening/spec.md` in EARS notation, `design.md`, `tasks.md`, `.openspec.yaml`). Implement on `chore/ci-parity-guard-hardening` off `dev` in atomic Conventional Commits (never `git add && git commit`, never `git commit -a`). Verify every claim with a real exit code, run a cold read-only `reviewer` audit, then follow the dual-forge flow in **§5**.

---

## 0. Verified Starting State (2026-11-24)

| Fact | Value |
|---|---|
| Branches on both forges | `master`, `alpha`, `dev` only. `dev` = `46073d7` on GitHub **and** GitLab (`node scripts/forge.mjs status` reports 0/0). |
| Open PRs / MRs | None. |
| `GIT_FORGE` | `both` (GitHub `origin` = primary, GitLab `gitlab` = secondary course repo, Decision 1.8). |
| GitLab project | id `6748`, API path `alpac001/nifty`. The git URL path is `01/alpac001/nifty`, so **never** URL-encode the git path for the API. `glab` env: `GITLAB_HOST=git.inf.fh-dortmund.de`, `-R alpac001/nifty`. |
| GitLab protections | Only `master` is protected (imported default). `dev` and `alpha` are **unprotected**; `only_allow_merge_if_pipeline_succeeds=false`. |
| GitLab runner | Tags `docker` + `dind`, privileged docker:dind works. Container Registry is **off**, so `$CI_REGISTRY_IMAGE` is empty. |
| RTK | v1.2.1 installed per developer (`/opt/homebrew/bin/rtk`), with a global Claude Code hook that rewrites `git …` to `rtk git …`. |
| Archived work | Local tag `archive/fe-mvp` → `97cf21b` (shelved frontend MVP; exists only on the author's machine). |

Read before starting: `AGENTS.md`, `.agents/rules/git-workflow.md`, `.agents/rules/coding-standards.md`, `docs/workflow.md` ("Working With Both Forges"), `docs/decisions/index.md` (1.7, 1.8, 3.4), `.gitlab-ci.yml`, `.github/workflows/ci.yml`, `.github/rulesets/*.json`, `scripts/apply-github-rulesets.sh`, `scripts/apply-gitlab-protections.sh`, `.claude/hooks/lib.mjs`, `.claude/hooks/guard-bash.mjs`, `.claude/hooks/hooks.test.mjs`, `.githooks/pre-push`.

---

## 1. Workstream A — CI Parity (GitHub must gate what GitLab gates)

### Why
Merges happen **only on GitHub**; GitLab receives them afterwards through `node scripts/forge.mjs sync`. GitLab's pipeline is stricter: it also builds both Docker images (`backend-image`, `frontend-image` in the `package` stage). A Dockerfile regression can therefore pass GitHub, get merged, and only fail on GitLab **after** it is already in `dev` on both forges. Today the only defence is a human remembering to wait for GitLab. The gates must be equivalent so a green GitHub run means a green GitLab run.

A second, smaller gap: `Hook Tests (Node)` runs on GitHub but is **not** a required status check in any `.github/rulesets/*.json`, so a red hook-test run does not block a merge.

### What
1. Add two jobs to `.github/workflows/ci.yml` that run the **same commands** as GitLab:
   - `docker build --pull -t "nifty-backend:${{ github.sha }}" backend`
   - `docker build --pull --target prod -t "nifty-frontend:${{ github.sha }}" frontend`
   Job display names (these become status-check contexts): `Backend Image (Docker)` and `Frontend Image (Docker)`.
2. Add `Backend Image (Docker)`, `Frontend Image (Docker)` and `Hook Tests (Node)` to `required_status_checks` in `dev.json`, `alpha.json` and `main.json`.
3. Re-apply the rulesets on GitHub with `scripts/apply-github-rulesets.sh` (needs repo admin; ask the user before running, it is outward-facing).
4. Add a CI parity table to `docs/workflow.md` (GitHub job name ↔ GitLab job name) and a one-line rule: *"Any job added to one CI file must be added to the other in the same PR."*
5. Add a zero-dependency drift test to `scripts/harness-scripts.test.mjs`: read both CI files as text, extract every `docker build` command line and every `./mvnw` / `npm run` / `node --test` command, normalise `${{ github.sha }}` and `$CI_COMMIT_SHORT_SHA` to one placeholder, and assert both sets are equal. No YAML parser dependency (the harness is zero-dependency); line-based extraction is enough.
6. Record the decision: `node scripts/record-decision.mjs --category 1 --topic "CI parity: GitHub CI gates the same jobs as GitLab CI" --trigger prompter-change …` and add a `CHANGELOG.md` entry.

### EARS requirements (put in `spec.md`)
- **REQ-CP-01:** WHEN a pull request targets `dev`, `alpha`, `main` or `master` on GitHub, THE SYSTEM SHALL build the backend image and the production frontend image with the same `docker build` commands used by `.gitlab-ci.yml`.
- **REQ-CP-02:** WHERE a GitHub ruleset protects `dev`, `alpha` or `main`/`master`, THE SYSTEM SHALL require the `Backend Image (Docker)`, `Frontend Image (Docker)` and `Hook Tests (Node)` checks to pass before merge.
- **REQ-CP-03:** IF the set of build/test/image commands in `.github/workflows/ci.yml` differs from `.gitlab-ci.yml`, THEN `node --test scripts/harness-scripts.test.mjs` SHALL fail and name the missing command.

### How (pitfalls)
- `ubuntu-latest` runners have Docker preinstalled; do **not** add `docker/setup-buildx-action` or a registry login. There is no push step on either forge (intentional; the registry is off on GitLab).
- Required-check contexts match the **job `name:`**, not the job key. A typo makes the ruleset wait forever for a check that never reports.
- `apply-github-rulesets.sh` replaces rulesets by name. Run it only after the new jobs have reported at least once on the PR, or the PR itself becomes unmergeable.
- The GitLab `default: tags: [docker, dind]` block stays; do not copy runner tags into GitHub.

---

## 2. Workstream B — Apply GitLab Branch Protections (ops task)

### Why
`dev` and `alpha` are unprotected on GitLab. Anyone with Developer access (or an agent) can push, force-push or merge there, which creates GitLab-only commits that `forge.mjs sync` cannot fast-forward over. That is exactly how GitLab `dev` drifted to `3f6b8b9` and needed a manual force reset on 2026-11-24. It is also the **only server-side backstop** for the `--no-verify` bypass described in Workstream C, because GitHub rulesets already block that path on GitHub but nothing blocks it on GitLab.

### What
1. `scripts/apply-gitlab-protections.sh --dry-run`. Review the printed API calls.
2. Ask the user to confirm, then run `scripts/apply-gitlab-protections.sh` (needs `glab` authenticated with **Maintainer** rights). It will:
   - set `only_allow_merge_if_pipeline_succeeds=true`;
   - protect `master`, `main`, `alpha`, `dev` (push + merge: Maintainers, `allow_force_push=false`);
   - try approval rules (master/main ×4, alpha ×2). These are **Premium-only**; on the FH instance expect a `WARN … skipping` line, which is fine.
3. Verify read-only: `GITLAB_HOST=git.inf.fh-dortmund.de glab api projects/6748/protected_branches` and `… glab api projects/6748 | node -e "…only_allow_merge_if_pipeline_succeeds"`.
4. Check off the last task in `openspec/changes/dual-forge/tasks.md` and note the outcome (including the Premium warning) in its Handoff Log. Then archive the `dual-forge` change with the `openspec-archive-change` skill if all its tasks are done.
5. Document in `docs/workflow.md` (both-forges section): only a GitLab **Maintainer** can run `forge.mjs sync`, because it pushes to protected `dev`/`alpha`/`master`.

### EARS requirements
- **REQ-GP-01:** WHERE the GitLab mirror is in use, THE SYSTEM SHALL protect `dev`, `alpha`, `master` and `main` against force pushes and non-Maintainer pushes.
- **REQ-GP-02:** WHEN a merge request is merged on GitLab, THE SYSTEM SHALL require a successful pipeline.

### How (pitfalls)
- Protecting `dev` means a future emergency reset like the 2026-11-24 one requires temporarily unprotecting the branch in the GitLab UI. That friction is intended.
- Protecting a branch name that does not exist yet (`main`) is allowed and harmless.
- `GITLAB_HOST` must be `git.inf.fh-dortmund.de` **without** `/01`; with `/01` the keyring lookup misses and glab returns 401.

---

## 3. Workstream C — Harden the Agent Bash Guard Against Command Wrappers

### Why
`.claude/hooks/guard-bash.mjs` only recognises a command when `git` (or `cat`, etc.) is the **first token** after `stripLeadingEnvAssignments()` in `.claude/hooks/lib.mjs` removes env assignments and the `env` / `command` / `rtk [flags] [proxy|bypass]` wrappers. Any other wrapper hides the real command. Probed on 2026-11-24 with the test suite's `cleanOpts` (`currentBranch: 'feat/x'`); all of these are currently **allowed**:

| Class | Allowed today |
|---|---|
| RTK subcommands that run a command | `rtk err git push origin master`, `rtk test …`, `rtk summary …`, `rtk run git push origin master`, `rtk run -- git push …`, `rtk run -c "git push origin master"` |
| Shell `-c` strings | `sh -c "git push origin master"`, `bash -c "git commit -m x"`, **`sh -c "cat .env"`**, **`rtk run -c "cat .env"`** |
| Generic prefixes | `eval git push …`, `xargs git push …`, `nice git push …`, `time git push …`, `sudo git push …` |
| Hook skipping | `git push --no-verify origin dev` (skips `.githooks/pre-push`) |

The `.env` rows are the serious ones: they leak `KICONNECT_API_KEY` into the model context, and no other layer catches them. The push rows are partly backstopped (§3 "Layers"), but the guard is the only layer that sees agent intent before execution.

The existing comment in `lib.mjs` (`shortcut: wrapper flags that take a separate value … are not skipped`) is now known to be **wrong for RTK**: `rtk run -c <COMMAND>` and `rtk run --shell <SHELL>` take values. RTK's **global** flags (`-v…`, `--skip-env`, `-h`, `-V`) take none (checked with `rtk --help`, v1.2.1).

### Layers (keep this model in `docs/setup.md`)
1. **Claude PreToolUse guard** (`guard-bash.mjs`): agents only, before execution. This workstream.
2. **Git hooks** (`.githooks/pre-push`, pre-commit): any client, but skippable with `--no-verify`.
3. **Server**: GitHub rulesets (active), GitLab protected branches (**Workstream B**).

### What
1. Extend `stripLeadingEnvAssignments` (keep the name and single shared location; coding standard #5, no second helper) to also peel `nice`, `time`, `sudo` (with flags), `xargs` (with flags), and RTK's command-running subcommands `err`, `test`, `summary`, `run` (with `--`, `--skip-env`, and the valued `--shell <SHELL>`).
2. Recursively evaluate command **strings**: for `sh|bash|zsh -c "<s>"`, `rtk run -c|--command "<s>"` and `eval <args…>`, run the full `evaluateBashCommand` on `<s>` (or on the joined `eval` args) and block if the inner evaluation blocks. Cap the recursion depth (e.g. 3) and **block** when the cap is hit.
3. Block `git push --no-verify` (and `-n`, not to be confused with `--dry-run`) when any destination is `dev`, `alpha`, `master` or `main`, on any remote. `node scripts/forge.mjs sync` is unaffected: it spawns git itself, and the guard only sees `node scripts/forge.mjs sync`.
4. Replace the wrong `shortcut:` comment with the accurate remaining limit.
5. Tests first (`mattpocock-skills:tdd` / `superpowers:test-driven-development`): add every row of the table above to `hooks.test.mjs` as must-block. Add must-allow controls: `rtk git status`, `rtk err npm run build`, `sh -c "npm run lint"`, `time ./mvnw -B test`, `git push origin feat/x`, `git push --no-verify origin feat/x`, and `git commit -m "docs: mention sh -c and .env"` (message text must not trigger).
6. Update the RTK paragraphs in `README.md` and `docs/setup.md` so they do not overclaim ("guards see through `rtk` wrappers and `-c` strings up to depth N").

### EARS requirements
- **REQ-BG-01:** WHEN an agent Bash command wraps a guarded command in `env`, `command`, `nice`, `time`, `sudo`, `xargs`, `eval`, or an RTK subcommand (`git`, `gh`, `glab`, `err`, `test`, `summary`, `run`, `proxy`, `bypass`), THE SYSTEM SHALL evaluate the wrapped command exactly as if it were run directly.
- **REQ-BG-02:** WHEN an agent Bash command passes a command string to `sh`, `bash` or `zsh` via `-c`, or to `rtk run` via `-c`/`--command`, THE SYSTEM SHALL evaluate that string with the same guard rules.
- **REQ-BG-03:** IF nested command strings exceed the recursion cap, THEN THE SYSTEM SHALL block the command.
- **REQ-BG-04:** IF an agent runs `git push` with `--no-verify` or `-n` targeting `dev`, `alpha`, `master` or `main`, THEN THE SYSTEM SHALL block it.
- **REQ-BG-05:** WHILE a wrapper runs an unguarded command (e.g. `rtk git status`, `time ./mvnw -B test`), THE SYSTEM SHALL allow it.

### How (pitfalls)
- The tokenizer already handles quotes (`"rtk" git push` is blocked), so extract the `-c` argument's **unquoted value** from the token, not from the raw string.
- Commit-message text is data. The existing test "allows … commit messages mentioning .env" must keep passing; do not grep the raw command for `git push`.
- `guard-agent.mjs` also calls `stripLeadingEnvAssignments`; run the whole `hooks.test.mjs`, not just the guard-bash suite.
- RTK's own hook rewrites the agent's Bash input **before** our guard runs, and it may drop a leading `cd <dir> &&` (observed 2026-11-24). Agents should use absolute paths or run from the repo root instead of relying on `cd` chains.

---

## 4. Workstream D — Leftover Housekeeping (user decisions, no code)

- **`archive/fe-mvp` tag:** ask the user whether to (a) push it for a remote backup (`git push origin archive/fe-mvp`, plus `git push gitlab archive/fe-mvp` for parity), or (b) leave it local. Never delete it without asking. Reusable pieces of it (mock endpoints, Blinkit tokens, auth flag) come back only through a new OpenSpec change.
- **fe-mvp Docker stack:** `docker compose ps` was empty on 2026-11-24 (already stopped). If it shows containers, ask before `docker compose down` (add `-v` only if the user wants the DB volume gone).

---

## 5. Delivery Flow (lessons from 2026-11-24, follow exactly)

1. Branch `chore/ci-parity-guard-hardening` off an up-to-date `dev` (`git checkout dev && git pull origin dev`).
2. Commit per workstream (A: `ci(github): …`, `test(harness): …`; C: `fix(hooks): …`; docs: `docs(…): …`; spec/tasks: `chore(specs): …`).
3. Verify: `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` and `OPENSPEC_TELEMETRY=0 openspec validate --all`. Backend/frontend are untouched, so only run `./mvnw test` / `npm run …` if you touch them.
4. Cold review with the `reviewer` agent on `git diff origin/dev...HEAD` (use `origin/dev`; local `dev` may be stale). Resolve all **Blocking** items.
5. `node scripts/forge.mjs push`, then `node scripts/forge.mjs pr --title "<conventional title>" --body-file <file>` with a body from the `pr-memo` skill (`.github/PULL_REQUEST_TEMPLATE.md`).
6. **Gate on GitLab first:** wait until the GitLab MR pipeline is green (`glab api projects/6748/merge_requests/<iid>/pipelines`), then confirm GitHub checks are green (`gh pr checks <n>`).
7. Merge **on GitHub only**, with a merge commit (`gh pr merge <n> --merge`, never squash/rebase, so GitLab can detect the MR as merged). Never merge on GitLab.
8. `node scripts/forge.mjs sync dev`, then `node scripts/forge.mjs status` must print `GitHub ahead 0, GitLab ahead 0` for every branch. The GitLab MR flips to **merged** by itself.
9. Delete the feature branch on both remotes and locally, after `git merge-base --is-ancestor <branch> origin/dev` confirms it is merged.
10. Run Workstream B's real protection run **after** this merge if it was not done earlier, so the protected `dev` does not block step 8 for a non-Maintainer.

## 6. Definition of Done

- [ ] REQ-CP-01..03, REQ-GP-01..02, REQ-BG-01..05 each map to a passing test or a recorded verification command in `tasks.md`.
- [ ] Every probe in the Workstream C table is blocked; every must-allow control passes.
- [ ] GitHub rulesets re-applied; GitLab protections applied; `dual-forge` change archived.
- [ ] Decision recorded; `CHANGELOG.md`, `docs/workflow.md`, `docs/setup.md`, `README.md` updated.
- [ ] Merged via §5; `forge.mjs status` 0/0; only `master`, `alpha`, `dev` remain on both forges.
