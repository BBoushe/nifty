# Tasks: AI Context Docs & ASD-STE100

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `chore/ai-context-docs` (pushed to origin + gitlab)
- **Worktree:** `/Users/alek/Developer/Programming/nifty-ai-context-docs` (keep until the user cleans up)
- **Current:** Delivery — review APPROVE, CI green on 5278188 (GitHub 5/5, GitLab pipeline 8721); the user said: do not merge yet
- **Next action:** when the user approves: re-check `gh pr checks 11` (this commit reruns CI), `gh pr merge 11 --merge`, `node scripts/forge.mjs sync dev`, `node scripts/forge.mjs status`. The user does the branch/worktree cleanup.
- **Uncommitted changes:** no
- **PR / MR:** https://github.com/BBoushe/nifty/pull/11 · https://git.inf.fh-dortmund.de/01/alpac001/nifty/-/merge_requests/10
- **Open review findings:** 0 Blocking. Convention Candidates (deferred): `#feature-slice-map` anchor arrives with `backend-architecture-baseline`; backlog cites Decision 1.10 for Testcontainers (1.10 covers it) but not for GitLab dind; README `Version: index` line; in-flight harness changes have no backlog rows (design lists only domain specs + `docs-ste-rewrite`)

## Tasks

Owned files: `AGENTS.md` §3 dispatch table, `.agents/rules/spec-driven.md`, `docs/feature_history.md`, `docs/feature_backlog.md`, `docs/ai_docs/**`, `.agents/skills/asd-ste100/` + `.claude/skills/asd-ste100` symlink, new rows at the end of the `docs/setup.md` inventory table, the `feature history` describe in `scripts/harness-scripts.test.mjs`. Decisions 3.5 and 3.6 are already recorded. Read `design.md` first.

- [x] **1. History test (RED)** — REQ-CD-02
  - [x] Add `describe('feature history')` per design §2.
  - [x] Run `node --test scripts/harness-scripts.test.mjs`. Expect: FAIL naming the 4 archived folders (no history file yet).
  - [x] Commit `test(harness): require a history entry for every archived change`.
- [x] **2. Backfill the history (GREEN)** — REQ-CD-01
  - [x] Write `docs/feature_history.md` per design §1 for the 4 changes archived on 2026-11-24.
  - [x] Run `node --test scripts/harness-scripts.test.mjs`. Expect: exit 0.
  - [x] Commit `docs(history): add feature history backfilled from archived changes`.
- [x] **3. Backlog** — REQ-CD-03
  - [x] Write `docs/feature_backlog.md` per design §3.
  - [x] Commit `docs(backlog): add feature backlog with blast radius and blockers`.
- [x] **4. Library notes** — REQ-CD-04
  - [x] Write `docs/ai_docs/README.md` and the three notes per design §4. Each note has `Version:`, `Sources:` and `Retrieved:`, and is ≤150 lines.
  - [x] Check: `wc -l docs/ai_docs/*.md` (each ≤150); every version matches `backend/pom.xml`.
  - [x] Commit `docs(ai-docs): add curated Spring Boot 4, Spring AI 2 and Timefold notes`.
- [x] **5. ASD-STE100 skill (vet first)** — REQ-CD-06
  - [x] Vet the source per design §5 step 1. Record the result (accepted, or rejected with the reason) in the Handoff Log.
  - [x] If accepted: install, move, symlink (design §5 step 2). Run the linter on one doc (step 3). Expect: the linter runs and prints findings.
  - [x] Commit `chore(skills): add vetted asd-ste100 skill`.
- [x] **6. Rules and dispatch** — REQ-CD-05, REQ-CD-06
  - [x] Add rule 6 to `.agents/rules/spec-driven.md` and the three AGENTS.md §3 rows (design §6).
  - [x] Add inventory rows to the end of the `docs/setup.md` table for `docs/feature_history.md`, `docs/feature_backlog.md`, `docs/ai_docs/` and `.agents/skills/asd-ste100/`.
  - [x] Add a `CHANGELOG.md` `[Unreleased]` entry.
  - [x] Run `OPENSPEC_TELEMETRY=0 openspec validate --all` and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: both exit 0.
  - [x] Commit `docs(agents): dispatch ai_docs, backlog/history lifecycle and STE rule`.

## Delivery

- [x] `git fetch origin && git rebase origin/dev`. On conflict in `CHANGELOG.md`, keep both entries; in `scripts/harness-scripts.test.mjs`, `docs/setup.md` and `AGENTS.md`, keep both sides.
- [x] Backfill a history entry for every change archived on `dev` since task 2 (the test tells you which).
- [x] Re-run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive ai-context-docs -y --skip-specs`, then add its own history entry (the test requires it). Continue in `openspec/changes/archive/*-ai-context-docs/tasks.md`. Commit `chore(specs): archive ai-context-docs`.
- [x] `node scripts/forge.mjs push`
- [x] PR/MR body via `pr-memo`, then `node scripts/forge.mjs pr --title "docs: add feature history, backlog, ai_docs and STE rule" --body-file <file>`. Record the links in Status.
- [x] Cold review session: verdict has no Blocking items.
- [x] GitLab MR pipeline green; `gh pr checks <n>` green.
- [ ] Merge on GitHub only: `gh pr merge <n> --merge`.
- [ ] `node scripts/forge.mjs sync dev`, then `node scripts/forge.mjs status` prints 0/0 for every branch.
- [ ] After `git merge-base --is-ancestor chore/ai-context-docs origin/dev`: delete the branch on both remotes and locally; `git worktree remove ../nifty-ai-context-docs`.

## Handoff Log

### Task 1 — History test (RED)
- **Commits:** `test(harness): require a history entry for every archived change`
- **Verification:** RED `node --test scripts/harness-scripts.test.mjs` → FAIL (names the 4 archived folders); GREEN n/a (task 2)
- **Contracts/Migrations added:** none
- **Decisions:** none new (3.5, 3.6 already recorded)
- **Open Questions:** none

### Task 2 — Backfill the history (GREEN)
- **Commits:** `docs(history): add feature history backfilled from archived changes`
- **Verification:** RED `node --test scripts/harness-scripts.test.mjs` → FAIL (task 1); GREEN same cmd → exit 0
- **Contracts/Migrations added:** `docs/feature_history.md` (heading format `## YYYY-MM-DD — <change>`)
- **Decisions:** none new; PR mapping from `git log`: project-setup + docker-and-kiconnect → #1, harness-v2-upgrade + decisions-coding-rules-gitlab → #2
- **Open Questions:** none

### Task 3 — Backlog
- **Commits:** `docs(backlog): add feature backlog with blast radius and blockers`
- **Verification:** RED n/a (docs only, no test in tasks.md); GREEN `node --test scripts/harness-scripts.test.mjs` → exit 0
- **Contracts/Migrations added:** `docs/feature_backlog.md`; migration column lists planned tables only
- **Decisions:** none new; O-item → row mapping is my reading of `docs/decisions/index.md` (review it)
- **Open Questions:** `#feature-slice-map` anchor arrives with `backend-architecture-baseline`

### Task 4 — Library notes
- **Commits:** `docs(ai-docs): add curated Spring Boot 4, Spring AI 2 and Timefold notes`
- **Verification:** RED n/a (docs only); GREEN `wc -l docs/ai_docs/*.md` → 28/149/136/149 (all ≤150); versions match `backend/pom.xml` (4.0.10, 2.0.0, 2.8.0)
- **Contracts/Migrations added:** `docs/ai_docs/{README,spring-boot-4,spring-ai-2-kiconnect,timefold-solver}.md`
- **Decisions:** none new. Findings for later changes: pom uses deprecated `spring-boot-starter-web` and lacks `spring-boot-starter-data-jpa-test` and `timefold-solver-spring-boot-starter`; `application.yml` uses deprecated `spring.ai.openai.chat.options.model` (2.0: `chat.model`)
- **Open Questions:** ki:connect chat-completions path and `useProviderStructuredOutput()` support need one live call; Timefold starter verified on Boot 4.1.1, not 4.0.x

### Task 5 — ASD-STE100 skill (vet first)
- **Commits:** `chore(skills): add vetted asd-ste100 skill`
- **Verification:** RED n/a (no test); GREEN `python3 -I .agents/skills/asd-ste100/scripts/ste-lint.py docs/feature_history.md` → exit 0, "0 violations (0 hard, baseline 0), 332 words" (run by the user: auto mode blocks downloaded scripts)
- **Contracts/Migrations added:** `.agents/skills/asd-ste100/` (danyuchn/asd-ste100-skill @ d714e53, v0.4.0, MIT) + `.claude/skills/asd-ste100` symlink
- **Decisions:** vet ACCEPTED — stdlib-only Python, no runtime network, no install/postinstall scripts, no binaries; writes only to a temp dir in `--selftest`. Copied the vetted files instead of `npx skills add`, so the repo holds exactly the read commit and the skills CLI telemetry never runs; `.claude-plugin/` omitted
- **Open Questions:** none

### Task 6 — Rules and dispatch
- **Commits:** `docs(agents): dispatch ai_docs, backlog/history lifecycle and STE rule`
- **Verification:** RED n/a (docs only); GREEN `OPENSPEC_TELEMETRY=0 openspec validate --all` → 14/14 passed; `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0
- **Contracts/Migrations added:** `spec-driven.md` rule 6; 3 AGENTS.md §3 rows; 4 `docs/setup.md` inventory rows; CHANGELOG `[Unreleased]` entry
- **Decisions:** none new (3.5, 3.6)
- **Open Questions:** none
