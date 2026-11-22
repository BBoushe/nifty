# Domain Spec: Evidence Pipeline (`evidence-pipeline`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §9, §10, §11 & §14)
> **Owner Layer:** Backend (`Spring AI 2.0` + deterministic Java verifiers/calculators)

## Purpose

Transform completed `assessment-transcript/v1` transcripts into verified, auditable evidence items, rubric levels, and deterministically computed confidence values—eliminating hallucinated quotes and unsupported scores.

## Requirements

### Requirement: REQ-EP-01 Verbatim Quote Verification
**WHEN** the extractor returns an evidence item **THE SYSTEM SHALL** verify that its quote appears word-for-word in the referenced transcript entry and discard it if it does not.

#### Scenario: Hallucinated quote rejected by deterministic verifier
- **WHEN** the LLM extractor emits an evidence item whose `quote` is not an exact substring of the referenced `entryId` answer text
- **THEN** the deterministic quote verifier discards the item before scoring.

### Requirement: REQ-EP-02 Explicit N/A for Missing Evidence
**IF** a skill has no verified evidence **THEN THE SYSTEM SHALL** record its score as `N/A` and exclude it from fit calculations.

#### Scenario: Untested skill produces N/A instead of 0 or 1
- **WHEN** a candidate transcript has zero verified evidence items for skill `spring_boot`
- **THEN** the scorer records `spring_boot = N/A` and omits `spring_boot` from both the numerator and denominator of the candidate's fit score.

### Requirement: REQ-EP-03 Deterministic Confidence Calculation
**THE SYSTEM SHALL** compute evidence and skill confidence deterministically from measurable signals (evidence count, quote verification ratio, judge agreement, question origin, and prompted vs. unprompted terms) and **SHALL NOT** use LLM self-reported confidence values.

#### Scenario: Judge disagreement lowers calculated confidence
- **WHEN** two independent judge runs disagree on a rubric level boundary
- **THEN** the deterministic confidence calculator reduces the skill's `0..1` confidence score based on the documented signal formula.

### Requirement: REQ-EP-04 Deterministic Numerical Aggregation
**THE SYSTEM SHALL** calculate all totals, rankings and team-balance metrics in code, and **SHALL NOT** take any numerical value from LLM output without validation.

#### Scenario: Pipeline discards unvalidated LLM totals
- **WHEN** the evidence pipeline processes a completed transcript
- **THEN** all skill averages, fit totals, coverage ratios, and cohort metrics are computed exclusively in deterministic Java/SQL code.
