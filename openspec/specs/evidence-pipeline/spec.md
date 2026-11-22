# Domain Spec: Evidence Pipeline (`evidence-pipeline`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §9, §10, §11 & §14)
> **Owner Layer:** Backend (`Spring AI 2.0` + deterministic Java verifiers/calculators)

## Purpose

Transform completed `assessment-transcript/v1` transcripts into verified, auditable evidence items, rubric levels, and deterministically computed confidence values—eliminating hallucinated quotes and unsupported scores.

## Scope

- **Step 2 — Evidence Extractor (LLM structured output):** Schema-validated extraction of `{ entryId, skill, quote, suggestedLevel, rationale, inferredTags }`. Never computes final scores.
- **Step 3 — Quote Verifier (deterministic Java):** Exact word-for-word substring verification against the referenced transcript entry; discards unverified items.
- **Step 4 — Constrained Judge & Scorer:** Two independent judge runs for level boundaries; combines verified evidence via the Formula Engine; records `N/A` when no verified evidence exists.
- **Step 5 — Confidence Calculator (deterministic Java):** Computes `0..1` confidence from measurable signals (evidence count vs. target, quote verification ratio, judge run agreement, question origin, prompted vs. unprompted terms)—never LLM self-report.
- Asynchronous background job execution with replayable recorded LLM fixtures for deterministic testing.

## Seed Requirements (EARS)

- **REQ-EP-01:** **WHEN** the extractor returns an evidence item **THE SYSTEM SHALL** verify that its quote appears word-for-word in the referenced transcript entry and discard it if it does not.
- **REQ-EP-02:** **IF** a skill has no verified evidence **THEN THE SYSTEM SHALL** record its score as `N/A` and exclude it from fit calculations.
- **REQ-EP-03:** **THE SYSTEM SHALL** compute evidence and skill confidence deterministically from measurable signals (evidence count, quote verification ratio, judge agreement, question origin, and prompted vs. unprompted terms) and **SHALL NOT** use LLM self-reported confidence values.
- **REQ-EP-04:** **THE SYSTEM SHALL** calculate all totals, rankings and team-balance metrics in code, and **SHALL NOT** take any numerical value from LLM output without validation.
