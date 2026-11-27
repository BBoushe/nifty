## ADDED Requirements

### Requirement: REQ-TB-13 Diagnostics Precision
**THE SYSTEM SHALL** store team diagnostics at full precision after both generate and recalculate. The UI rounds for display only.

#### Scenario: Recalculate after a drag
- **WHEN** the professor moves a student and the proposal is recalculated
- **THEN** the stored diagnostics equal those of a fresh computation on the same teams, with no rounding.
