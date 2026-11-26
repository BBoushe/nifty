# Ticket #6: `mvp-hardening` — Review Modals, Bug Fixes and Review Follow-Ups

- **OpenSpec Change:** [`openspec/changes/mvp-hardening/`](../../openspec/changes/mvp-hardening/)
- **Branch / Worktree:** `fix/mvp-hardening` (`../nifty-mvp-hardening`). Both already exist and hold the authored change.
- **Owner:** sole contributor. The change touches every slice, but the tasks run one at a time on one branch.
- **Migration:** none planned. If a task needs one, use the next free version (`V11`) and never edit `V1`–`V10`.
- **Decisions:** 2.31 (soft-only auto-release), 2.32 (remove the invented-questions toggle; LLM follow-ups stay), 2.33 (expire and evaluate without BFI-10), 2.34 (re-invite after revoke rotates the token).

## What and Why

The MVP is on `dev`. The first manual runs, an audit of `dev` and the cold reviews of PRs #26–#34 left about 35 open items. The professor review is hard to use: the Evidence Drawer opens below the student list, and the conversation shows as raw records. The full list, grouped by blast radius, is in `proposal.md`. The fix for each item is in `design.md`.

| Group | Tasks | Area |
|---|---|---|
| A | 1–2 | Transcript dialog (chat layout) and evidence review as modals: **user request, do first** |
| B | 3–6 | Frontend fixes: live status, Release state, names, route sync, retake, 404, shell errors |
| C | 7–10 | Evaluation: judge JSON, final-try crash, re-run endpoint, lost events, snapshot weights, lock order, soft-only release |
| D | 11–13 | Assessment and context: toggle removal, `followUpLimit = 0` prompt, prompt-failure fallback, bounded streams, expire and evaluate |
| E | 14–16 | Auth, invites, Studio: re-invite, `409` on races, CSV, CSRF renewal, rate limit, bcrypt, list query |
| F | 17–18 | Teams precision and tests, docs |
| G | 19 | Full verification |

**Out of scope** (see `proposal.md`): the unused prompt-preview endpoint, more Team Builder options, more formula presets, `promptedTerms`, the per-request revocation check, and separate LLM configs.

## Ready-to-Paste Launch Prompt

```text
Run docs/tickets/06-mvp-hardening.md (openspec/changes/mvp-hardening) with the orchestrate-changes skill.

Setup:
- The branch fix/mvp-hardening and the worktree /Users/alek/Developer/Programming/nifty-mvp-hardening already exist and hold the authored change (commit "docs(specs): propose mvp-hardening change and ticket 06"). Do not create another branch or worktree. Check with `git worktree list`. Then run `git -C /Users/alek/Developer/Programming/nifty-mvp-hardening fetch origin && git -C /Users/alek/Developer/Programming/nifty-mvp-hardening merge origin/dev` (merge, never rebase).
- Resume from openspec/changes/mvp-hardening/tasks.md `## Status` and `## Handoff Log`. If the change is already archived, use openspec/changes/archive/*-mvp-hardening/tasks.md.

Read first (do not scan the whole tree): AGENTS.md, .agents/rules/coding-standards.md, .agents/rules/backend-java.md, .agents/rules/frontend-vue.md, docs/architecture.md, docs/api.md, then openspec/changes/mvp-hardening/{proposal.md,design.md,tasks.md,specs/**}. Decisions 2.31–2.34 are already recorded. Cite them, never re-record them.

How to run it:
- One spec-builder agent (Sonnet) per task group, in this order: A → B → C → D → E → F → G. Run the groups one after another in the one worktree. Never run two builders in the same worktree at the same time. Every agent Bash command starts with `cd /Users/alek/Developer/Programming/nifty-mvp-hardening && pwd`, and agents pass only absolute worktree paths to Edit/Write.
- Group A is my direct request. Spend design care on it: the transcript must read like a chat (design.md §1), not like a log. When Group A is green, stop and ask me to look at it in the browser (docker compose up --build -d, candidate in a private window) before you start Group B.
- Each code task: RED test first, then GREEN, then the Handoff entry with `Verification: RED: <cmd> → FAIL (...); GREEN: <cmd> → exit 0 (n run)`, then tick the boxes, overwrite `## Status`, and commit with explicit file names. Never use `git add -A`, `git commit -a`, or `git add && git commit` in one command.
- After each group: read the diff yourself, then run the group's checks. Backend: `cd backend && ./mvnw -B test`. Frontend: `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`.
- A controller or DTO change updates docs/api.md in the same commit. Keep the deterministic-core invariants: no LLM math, N/A never becomes 0, the interviewer never sees rubrics or scores.
- If a design.md instruction turns out to be wrong against the real code (a method name, a field that already exists), choose the smallest fix that meets the EARS requirement and note it under Decisions in the Handoff entry. Ask me only if a requirement itself must change.

Delivery:
- When Task 19 is green: merge origin/dev again, re-run the full checks, archive (`OPENSPEC_TELEMETRY=0 openspec archive mvp-hardening -y`), set the backlog row to done, and add the docs/feature_history.md entry.
- Ask me before you push. Then `node scripts/forge.mjs push` and `node scripts/forge.mjs pr` with a pr-memo body.
- Start a cold reviewer agent (Opus) on origin/dev...HEAD plus the change specs. Fix the Blocking items and review again. When the rounds only find narrower edge cases, tell me instead of looping.
- Ask me before the merge, with the architecture summary (orchestrate-changes §7). Merge on GitHub only, then `node scripts/forge.mjs sync dev` and `node scripts/forge.mjs status` (expect 0/0), then clean up the branch on both remotes and the worktree.
- Never read or print .env.
```
