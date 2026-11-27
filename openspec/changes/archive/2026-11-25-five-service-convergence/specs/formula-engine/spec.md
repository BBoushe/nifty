# Spec Delta: Formula Engine

## MODIFIED Requirements

### Requirement: REQ-FE-01 Deterministic Code-Only Scoring
**THE SYSTEM SHALL** calculate all totals, rankings and team-balance metrics in code, and **SHALL NOT** take any numerical value from LLM output without validation (Decision 2.25).

#### Scenario: Formula engine computes skill score and fit
- **WHEN** verified evidence items and the configured question weights are supplied
- **THEN** the Java formula engine deterministically computes `score(s)`, `fit`, and `coverage` with reproducible outputs.

### Requirement: REQ-FE-02 Versioned Formula Auditability
**WHEN** a professor changes the formula settings or overrides a level **THE SYSTEM SHALL** store a new immutable formula version when the settings change, record which formula version produced every computed candidate score, and recompute the affected scores, `fit` and `coverage` in code from the stored evaluations, with no LLM call.

#### Scenario: Weight update creates new formula version
- **WHEN** a professor picks another formula preset for an assessment and saves it
- **THEN** the system persists a new immutable formula version and tags recomputed candidate scores with that version identifier.

#### Scenario: Override recomputes without the LLM
- **WHEN** a professor overrides an entry level from 2 to 3
- **THEN** the affected skill score, `fit` and `coverage` are recomputed in Java, and no `ChatClient` call occurs.

### Requirement: REQ-FE-03 Separate Fit and Coverage Reporting
**THE SYSTEM SHALL** compute and expose candidate `fit` and `coverage` as separate metrics. `fit` is the unweighted mean of the scored skills (`null` if none) and excludes `N/A` skills. `coverage` is the weight of answered, scored core questions divided by the weight of all core questions. Only a question without answer text counts as unanswered (Decision 2.20). **THE SYSTEM SHALL** flag an attempt below the configured minimum coverage (Decisions 2.12, 2.22).

#### Scenario: Low-coverage candidate flagged separately
- **WHEN** a configuration has 4 core questions with weights 1, 1, 1 and 2, question 4 timed out with no answer text, and the minimum coverage is 0.70
- **THEN** the system reports `fit` over the scored skills only, reports `coverage = 0.60` separately, and flags the attempt for low coverage.

### Requirement: REQ-FE-05 Formula Presets
**THE SYSTEM SHALL** offer a fixed set of named formula presets, with the question-weighted mean `WEIGHTED_MEAN` (REQ-FE-06) as the default, and let the professor choose one per assessment. **THE SYSTEM SHALL NOT** evaluate free-form formula expressions in MVP (Decision 2.19).

#### Scenario: Professor picks the default preset
- **WHEN** the professor creates an assessment and keeps the default preset
- **THEN** the formula version stores the preset name, and the engine computes `score(s)`, `fit` and `coverage` with it.

#### Scenario: Expression in the request
- **WHEN** a request to the formula endpoint contains a free-form expression field
- **THEN** the system answers `400` and stores no formula version.

## ADDED Requirements

### Requirement: REQ-FE-06 Question-Weighted Skill Score
**THE SYSTEM SHALL** compute each skill score in Java as the weighted mean of its verified levels. An entry weighs as much as its core question (a follow-up `1.0 ×`, an invented question `0.5 ×` the active core weight). Skills have no weight. **WHEN** a skill has 2 or more scored entries **THE SYSTEM SHALL** cap the weight share of each entry at `0.50`, give the excess to the other entries by weight, and repeat until no share exceeds `0.50` (Decisions 2.2, 2.4, 2.22).

#### Scenario: Weight cap on two core questions
- **WHEN** skill `git_conflicts` has a level 4 on core question 1 (weight 3) and a level 2 on core question 2 (weight 1)
- **THEN** the cap sets each share to `0.50`, and the skill score is `3.00`.

#### Scenario: Follow-up inherits the core weight
- **WHEN** skill `sql` has a level 3 on core question 2 (weight 2) and a level 1 on a follow-up to core question 2
- **THEN** both entries use weight 2, and the skill score is `2.00`.

#### Scenario: Cap with three entries
- **WHEN** skill `docker` has levels 4, 2 and 0 on core questions with weights 3, 1 and 1
- **THEN** the shares are `0.50`, `0.25` and `0.25`, and the skill score is `2.50`.
