# Ticket #3: `assessment` — Interview Service, Ledger & BFI-10 (Teammate S)

- **OpenSpec Change:** [`openspec/changes/assessment-chat/`](../../openspec/changes/assessment-chat/)
- **Branch / Worktree:** `feat/assessment-chat` (`../nifty-assessment-chat`)
- **Owned Folders:** `backend/src/main/java/de/fhdortmund/nifty/assessment/`, `frontend/src/views/assessment/` (chat + BFI-10; leave `ResultsView.vue` for `evaluation`)
- **Reserved Migration:** `V6__assessment.sql`
- **Wave:** Wave 4 (start pure-JVM + mocked-upstream stories immediately in parallel!)

## Key Convergence Fixes Applied to Original Draft
1. **No duplicate prompt builder (Decision 1.18):** Drop internal `InterviewerPromptBuilder` and inject `ContextCreationService` (`buildInterviewerContext` + `inspectCandidateInput(attemptId, text)`) from `context`.
2. **Timed-out partial answers evaluated (Decision 2.20):** Answers arriving after `expiresAt` within the grace window are stored with `timed_out = true` and **evaluated normally downstream** if non-blank (only blank/expired-past-grace entries become `N/A`).
3. **Anomaly flags (Decision 2.24):** Add `injection_suspected BOOLEAN NOT NULL DEFAULT FALSE` column to `conversation_entry` (from `ContextCreationService.inspectCandidateInput(attemptId, text)`; `context` writes the audit row, so `assessment` does not) and pair `min_time_sec` with a deterministic character-velocity check (`> 12 chars/sec` for answers `>= 150 chars`) on `paste_suspected`.
4. **Transcript export route fix (Decision 1.19):** Do NOT expose `GET /api/v1/assessment/sessions/{id}/transcript` under `CANDIDATE`-only `/api/v1/assessment/**`. Expose `AssessmentQueryService.getTranscriptForAttempt(attemptId)` internally so `evaluation` serves `GET /api/v1/ranking/transcripts/{attemptId}` (`PROFESSOR` only) and `GET /api/v1/assessment/results/{configId}` (post-release `CANDIDATE`).
5. **Single-source BFI-10 & event:** Keep single-source Java BFI-10 scoring (`1.0..5.0` means, never passed to `context` or the evaluator judge, REQ-AC-16; `evaluation` reads them only to show the candidate's own results) and publish `AttemptCompletedEvent(attemptId, configId, inviteId)` on `COMPLETED`.
6. **Frontend start call:** `POST /api/v1/assessment/sessions` answers JSON for a `SOFT_SKILLS`-only start and SSE otherwise. `postSse` in `frontend/src/api/client.ts` accepts only `text/event-stream`, so check the response type first or extend the client in this ticket.

## Exposes / Consumes
- **Exposes:** `AssessmentQueryService` (`getTranscriptForAttempt`, `getLatestCompletedAttemptId`, `getSoftSkillProfilesForConfig`, `getSoftSkillProfileForAttempt`), `AttemptCompletedEvent`, `POST/GET /api/v1/assessment/sessions/**`.
- **Consumes:** `AuthContextService`, `StudioQueryService.interviewerView`, `ContextCreationService`, `AuditService`, `AssessmentDeletedEvent`.

## Ready-to-Paste Agent Launch Prompt

```text
You are implementing the `assessment` vertical slice (`assessment-chat`) for Nifty.
Setup (do this before anything else):
1. Prerequisite check: run `git fetch origin && git show origin/dev:backend/src/main/java/de/fhdortmund/nifty/audit/service/AuditService.java > /dev/null && git show origin/dev:frontend/package.json | grep -q '"test":'`. If it fails, Wave 0 (`backend-foundation` with the US-BF-06 contract stubs, and `frontend-foundation`) is not on `dev` yet. Stop and tell the human.
2. From the main checkout, run `git worktree add ../nifty-assessment-chat -b feat/assessment-chat origin/dev`. Start every Bash command with `cd /absolute/path/to/nifty-assessment-chat && pwd`, and pass only absolute worktree paths to Edit/Write. `forge.mjs` reads `.env` from the main checkout, so do not copy it. Never read `.env` yourself.
3. The shared interfaces, records and enums of `docs/tickets/00-parallel-waves-and-contracts.md` §3 already exist as stubs on `dev`. Implement only the ones your slice owns. Mock the others. Never create a second copy of a shared type.

Read these files first (do NOT scan the whole source tree):
1. `AGENTS.md`, `.agents/rules/coding-standards.md`, `.agents/rules/backend-java.md`, `.agents/rules/frontend-vue.md`
2. `docs/architecture.md`, `docs/api.md`, `docs/tickets/00-parallel-waves-and-contracts.md`
3. `openspec/specs/assessment-chat/spec.md`
4. `openspec/changes/assessment-chat/{proposal.md,design.md,tasks.md,specs/assessment-chat/spec.md}`

Non-negotiable rules:
- Use the `spec-implementer` skill for the tasks and the `pr-memo` skill for the PR/MR body. Commit after each task, but push only after the human says yes. Never merge. Ask the human before you push or open the PR/MR. Update `tasks.md` `## Status` before every stop, so another session can resume.
- Follow TDD strictly (RED failing test -> GREEN minimal code -> REFACTOR) for US-AC-01 through US-AC-06 in `tasks.md`.
- All `@Entity` and `JpaRepository` classes live privately in `de.fhdortmund.nifty.assessment.repository` (`UUID` IDs everywhere).
- Delegate all interviewer prompt creation and injection scanning to `ContextCreationService` (never build prompts inside `assessment`). Mock `StudioQueryService`, `ContextCreationService`, `AuthContextService`, and `ChatClient` in unit tests so you can work in parallel immediately.
- Enforce Decision 2.26: reject an answer `content` over 4000 characters with `400` ProblemDetail before any ledger write or LLM call (REQ-AC-20); the chat box sets `maxlength=4000`.
- Enforce Decision 2.20 (non-blank timed-out answers kept for evaluation; blank/post-grace closed with empty text), Decision 2.23 (`SOFT_SKILLS`-only skips to `BFI10`), Decision 2.24 (`injection_suspected` + character-velocity `paste_suspected`), and Decision 4.3 (single-source zero-Stream BFI-10 scoring, never passed to `context` or the evaluator judge, REQ-AC-16).
- Implement PostgreSQL immutability trigger on `conversation_entry` with `SET LOCAL nifty.allow_purge = 'on'` cleanup on `AssessmentDeletedEvent`, publish `AttemptCompletedEvent(attemptId, configId, inviteId)` on commit, and expose exact `AssessmentQueryService` signatures from `docs/tickets/00-parallel-waves-and-contracts.md`.
- Update `docs/api.md`, leave `// Decision X.Y` pointers, run `./mvnw -B test` and `npm run typecheck && npm run lint && npm run test && npm run build`, archive before pushing, and open PR/MR via `node scripts/forge.mjs`.
```
