# Design: Context Creation (`context`)

> **Canonical Contract Reference:** `docs/architecture.md`, `docs/api.md`, `docs/tickets/00-parallel-waves-and-contracts.md`, `docs/tickets/02-context-creation.md`.

## Package Layout (`de.fhdortmund.nifty.context` — Stateless: `web/` + `service/` Only)
- `web/`: `ContextController` (`POST /api/v1/ranking/context/preview/interviewer`, `PROFESSOR` only), `InterviewerPreviewRequest` (`@Valid`), `ContextPreviewResponse`.
- `service/`: `ContextCreationService` (public interface), `ContextCreationServiceImpl` (`@Service`), `PromptContextBuilder<I>` (package-private), `InterviewerContextBuilder` (`interviewer-v1`), `JudgeContextBuilder` (`evaluator-judge-v1`), `PromptSanitizer` (zero Streams), `InjectionDetector` (zero Streams), `PromptHasher` (SHA-256), `PromptTemplates`.
- `service/dto/`: `AgentRole`, `BuiltPromptContext`, `PromptMessage`, `SanitizationReport`, `InterviewerContextInput`, `CoreQuestionPromptView`, `DialogueTurnView`, `JudgeContextInput`.

## Key Design Rules
- **Compile-time rubric isolation:** `InterviewerContextInput` declares zero fields for rubrics, weights, thresholds, scores, or BFI-10. Reflection test enforces this permanently.
- **Agent 2 alignment (Decision 1.19):** `buildJudgeContext(JudgeContextInput)` builds the fresh-context prompt for one `(entryId, skillId)` pair using `List<String> rubricLevels` (size 5, index `0..4` ascending), rejects blank/null answer text (`IllegalArgumentException` / skipped upstream), wraps non-blank timed-out partial answers with `timedOut="true"` metadata (Decision 2.20), and enforces `{ level, verbatimQuote, rationale, suggestion? } (REQ-EP-08, Decision 2.25)` JSON output with zero totals or self-reported confidence.
- **Hotspot performance:** Zero Java Streams inside `PromptSanitizer`, `InjectionDetector`, or template formatting loops (`.agents/rules/coding-standards.md` #3).
- **Audit trail:** Only `inspectCandidateInput` writes audit rows. Assessment calls it once per answer, so one answer gives one row. When `injectionSuspected == true`, it calls `AuditService.record(...)` with `attemptId`, `promptSha256` (SHA-256 of the raw answer), and `matchedRuleIds`. It never records raw candidate text. The `build*` methods only return the report.
