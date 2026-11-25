# Proposal: Context Creation Service (`context-creation`)

## Summary

Implement the stateless `context` backend slice (`de.fhdortmund.nifty.context`) as the sole deterministic prompt-assembly, dual-agent rubric-isolation, and prompt-injection-hardening boundary for Agent 1 (Interviewer) and Agent 2 (2-Run Judge Evaluator) (Decisions 1.12, 1.18, 1.19, 2.24).

## Scope

- Public `ContextCreationService` interface and immutable record DTOs (`InterviewerContextInput`, `JudgeContextInput`, `BuiltPromptContext`, `SanitizationReport`, `AgentRole`).
- Pure deterministic zero-Stream `PromptSanitizer`, `InjectionDetector`, `PromptHasher` (`SHA-256`), `InterviewerContextBuilder`, and `JudgeContextBuilder` built on an extensible `PromptContextBuilder<I>` contract.
- Security audit logging through `AuditService` when `injectionSuspected == true` (metadata + `promptSha256` + rule IDs, zero raw PII).
- `PROFESSOR`-only preview endpoint `POST /api/v1/ranking/context/preview/interviewer`.

## Out of Scope

- Calling `ChatClient` (owned by `assessment` and `evaluation`).
- Database tables or Flyway migrations (stateless slice per `.agents/rules/backend-java.md` §8).
- Secondary fresh-context builders for UX-10 AI narrative advice or Team Builder Mode B (Post-MVP plug-ins on `PromptContextBuilder<I>`).
