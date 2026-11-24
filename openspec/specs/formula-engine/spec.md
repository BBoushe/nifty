# Domain Spec: Formula Engine (`formula-engine`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-03, §11 & §14)
> **Primary View:** Ranking View (`PROFESSOR`) + Backend deterministic evaluator

## Purpose

Give professors transparent, versioned, and strictly deterministic control over question weights, skill weights, coverage thresholds, and fit formulas. Professors get live what-if previews before they publish changes.

## Requirements

### Requirement: REQ-FE-01 Deterministic Code-Only Scoring
**THE SYSTEM SHALL** calculate all totals, rankings and team-balance metrics in code, and **SHALL NOT** take any numerical value from LLM output without validation.

#### Scenario: Formula engine computes skill score and fit
- **WHEN** verified evidence items and configured question/skill weights are supplied
- **THEN** the Java formula engine deterministically computes `score(s)`, `fit`, and `coverage` with reproducible outputs.

### Requirement: REQ-FE-02 Versioned Formula Auditability
**WHEN** a professor modifies weights or formula settings **THE SYSTEM SHALL** store a new immutable formula version and record which formula version produced every computed candidate score.

#### Scenario: Weight update creates new formula version
- **WHEN** a professor adjusts a skill weight and saves the formula
- **THEN** the system persists a new immutable formula version and tags recomputed candidate scores with that version identifier.

### Requirement: REQ-FE-03 Separate Fit and Coverage Reporting
**THE SYSTEM SHALL** compute and expose candidate `fit` and `coverage` as separate metrics, excluding `N/A` skills from the `fit` calculation and flagging candidates below the configured minimum coverage threshold.

#### Scenario: Low-coverage candidate flagged separately
- **WHEN** a candidate has verified evidence for only 40% of weighted skills and the minimum coverage threshold is 60%
- **THEN** the system reports `fit` over the scored skills only, reports `coverage = 0.40` separately, and flags the candidate for low coverage.

### Requirement: REQ-FE-04 Sandboxed Custom Formula Expressions
**WHERE** custom formula expressions are enabled **THE SYSTEM SHALL** evaluate them strictly inside a sandboxed context restricted to allowlisted variables and functions.

#### Scenario: Disallowed expression rejected by sandbox
- **WHEN** an expression attempts to invoke a method or class outside the allowlisted formula variables and functions (`score(s)`, `w(s)`, `coverage`, `min`, `max`, `if`)
- **THEN** the sandboxed evaluator rejects the expression with a validation error without executing arbitrary code.
