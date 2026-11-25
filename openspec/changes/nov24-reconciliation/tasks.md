# Tasks: `nov24-reconciliation`

## Status
- **Branch:** `docs/nov24-reconciliation`
- **Worktree:** `/Users/alek/Developer/Programming/nifty` (main checkout)
- **Current:** Tasks 1–6 done
- **Next action:** Task 7 (backlog)
- **Uncommitted changes:** yes (change folder)
- **PR / MR:** none yet
- **Open review findings:** none

## Tasks

### Task 1: Delta specs
- [x] Write delta specs for the 7 domain specs and the new `auth-access` capability (design §1 map)
- [x] Verify: `OPENSPEC_TELEMETRY=0 openspec validate nov24-reconciliation --strict` → valid
- [x] Commit `docs(specs): add nov24-reconciliation change with delta specs`

### Task 2: Discovery canvas (Decision 4.1)
- [x] Copy the 2026-11-22 canvas to `docs/archive/discovery-canvas-2026-11-22.md` with the OUTDATED banner
- [x] Update `docs/discovery-canvas.md` to the feature list and decisions 1.11–4.4; keep heading anchors
- [x] Verify: `ste-lint.py` on changed prose; no stale hits for `.kiro/specs`, `TBD`, `/100`, pause/resume, re-probe
- [x] Commit `docs(canvas): archive the discovery canvas and update it to the Nov 24 decisions`

### Task 3: Invariants and rules
- [x] `AGENTS.md` §1–§3, `openspec/config.yaml`, `README.md`, `.agents/rules/spec-driven.md`: two roles, threshold-gated release, dual-agent isolation, real data rule, PrimeVue, `specs/` instead of `requirements.md`
- [x] `CLAUDE.md` + `AGENTS.md`: context read order (Decision 3.13)
- [x] `.agents/rules/backend-java.md`: roles, ledger instead of a mutable JSONB transcript, Java/Spring conventions (Decision 3.13)
- [x] `.agents/rules/frontend-vue.md`: O1 resolved, duplicate rule 11 removed, per-question flags, countdown
- [x] `docs/workflow.md`: `specs/` instead of `requirements.md`
- [x] `openspec/specs/*/spec.md`: `Primary View` and `Purpose` lines (design §7)
- [x] Verify: `ste-lint.py` on the changed files; `openspec validate --all --strict`; harness tests
- [x] Commit `docs(rules): align invariants and rules with the Nov 24 decisions`

### Task 4: Architecture and API
- [x] `docs/architecture.md`: two views, dual-agent pipeline, ledger contract, domain model + ER diagram, enum table, slice map (design §2–§6)
- [x] `docs/api.md`: role table, planned endpoints (auth, invites, sessions, answers, BFI-10, logs), Docker OpenAPI row
- [x] Verify: `ste-lint.py`; harness tests
- [x] Commit `docs(architecture): add the dual-agent model, ledger and domain model`

### Task 5: Design system (Decision 1.14)
- [x] `docs/design_system.md`: 0–4 + hatched `N/A`, PrimeVue on Tailwind v4 tokens, interview framing, time format, advice tone, no Ask AI search in MVP
- [x] Verify: `ste-lint.py`
- [x] Commit `docs(design): align the design system with the 0-4 scale and PrimeVue`

### Task 6: AI logs (Decision 3.12)
- [x] `docs/ai_logs/README.md` + `docs/ai_logs/2026-11-25-architecture-handover.md`
- [x] `AGENTS.md` dispatch row, `docs/setup.md` inventory row
- [x] Verify: `ste-lint.py`; harness tests
- [x] Commit `docs(ai-logs): start the prompt-as-code audit trail`

### Task 7: Backlog (Decision 3.14)
- [ ] Rebuild `docs/feature_backlog.md`: foundation, auth, audit, domain slices with waves, reserved migrations and US-NN stories; Post-MVP section; harness follow-ups
- [ ] `CHANGELOG.md` entry
- [ ] Verify: `ste-lint.py`; harness tests
- [ ] Commit `docs(backlog): rebuild the backlog as atomic stories in delivery waves`

## Delivery
- [ ] `git fetch origin && git merge origin/dev`
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive nov24-reconciliation -y` (syncs the delta specs), fix the `auth-access` Purpose if needed, history entry in `docs/feature_history.md`
- [ ] Verify: `openspec validate --all --strict`; `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`
- [ ] `node scripts/forge.mjs push`; PR/MR with `pr-memo`
- [ ] Cold review (fresh `reviewer` agent)
- [ ] CI green on both forges (`node scripts/forge.mjs prs`)
- [ ] Ask the user with the architecture summary, then merge on GitHub and `node scripts/forge.mjs sync dev`
- [ ] Branch cleanup

## Handoff Log

### 2026-11-25 — Decisions and Task 1 (main session)
- **Did:** question rounds with the user; recorded Decisions 1.11–1.17, 2.10–2.19, 3.12–3.14, 4.1–4.4 (O1–O7 resolved); committed the feature list as `docs/feature-list-2026-11-24.md`; wrote the delta specs, proposal and design.
- **Verification:** RED: n/a (no behaviour). `OPENSPEC_TELEMETRY=0 openspec validate nov24-reconciliation --strict` → valid.
- **Decisions:** all of the above; the user chose POST answers that stream the reply (1.11) and invite-link sign-in (1.17).
- **Open questions:** a timed-out partial answer is logged but not scored (Decision 2.14 + feature list); confirm with the user.

### 2026-11-25 — Tasks 2–3 (main session + canvas subagent)
- **Did:** archived the canvas as OUTDATED in `docs/archive/` and updated the live canvas (subagent); aligned `AGENTS.md` (invariants 4–6, 8–10, read order), `CLAUDE.md`, `README.md`, `openspec/config.yaml`, `spec-driven.md`, `backend-java.md` (roles, ledger, Java conventions as new rule 7, so Architecture is now §9), `frontend-vue.md` (PrimeVue, countdown, duplicate rule removed), `workflow.md`, reviewer and check skills, spec header lines.
- **Verification:** RED: n/a (no behaviour). ste-lint: no new hard findings in written text (EARS lines exempt); `openspec validate --all --strict` 8/8; harness tests 43/43.
- **Open questions:** canvas subagent notes: interview budget has no decision; rubber-stamp metrics kept in UX-04.

### 2026-11-25 — Task 4 (main session)
- **Did:** `docs/architecture.md` §2–§5 rewritten (two roles, dual-agent pipeline, interview request flow, ledger and transcript contract, enum table with `auto_released`, domain model ER diagram) and the slice map with `auth` and `audit`; `docs/api.md` role table, Docker OpenAPI row, planned endpoints.
- **Verification:** RED: n/a (no behaviour). ste-lint: 0 hard findings in both files.

### 2026-11-25 — Tasks 5–6 (main session)
- **Did:** `docs/design_system.md` (0–4 + hatched `N/A` level table, PrimeVue preset from Tailwind `@theme` tokens, interview framing, `mm:ss` timers, advice tone, Countdown/ConversationLog/LikertScale, Ask AI Post-MVP); `docs/ai_logs/` README and the 2026-11-25 handover; `AGENTS.md` dispatch row; `docs/setup.md` inventory row.
- **Verification:** RED: n/a (no behaviour). ste-lint: 0 hard findings in the new text.
