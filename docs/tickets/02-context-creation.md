# Ticket #2: `context` — Context Creation Service (Teammate A)

- **OpenSpec Change:** [`openspec/changes/context-creation/`](../../openspec/changes/context-creation/)
- **Branch / Worktree:** `feat/context-creation` (`../nifty-context-creation`)
- **Owned Folders:** `backend/src/main/java/de/fhdortmund/nifty/context/` (`web/`, `service/` only — stateless)
- **Reserved Migration:** None (stateless slice; zero Flyway contention)
- **Wave:** Wave 1 (start immediately in parallel; mock `AuditService` until `audit-log-service` merges)

## Key Convergence Alignment
1. **Sole prompt builder (Decision 1.18):** Both `assessment` (#3) and `evaluation` (#4) delegate prompt construction to `ContextCreationService`.
2. **Agent 2 aligned to per-pair 2-run judge (Decision 1.19):** `buildJudgeContext(JudgeContextInput)` takes `List<String> rubricLevels` (size 5, index `0..4` ascending) from Teammate R's `EvaluatorView` and builds the fresh-context prompt for Teammate M's `JudgeEvaluationOutput(level, verbatimQuote, rationale, suggestion)` JSON object (REQ-EP-08, Decision 2.25).
3. **Two separate contexts per attempt (Decision 1.21):** build the interviewer context only from `InterviewerView` and the current attempt's ledger, and the judge context only from `EvaluatorView` and the completed attempt's ledger (REQ-CC-01).
4. **Partial timed-out answers evaluated (Decision 2.20):** Reject blank/null `answerText`; include non-blank `timedOut = true` answers wrapped in `<transcript_entry id="..." timedOut="true">`.
5. **Defense-in-depth (Decision 2.24):** Zero-Stream `PromptSanitizer` + `InjectionDetector` return `SanitizationReport(injectionSuspected, matchedRuleIds, truncated)` and log security events via `AuditService` with `promptSha256` and zero raw PII.
6. **Extensible architecture:** Implement `PromptContextBuilder<I>` + `AgentRole` so Post-MVP builders (UX-10 advice, Team Mode B) plug in without modifying Agent 1 or Agent 2.

## Exposes / Consumes
- **Exposes:** `ContextCreationService` (`buildInterviewerContext`, `buildJudgeContext`, `inspectCandidateInput(attemptId, text)`), `POST /api/v1/ranking/context/preview/interviewer` (`PROFESSOR` only).
- **Consumes:** `AuditService.record(...)` (`audit`). Zero other slice dependencies!

## Ready-to-Paste Agent Launch Prompt

```text
You are implementing the `context` vertical slice (`context-creation`) for Nifty.
Setup (do this before anything else):
1. Prerequisite check: run `git fetch origin && git show origin/dev:backend/src/main/java/de/fhdortmund/nifty/audit/service/AuditService.java > /dev/null && git show origin/dev:frontend/package.json | grep -q '"test":'`. If it fails, Wave 0 (`backend-foundation` with the US-BF-06 contract stubs, and `frontend-foundation`) is not on `dev` yet. Stop and tell the human.
2. From the main checkout, run `git worktree add ../nifty-context-creation -b feat/context-creation origin/dev`. Start every Bash command with `cd /absolute/path/to/nifty-context-creation && pwd`, and pass only absolute worktree paths to Edit/Write. `forge.mjs` reads `.env` from the main checkout, so do not copy it. Never read `.env` yourself.
3. The shared interfaces, records and enums of `docs/tickets/00-parallel-waves-and-contracts.md` §3 already exist as stubs on `dev`. Implement only the ones your slice owns. Mock the others. Never create a second copy of a shared type.

Read these files first (do NOT scan the whole source tree):
1. `AGENTS.md`, `.agents/rules/coding-standards.md`, `.agents/rules/backend-java.md`
2. `docs/architecture.md`, `docs/api.md`, `docs/tickets/00-parallel-waves-and-contracts.md`
3. `openspec/specs/context-creation/spec.md`
4. `openspec/changes/context-creation/{proposal.md,design.md,tasks.md,specs/context-creation/spec.md}`

Non-negotiable rules:
- Use the `spec-implementer` skill for the tasks and the `pr-memo` skill for the PR/MR body. Commit after each task, but push only after the human says yes. Never merge. Ask the human before you push or open the PR/MR. Update `tasks.md` `## Status` before every stop, so another session can resume.
- Follow TDD strictly (RED failing test -> GREEN minimal code -> REFACTOR) for US-CC-01 through US-CC-06 in `tasks.md`.
- Keep `context` stateless (`web/` and `service/` only; no `repository/` or Flyway migration).
- Implement exact `ContextCreationService` and `context/service/dto/*` signatures from `docs/tickets/00-parallel-waves-and-contracts.md`.
- Enforce compile-time rubric isolation on `InterviewerContextInput` (reflection test), zero Java Streams in `PromptSanitizer`/`InjectionDetector`/formatting hot loops, ascending `0..4` rubric order in `JudgeContextBuilder`, Decision 2.20 partial timeout inclusion, and `AuditService` logging with zero raw PII on `injectionSuspected == true`.
- Mount preview endpoint at `POST /api/v1/ranking/context/preview/interviewer` (`PROFESSOR` only, `CANDIDATE` -> `403`, invalid body -> `400` ProblemDetail). Update `docs/api.md` and add `docs/ai_logs/YYYY-MM-DD-context-creation.md` (+ index row).
- Run `./mvnw -B test` (including `ArchitectureTest`), archive the change before pushing, and open PR/MR into `dev` via `node scripts/forge.mjs`.
```
