# Domain Spec: Formula Engine (`formula-engine`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-03, §11 & §14)
> **Primary View:** Ranking View (`PROFESSOR`) + Backend deterministic evaluator

## Purpose

Give professors transparent, versioned, and strictly deterministic control over question weights, skill weights, coverage thresholds, and fit formulas—with live what-if previews before publishing changes.

## Scope

- Per-core-question weights (`w_q`) and per-skill weights (`w_s`).
- Default deterministic formulas:
  - Skill score: `sum(w_q * level * c) / sum(w_q * c)` over verified evidence; `N/A` if none.
  - Fit: `sum(w_s * score(s)) / sum(w_s)` over scored skills only.
  - Coverage: `sum(w_s for scored skills) / sum(w_s for all skills)` (always displayed separately from fit).
- Formula presets (weighted mean, minimum-coverage-gated, must-have skills).
- Optional sandboxed expression editor (SpEL `SimpleEvaluationContext` or custom allowlisted parser supporting only `score(s)`, `w(s)`, `coverage`, `min`, `max`, `if`—never arbitrary code execution).
- Live what-if preview showing reordering impact on sample candidates prior to saving.
- Immutable formula versioning linked to every computed score.

## Seed Requirements (EARS)

- **REQ-FE-01:** **THE SYSTEM SHALL** calculate all totals, rankings and team-balance metrics in code, and **SHALL NOT** take any numerical value from LLM output without validation.
- **REQ-FE-02:** **WHEN** a professor modifies weights or formula settings **THE SYSTEM SHALL** store a new immutable formula version and record which formula version produced every computed candidate score.
- **REQ-FE-03:** **THE SYSTEM SHALL** compute and expose candidate `fit` and `coverage` as separate metrics, excluding `N/A` skills from the `fit` calculation and flagging candidates below the configured minimum coverage threshold.
- **REQ-FE-04:** **WHERE** custom formula expressions are enabled **THE SYSTEM SHALL** evaluate them strictly inside a sandboxed context restricted to allowlisted variables and functions.
