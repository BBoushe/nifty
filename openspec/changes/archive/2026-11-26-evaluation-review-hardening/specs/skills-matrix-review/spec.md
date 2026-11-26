## MODIFIED Requirements

### Requirement: REQ-SM-03 Individual Review Only (No Bulk Approval)
**WHERE** an attempt is not auto-released (REQ-SM-05), **THE SYSTEM SHALL** require the professor to approve or override each flagged evaluation of that attempt individually. **THE SYSTEM SHALL** also require the professor to release that attempt individually. **THE SYSTEM SHALL NOT** provide bulk approval (Decision 2.30).

#### Scenario: Professor reviews an attempt individually
- **WHEN** the professor opens an attempt that is not auto-released in the Evidence Drawer
- **THEN** the professor approves or overrides each flagged evaluation individually before releasing the attempt, with no bulk-approval action in the UI or API.

#### Scenario: Auto-released attempt needs no professor action
- **WHEN** every score of an attempt meets its threshold and no score has a flag (REQ-SM-05)
- **THEN** the attempt is auto-released without professor action, and REQ-SM-03 does not apply.

#### Scenario: Second release gets 409
- **WHEN** the professor releases an attempt that is already released
- **THEN** the system answers `409`, keeps the original release time, and writes no audit log entry.

#### Scenario: Two concurrent releases
- **WHEN** two release requests for one unreleased attempt arrive at the same time
- **THEN** exactly one request releases the attempt and writes one `ATTEMPT_RELEASED` audit log entry, and the other request gets `409`.
