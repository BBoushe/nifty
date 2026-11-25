# Domain Spec: Formula Engine (`formula-engine`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-03, §11 & §14), updated 2026-11-25 by `nov24-reconciliation`
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
**THE SYSTEM SHALL** compute and expose candidate `fit` and `coverage` as separate metrics. `fit` excludes `N/A` skills. `coverage` is the weight of answered, scored core questions divided by the weight of all core questions; a timed-out question counts as unanswered. **THE SYSTEM SHALL** flag an attempt below the configured minimum coverage (Decision 2.12).

#### Scenario: Low-coverage candidate flagged separately
- **WHEN** a configuration has 4 core questions with weights 1, 1, 1 and 2, question 4 timed out, and the minimum coverage is 0.70
- **THEN** the system reports `fit` over the scored skills only, reports `coverage = 0.60` separately, and flags the attempt for low coverage.

### Requirement: REQ-FE-05 Formula Presets
**THE SYSTEM SHALL** offer a fixed set of named formula presets, with the weighted formula of the discovery canvas §11 as the default, and let the professor choose one per assessment. **THE SYSTEM SHALL NOT** evaluate free-form formula expressions in MVP.

#### Scenario: Professor picks the default preset
- **WHEN** the professor creates an assessment and keeps the default preset
- **THEN** the formula version stores the preset name, and the engine computes `score(s)`, `fit` and `coverage` with it.

#### Scenario: Expression in the request
- **WHEN** a request to the formula endpoint contains a free-form expression field
- **THEN** the system answers `400` and stores no formula version.
