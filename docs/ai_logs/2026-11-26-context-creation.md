# AI Log: Context Creation Slice (2026-11-26)

## Prompt

The orchestrator asked a builder agent to implement `context-creation` (stories US-CC-01 to US-CC-06) with strict TDD. The agent had to stay inside the `context` slice and mock `AuditService`.

## Inputs

- `AGENTS.md`, `.agents/rules/coding-standards.md`, `.agents/rules/backend-java.md`
- `docs/tickets/00-parallel-waves-and-contracts.md` §3.5, `docs/tickets/02-context-creation.md`
- `openspec/specs/context-creation/spec.md` and `openspec/changes/context-creation/`
- Decisions 1.12, 1.18, 1.19, 1.21, 2.20, 2.24, 2.25

## Decisions

- **1.12:** `InterviewerContextInput` has no rubric field. A reflection test and a prompt term check (`PromptTemplates.isRubricBlind`) keep it so.
- **2.24:** Only `inspectCandidateInput` writes the audit row `PROMPT_INJECTION_DETECTED`. The `build*` methods return the report and never audit. One answer then writes one row, not one row per later turn.
- **2.24:** The audit value holds `promptSha256` (SHA-256 of the raw answer) and rule IDs. It never holds raw text.
- **2.24:** The escaped tag list leaves out `user`, `human` and `tool`, so `List<User>` in a Java answer stays unchanged. A quote that spans an escaped tag fails word-for-word verification and the system discards it.
- **2.20:** `JudgeContextBuilder` throws `IllegalArgumentException` for blank answers. Timed-out non-blank answers get `timedOut="true"`.
- **2.25:** The judge prompt asks for one JSON object and forbids totals, fit, coverage, rankings and confidence.
- **Local choice:** The preview request uses boxed `Integer followUpLimit`. Jackson 3 answers a missing primitive with a 400 that names no field.

## Outputs

- `context/service`: builders, `PromptSanitizer`, `InjectionDetector`, `PromptHasher`, `PromptTemplates`, `ContextCreationServiceImpl`.
- `context/web`: `ContextController` and its request and response records.
- Deleted `ContextCreationServicePlaceholder`. No contract stub changed. No migration.
- `docs/api.md` has the preview endpoint.

## Human gates

- The human approves the push and the PR. The agent does not push.

## Open items

- `audit-log-service` replaces `AuditServicePlaceholder`. Until then, a flagged answer makes `inspectCandidateInput` throw.
- `ContractStubsTest.placeholderMethodsThrowUnsupportedOperation` skips as a whole when any placeholder is gone. The last owner to delete a placeholder should make that check per interface.
