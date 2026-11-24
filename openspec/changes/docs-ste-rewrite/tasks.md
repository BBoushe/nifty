# Tasks: Rewrite Existing Docs in ASD-STE100

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `docs/docs-ste-rewrite` (off `origin/dev` f915727; the five harness changes are archived there)
- **Worktree:** `/Users/alek/Developer/Programming/nifty-docs-ste-rewrite`
- **Current:** Task 1 next. Method: one Sonnet subagent per task, one after another; the main session checks each diff and commits it
- **Next action:** Task 1. Task 3 (`README.md`, `docs/setup.md`) waits until `bash-guard-gaps` merges, because that change edits both files
- **Uncommitted changes:** no
- **PR / MR:** —
- **Open review findings:** —

## Tasks

Each task has the same steps: (a) run the obligation check (design §2) on each file and save the output; (b) rewrite the prose; (c) run the obligation check again and compare (expect identical output); (d) run the STE linter (expect no errors, or log the exceptions); (e) run `OPENSPEC_TELEMETRY=0 openspec validate --all` and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` (expect exit 0); (f) commit `docs(<scope>): rewrite <group> in ASD-STE100`. Doc-only tasks write `RED: n/a (no behaviour)` in the Handoff.

- [ ] **1. Agent entry points** — `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.kiro/steering/*.md`
  - [ ] Steps (a)–(f)
- [ ] **2. Rules** — `.agents/rules/*.md`
  - [ ] Steps (a)–(f)
- [ ] **3. Onboarding** — `README.md`, `docs/setup.md`, `docs/workflow.md`
  - [ ] Steps (a)–(f)
- [ ] **4. Architecture docs** — `docs/architecture.md`, `docs/api.md`, `docs/design_system.md`
  - [ ] Steps (a)–(f)
- [ ] **5. Product vision** — `docs/discovery-canvas.md`
  - [ ] Steps (a)–(f)
- [ ] **6. Domain specs** — `openspec/specs/*/spec.md`
  - [ ] Steps (a)–(f)
- [ ] **7. Changelog**
  - [ ] Add a `CHANGELOG.md` `[Unreleased]` entry. Commit `docs(changelog): note STE rewrite`.

## Delivery

- [ ] `git fetch origin && git merge origin/dev` (no rebase: a rebase needs a force push, which the guard blocks).
- [ ] Re-run `OPENSPEC_TELEMETRY=0 openspec validate --all` and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive docs-ste-rewrite -y --skip-specs`. Add the `docs/feature_history.md` entry and update `docs/feature_backlog.md`. Commit `chore(specs): archive docs-ste-rewrite`.
- [ ] `node scripts/forge.mjs push`
- [ ] PR/MR body via `pr-memo`, then `node scripts/forge.mjs pr --title "docs: rewrite existing docs in ASD-STE100" --body-file <file>`. Record the links in Status.
- [ ] Cold review session with the word diff (design §3): verdict has no Blocking items.
- [ ] GitLab MR pipeline green; `gh pr checks <n>` green.
- [ ] Merge on GitHub only: `gh pr merge <n> --merge`.
- [ ] `node scripts/forge.mjs sync dev`, then `node scripts/forge.mjs status` prints 0/0 for every branch.
- [ ] After `git merge-base --is-ancestor docs/docs-ste-rewrite origin/dev`: delete the branch on both remotes and locally; `git worktree remove ../nifty-docs-ste-rewrite`.

## Handoff Log
