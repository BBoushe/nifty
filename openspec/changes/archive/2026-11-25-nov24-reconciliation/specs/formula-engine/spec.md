# Spec Delta: Formula Engine

## MODIFIED Requirements

### Requirement: REQ-FE-03 Separate Fit and Coverage Reporting
**THE SYSTEM SHALL** compute and expose candidate `fit` and `coverage` as separate metrics. `fit` excludes `N/A` skills. `coverage` is the weight of answered, scored core questions divided by the weight of all core questions; a timed-out question counts as unanswered. **THE SYSTEM SHALL** flag an attempt below the configured minimum coverage (Decision 2.12).

#### Scenario: Low-coverage candidate flagged separately
- **WHEN** a configuration has 4 core questions with weights 1, 1, 1 and 2, question 4 timed out, and the minimum coverage is 0.70
- **THEN** the system reports `fit` over the scored skills only, reports `coverage = 0.60` separately, and flags the attempt for low coverage.

## REMOVED Requirements

### Requirement: REQ-FE-04 Sandboxed Custom Formula Expressions
**Reason**: MVP ships formula presets only (Decision 2.19).
**Migration**: The Post-MVP backlog item "SpEL formula editor" adds this requirement back. REQ-FE-05 covers MVP formula choice.

## ADDED Requirements

### Requirement: REQ-FE-05 Formula Presets
**THE SYSTEM SHALL** offer a fixed set of named formula presets, with the weighted formula of the discovery canvas §11 as the default, and let the professor choose one per assessment. **THE SYSTEM SHALL NOT** evaluate free-form formula expressions in MVP.

#### Scenario: Professor picks the default preset
- **WHEN** the professor creates an assessment and keeps the default preset
- **THEN** the formula version stores the preset name, and the engine computes `score(s)`, `fit` and `coverage` with it.

#### Scenario: Expression in the request
- **WHEN** a request to the formula endpoint contains a free-form expression field
- **THEN** the system answers `400` and stores no formula version.
