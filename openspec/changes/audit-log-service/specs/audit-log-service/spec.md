# Spec Delta: Audit Log Service (`audit-log-service`)

## ADDED Requirements

### Requirement: REQ-AL-01 Single Audit Writer
**THE SYSTEM SHALL** write `audit_log` rows only through `AuditService.record(...)`, and **SHALL NOT** let any class outside the `audit` slice use `AuditLogRepository`.

#### Scenario: Override recorded
- **WHEN** a service calls `record("prof-1", PROFESSOR, "RESPONSE_EVALUATION", id, "SCORE_OVERRIDDEN", "2", "3", "Clear explanation")`
- **THEN** one `audit_log` row holds these values and a database `created_at`.

### Requirement: REQ-AL-02 Immutable Audit Rows
**THE SYSTEM SHALL NOT** offer any way to update or delete an `audit_log` row.

#### Scenario: Changed entity is not written
- **WHEN** a test loads a saved `AuditLogEntity`, changes its `reason` and flushes
- **THEN** the database row keeps the original `reason`.
