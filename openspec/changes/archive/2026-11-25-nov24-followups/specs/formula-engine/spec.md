# Spec Delta: Formula Engine

## MODIFIED Requirements

### Requirement: REQ-FE-03 Separate Fit and Coverage Reporting
**THE SYSTEM SHALL** compute and expose candidate `fit` and `coverage` as separate metrics. `fit` excludes `N/A` skills. `coverage` is the weight of answered, scored core questions divided by the weight of all core questions. A question without answer text counts as unanswered. A timed-out answer with text counts as answered (Decision 2.20). **THE SYSTEM SHALL** flag an attempt below the configured minimum coverage (Decision 2.12).

#### Scenario: Low-coverage candidate flagged separately
- **WHEN** a configuration has 4 core questions with weights 1, 1, 1 and 2, question 4 timed out with no answer text, and the minimum coverage is 0.70
- **THEN** the system reports `fit` over the scored skills only, reports `coverage = 0.60` separately, and flags the attempt for low coverage.
