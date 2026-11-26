# Proposal: Assessment Chat, Immutable Ledger & BFI-10 (`assessment-chat`)

## Summary

Implement the `assessment` vertical slice (`de.fhdortmund.nifty.assessment` + `frontend/src/views/assessment/` chat/BFI-10 UI) for candidate interview execution, append-only ledger persistence, deterministic paste-velocity and prompt-injection flagging, and single-source Java BFI-10 scoring (Decisions 1.11, 1.18, 1.19, 2.20, 2.23, 2.24, 4.3).

## Scope

- Flyway `V6__assessment.sql` (`assessment_attempt`, `conversation_entry` with `injection_suspected` + immutability trigger, `bfi_response`).
- Attempt state machine (`IN_PROGRESS`, `BFI10`, `COMPLETED`) with `SOFT_SKILLS`-only skip-to-BFI-10 routing, server-enforced deadlines, grace window, expiry sweep, and attempt cap (`409`).
- Delegated prompt construction via `ContextCreationService.buildInterviewerContext` (zero duplicate prompt builder) and SSE streaming via Spring AI 2.0 `ChatClient`.
- Single-source BFI-10 reverse-scoring (`6 - score`) and `AssessmentQueryService` read contracts (`getTranscriptForAttempt`, `getSoftSkillProfilesForConfig`) for `evaluation` and `teams`.
- `AttemptCompletedEvent(attemptId, configId, inviteId)` on commit and `AssessmentDeletedEvent` GDPR purge listener.
- Vue 3.5 + PrimeVue chat + countdown + BFI-10 views under `frontend/src/views/assessment/`.

## Out of Scope

- Building interviewer prompts locally (delegated to `ContextCreationService`).
- Candidate results display (`evaluation` slice + `views/assessment/ResultsView.vue`).
- Professor transcript export endpoint (`/api/v1/ranking/transcripts/{attemptId}` lives in `evaluation` reading `AssessmentQueryService`).
