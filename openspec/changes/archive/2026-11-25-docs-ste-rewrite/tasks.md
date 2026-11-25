# Tasks: Rewrite Existing Docs in ASD-STE100

## Status
<!-- Overwrite this block at every stop. Any tool resumes from here (see docs/prompts/harness-upgrade-board.md). -->
- **Branch:** `docs/docs-ste-rewrite` (off `origin/dev` f915727; the five harness changes are archived there)
- **Worktree:** `/Users/alek/Developer/Programming/nifty-docs-ste-rewrite`
- **Current:** Tasks 1-7 done; Delivery next
- **Next action:** CI green on both forges, then ask the user to merge PR #14
- **Uncommitted changes:** no
- **PR / MR:** https://github.com/BBoushe/nifty/pull/14 · https://git.inf.fh-dortmund.de/01/alpac001/nifty/-/merge_requests/13
- **Open review findings:** cold review APPROVE (0 Blocking); 5 Convention Candidates fixed in the review-fix commit, intensifier drops accepted

## Tasks

Each task has the same steps: (a) run the obligation check (design §2) on each file and save the output; (b) rewrite the prose; (c) run the obligation check again and compare (expect identical output); (d) run the STE linter (expect no errors, or log the exceptions); (e) run `OPENSPEC_TELEMETRY=0 openspec validate --all` and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` (expect exit 0); (f) commit `docs(<scope>): rewrite <group> in ASD-STE100`. Doc-only tasks write `RED: n/a (no behaviour)` in the Handoff.

- [x] **1. Agent entry points** — `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.kiro/steering/*.md`
  - [x] Steps (a)–(f)
- [x] **2. Rules** — `.agents/rules/*.md`
  - [x] Steps (a)–(f)
- [x] **3. Onboarding** — `README.md`, `docs/setup.md`, `docs/workflow.md`
  - [x] Steps (a)–(f)
- [x] **4. Architecture docs** — `docs/architecture.md`, `docs/api.md`, `docs/design_system.md`
  - [x] Steps (a)–(f)
- [x] **5. Product vision** — `docs/discovery-canvas.md`
  - [x] Steps (a)–(f)
- [x] **6. Domain specs** — `openspec/specs/*/spec.md`
  - [x] Steps (a)–(f)
- [x] **7. Changelog**
  - [x] Add a `CHANGELOG.md` `[Unreleased]` entry. Commit `docs(changelog): note STE rewrite`.

## Delivery

- [x] `git fetch origin && git merge origin/dev` (no rebase: a rebase needs a force push, which the guard blocks).
- [x] Re-run `OPENSPEC_TELEMETRY=0 openspec validate --all` and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`. Expect: exit 0.
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive docs-ste-rewrite -y --skip-specs`. Add the `docs/feature_history.md` entry and update `docs/feature_backlog.md`. Commit `chore(specs): archive docs-ste-rewrite`.
- [x] `node scripts/forge.mjs push`
- [x] PR/MR body via `pr-memo`, then `node scripts/forge.mjs pr --title "docs: rewrite existing docs in ASD-STE100" --body-file <file>`. Record the links in Status.
- [x] Cold review session with the word diff (design §3): verdict has no Blocking items.
- [ ] GitLab MR pipeline green; `gh pr checks <n>` green.
- [ ] Merge on GitHub only: `gh pr merge <n> --merge`.
- [ ] `node scripts/forge.mjs sync dev`, then `node scripts/forge.mjs status` prints 0/0 for every branch.
- [ ] After `git merge-base --is-ancestor docs/docs-ste-rewrite origin/dev`: delete the branch on both remotes and locally; `git worktree remove ../nifty-docs-ste-rewrite`.

## Handoff Log

### 2026-11-25 — Task 1 (Sonnet subagent, checked by main session)
- **Did:** STE rewrite of `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.kiro/steering/nifty-core.md`. Headings, tables, code, links unchanged.
- **Verification:** RED: n/a (no behaviour). Obligations equal on all 4 files; ste-lint hard findings 13→0, 8→0, 1→0, 0→0; `openspec validate --all` exit 0; harness tests exit 0. Main session read the word diff: no meaning change.
- **Note:** word counts rose slightly (split semicolons and long sentences).
- **Next:** Task 2.

### 2026-11-25 — Task 2 (Sonnet subagent, checked by main session)
- **Did:** STE rewrite of `.agents/rules/*.md`; frontmatter, headings, Reviewer levels and frontend-vue rules 10/11 unchanged.
- **Verification:** RED: n/a (no behaviour). Obligations equal on all 5 files; ste-lint hard findings 14→0, 7→1, 5→0, 0→0, 2→1. Exceptions: coding-standards.md:3 (YAML description, kept byte-for-byte), spec-driven.md:16 (heading "Verify Before Checking Off" kept). `openspec validate --all` exit 0; harness tests exit 0. Main session read the invariant lines: no meaning change.
- **Next:** Task 4.

### 2026-11-25 — Task 4 (Sonnet subagent, checked by main session)
- **Did:** STE rewrite of `docs/architecture.md`, `docs/api.md`, `docs/design_system.md`; headings, anchors, tables, diagrams unchanged. Main session restored the past tense in the design-system source note and reworded the api.md maintenance rule.
- **Verification:** RED: n/a (no behaviour). Obligations equal on all 3 files; ste-lint hard findings 19→0, 4→0, 3→0; `openspec validate --all` exit 0; harness tests 35/35.
- **Next:** Task 5.

### 2026-11-25 — Task 5 (Sonnet subagent, checked by main session)
- **Did:** STE rewrite of `docs/discovery-canvas.md`; quotes, code, diagrams, tables, headings unchanged. Main session restored the 5 EARS requirements in §14.2 word for word (the subagent had split or shortened them), the concession in §1.1, and made the P1 line pronoun-free.
- **Verification:** RED: n/a (no behaviour). Obligations equal; ste-lint hard findings 36→7. Accepted exceptions: 6 in the verbatim §14.2 EARS lines (EARS text is not rewritten), 1 synonym in the Spring AI feature name "self-correcting". `openspec validate --all` exit 0; harness tests 35/35.
- **Rule for Task 6:** EARS requirement sentences stay verbatim; only surrounding prose and scenario text may change.
- **Next:** Task 6.

### 2026-11-25 — Task 6 (Sonnet subagent, checked by main session)
- **Did:** STE rewrite of the Purpose paragraph in all 7 `openspec/specs/*/spec.md` (the only free prose). EARS statements, scenarios, headings and REQ IDs unchanged (`git diff -U0 | grep SHALL|WHEN|…` prints nothing).
- **Verification:** RED: n/a (no behaviour). Obligations equal on all 7; prose lint findings 2→0; EARS findings accepted (28, verbatim by rule); `openspec validate --all --strict` 8/8; harness tests 35/35.
- **Next:** Task 3 after PR #13 merges.

### 2026-11-25 — Task 3 (Sonnet subagent, checked by main session)
- **Did:** STE rewrite of `README.md`, `docs/setup.md`, `docs/workflow.md`; headings, anchors, tables, code and the guard form/limit lists unchanged (keyword counts compared before/after).
- **Verification:** RED: n/a (no behaviour). Obligations equal on all 3; ste-lint hard findings 11→0, 23→1, 12→1 (exceptions: synonym rotation forced by the kept labels "Verify End-to-End" and "Implement & Verify"); `openspec validate --all` exit 0; harness tests 40/40.

### 2026-11-25 — Task 7 (main session)
- **Did:** CHANGELOG `[Unreleased]` entry for the STE rewrite.
- **Verification:** RED: n/a (no behaviour). Harness tests exit 0.
- **Next:** Delivery.

### 2026-11-25 — Delivery and review fixes (main session)
- **Did:** pushed both forges by hand from the main checkout (the worktree has no `.env`), opened PR #14 / MR !13. Cold review APPROVE. Fixed 5 Convention Candidates: README now states Decision 3.10 (an old contradiction, not caused by this change), the candidate persona keeps "felt" for both claims, the Mode C subject is explicit, backend-java "Then" → "This way", architecture row 6 names "a human" instead of "the reviewer". The Task 7 CHANGELOG entry went into commit 2f3353e, not a separate commit.
- **Verification:** RED: n/a (no behaviour). Obligations equal on the changed files except README, which gains the intended `3.10`; `openspec validate --all --strict` exit 0; harness tests exit 0.
- **Next:** CI, then merge (ask the user).
