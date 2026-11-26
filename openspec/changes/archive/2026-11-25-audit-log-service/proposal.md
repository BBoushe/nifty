# Proposal: Audit Log Service (`audit-log-service`)

## Context & Problem

Studio, context, evaluation and teams write audit rows through `AuditService` (Decision 1.16). On `dev`, `AuditService` is only a placeholder that throws. No slice can write a real audit row.

## Scope

- US-AL-01: `AuditLogEntity` over the existing `V1` `audit_log` table and `AuditServiceImpl.record(...)` with the contract-pack §3.2 signature. Delete `AuditServicePlaceholder`.
- US-AL-02: no update or delete path. A test proves that a saved row cannot change.

## Out of Scope

- An Audit Log page or read endpoint (later change).
- Any new migration. `V1` stays as merged.
