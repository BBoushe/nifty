# Tasks: `nov24-followups`

## Status
- **Branch:** `chore/nov24-followups`
- **Worktree:** `/Users/alek/Developer/Programming/nifty` (main checkout)
- **Current:** Tasks 1–3 done
- **Next action:** the user adds their side work to this branch; then push, PR/MR, cold review, ask before merge
- **Uncommitted changes:** no
- **PR / MR:** none yet (the user asked to hold the PR)
- **Open review findings:** none

## Tasks

### Task 1: Two roles in code (Decision 1.15, REQ-AA-01)
- [x] RED: `RoleAccessTest` (two roles; `403` across views, `404` inside a view) with `spring-boot-starter-security-test`
- [x] GREEN: `Role` = `PROFESSOR`, `CANDIDATE`; `SecurityConfig` uses `hasRole` per view prefix
- [x] Verify: `cd backend && ./mvnw -B test` → exit 0

### Task 2: Timed-out answers are evaluated (Decision 2.20)
- [x] Delta specs REQ-AC-08, REQ-EP-06 (renamed), REQ-FE-03
- [x] Docs: architecture, canvas, backlog, AI log; no cost limit per interview (Decision 4.5)
- [x] Verify: `openspec validate --all --strict`

### Task 3: Close the previous change and the dependency check
- [x] Tick the Delivery boxes of `archive/2026-11-25-nov24-reconciliation/tasks.md`
- [x] Check the US-BF dependency names in Maven Central (design §3); remove the API drift note; CHANGELOG
- [x] Verify: harness tests

## Delivery
- [x] Archive: `OPENSPEC_TELEMETRY=0 openspec archive nov24-followups -y`, history entry
- [ ] `git fetch origin && git merge origin/dev` (after the user's side work)
- [ ] `node scripts/forge.mjs push`; PR/MR with `pr-memo`
- [ ] Cold review (fresh `reviewer` agent)
- [ ] CI green on both forges (`node scripts/forge.mjs prs`)
- [ ] Ask the user with the architecture summary, then merge on GitHub and `node scripts/forge.mjs sync dev`
- [ ] Branch cleanup

## Handoff Log

### 2026-11-25 — Tasks 1–3 (main session)
- **Did:** removed `REVIEWER` and split the route rules per view; recorded Decisions 2.20 (timed-out answers with text are evaluated) and 4.5 (no cost limit per interview); updated the specs and docs; closed the `nov24-reconciliation` Delivery boxes; checked the wave-0 dependency names (all exist at the pinned versions).
- **Verification:** RED: `./mvnw -B -q test -Dtest=RoleAccessTest` → FAIL (`Role` has `REVIEWER`; `PROFESSOR` on `/api/v1/assessment/**` got 404, expected 403); GREEN: `cd backend && ./mvnw -B test` → exit 0 (24 tests). `openspec validate --all --strict` → valid.
- **Contracts:** `/api/v1/assessment/**` no longer allows `PROFESSOR` (REQ-AA-01).
- **Decisions:** 2.20, 4.5; applies 1.15.
