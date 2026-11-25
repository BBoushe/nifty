# Tasks: `five-service-convergence`

## Status
- **Branch:** `chore/five-service-convergence`
- **Worktree:** `/Users/alek/Developer/Programming/nifty` (main checkout)
- **Current:** archived; second cold review fixed; committed
- **Next action:** push and open the PR/MR, CI green, ask the user before merge
- **Uncommitted changes:** no
- **PR / MR:** GitHub #17, GitLab !16
- **Open review findings:** none

## Tasks

### Task 1: Decisions and delta specs
- [x] Record 1.18–1.21 and 2.21–2.25 with `node scripts/record-decision.mjs`, with Supersedes lines
- [x] Delta specs for 8 capabilities (this folder). Old IDs keep their meaning.
- [x] Verify: `OPENSPEC_TELEMETRY=0 openspec validate --all --strict`

### Task 2: Docs, rules and build
- [x] Architecture, API, backlog, workflow ownership table, AI log, AGENTS.md, `openspec/config.yaml`, `backend-java.md`, discovery canvas
- [x] Remove Timefold from `backend/pom.xml`, the Ranking View label and `docs/ai_docs/`
- [x] Verify: `cd backend && ./mvnw -B test`

### Task 3: Teammate changes and tickets
- [x] Five implementation changes and `docs/tickets/00..05`. Teammates named by initial.
- [x] Verify: harness tests and `node .claude/hooks/archive-gate.mjs --pre-push`

## Delivery
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive five-service-convergence -y`, history entry
- [x] `git fetch origin && git merge origin/dev` (already up to date)
- [x] `node scripts/forge.mjs push`; PR/MR with `pr-memo`
- [x] Cold review (fresh `reviewer` agent): two rounds, all blocking items fixed
- [ ] CI green on both forges (`node scripts/forge.mjs prs`)
- [ ] Ask the user with the architecture summary, then merge on GitHub and `node scripts/forge.mjs sync dev`
- [ ] Branch cleanup

## Handoff Log

### 2026-11-25 — Tasks 1–3 (main session, after cold review)
- **Did:** rebuilt the spec edits of the convergence session as delta specs on the merged base; restored dropped rules and scenarios; gave new content new IDs; added Decisions 1.21 and 2.25; removed Timefold; fixed the review findings in docs, tickets and teammate changes.
- **Verification:** RED: n/a (docs and specs; the Timefold removal has no behaviour). `openspec validate --all --strict`, harness tests and `./mvnw -B test` → exit 0.
- **Contracts:** `EvaluationQueryService` returns released profiles only; typed enums in the contract pack.
- **Decisions:** 1.18–1.21, 2.21–2.25.

### 2026-11-25 — Second cold review and ticket gate
- **Did:** fixed the second review: restored the formula preset endpoints (US-EV-04b), the cap rule for 3+ entries, the full interviewer context list, the `SOFT_SKILLS` JSON start, and 9 convention items. Added a Wave 0 gate to the tickets: the US-BF-06 contract stubs, a worktree setup step, and "never merge" in every launch prompt.
- **Verification:** RED: n/a (docs and specs). `openspec validate --all --strict` 14/14, harness tests 43/43, archive gate 0, frontend typecheck and lint 0, `./mvnw -B test` 0.
- **Contracts:** formula endpoints under `/api/v1/ranking/formula-presets` and `/api/v1/ranking/configs/{configId}/formula`.
- **Decisions:** none new.
