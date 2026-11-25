# 2026-11-25 — Five-Service Convergence & Parallel Ticket Pack

## Prompt

Cross-reference all five teammate service artifacts against each other and the live codebase (`fe517aa`). Resolve discrepancies once, sync the canonical source of truth, and produce five disjoint OpenSpec change packs plus runnable team tickets and a mocking map.

## Inputs

- Live codebase: `ArchitectureRules.java`, `SecurityConfig.java`, `Role.java`, `docs/architecture.md`, `docs/api.md`, `docs/decisions/index.md`, `docs/feature_backlog.md`, and all 8 `openspec/specs/*/spec.md`.
- Five teammate design drafts (one per teammate R, A, S, M, C). They are local working files in `docs/artifacts/`, which git ignores. The tickets in `docs/tickets/` keep the agreed content, so the trail does not need the drafts.

## Decisions

| ID | Decision |
|---|---|
| 1.18 | Unified 7-slice backend map (`auth`, `audit`, `studio`, `context`, `assessment`, `evaluation`, `teams`). Entities live in `<slice>/repository/`. `context` is sole prompt builder. |
| 1.19 | Use `UUID` IDs across all slices. Publish `AttemptCompletedEvent`. Use per-skill 5-level `List<String>` rubrics. Keep all professor routes under `/api/v1/ranking/**` and return `400` RFC 9457 `ProblemDetail`. |
| 1.20 | Seeded pure-Java Simulated Annealing + 2-opt team optimizer replaces Timefold in MVP. It excludes `N/A` skills gracefully. |
| 2.21 | One-shot immutable assessment creation (`Idempotency-Key`, DB triggers) with `max_attempts`, `invented_questions`, and GDPR `DELETE` kept. |
| 2.22 | Question-weighted scoring without `SKILL.weight`. Follow-ups inherit `1.0` weight, `D2` caps each entry at `0.50`, and APIs use `0..1` decimals. |
| 2.23 | Store grouping config fields on `assessment_config`. `SOFT_SKILLS`-only mode allows `0` questions when `bfi10Enabled = true`. |
| 2.24 | `context` owns deterministic `PromptSanitizer` and `InjectionDetector`. `assessment` persists `injection_suspected` and velocity `paste_suspected`. `evaluation` flags both. |
| 1.21 | `context` builds two separate contexts per attempt. A retake gets a new attempt ID, so no context reads another attempt. |
| 2.25 | The evaluator returns one JSON object per (entry, skill). Code validates it and computes every aggregate and ranking. |

## Outputs

- Decisions `1.18–1.21` and `2.21–2.25` recorded via `scripts/record-decision.mjs`, with Supersedes lines.
- Updated `docs/architecture.md`, `docs/api.md`, `docs/feature_backlog.md`, the workflow ownership table and the rules. Removed Timefold from the build and the docs.
- Spec changes went through the archived change `openspec/changes/archive/2026-11-25-five-service-convergence/`. Old requirement IDs keep their meaning.
- Five vertical-split change packs in `openspec/changes/{studio-assessment-creation,context-creation,assessment-chat,evaluation-service,team-builder}/`.
- Runnable teammate briefs and contract mocking pack in `docs/tickets/00-parallel-waves-and-contracts.md` and `docs/tickets/01..05-*.md`.

## Human Gates

- The prompter approved all eight convergence defaults (Q1–Q8) and requested both runnable team prompts (`docs/tickets/`) and OpenSpec vertical-split change packs (`openspec/changes/`).
- A cold review asked for changes (direct spec edits, reused IDs, weakened rules, unreleased scores reaching teams). The prompter chose the fixes, confirmed Simulated Annealing and asked for teammate initials instead of names.
- Merging `chore/five-service-convergence` into `dev` waits for human approval (Decision 3.11).

## Open Items

- Wave 0 (`backend-foundation`, `frontend-foundation`) and Wave 1 (`auth-accounts`, `audit-log-service`) run first via orchestrator so shared `FieldValidationException`, `AuditService`, and `AuthContextService` land on `dev`.
- Teammates start immediately in parallel using the locked interfaces in `docs/tickets/00-parallel-waves-and-contracts.md`.
