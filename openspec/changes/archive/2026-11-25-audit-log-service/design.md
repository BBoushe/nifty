# Design: Audit Log Service

## 1. Sources

`docs/tickets/00-parallel-waves-and-contracts.md` §1 and §3.2, `docs/feature_backlog.md` §4, `docs/architecture.md` §5 (`AUDIT_LOG`), `.agents/rules/backend-java.md`.

## 2. Decisions inside this change

- **Entity:** `audit.repository.AuditLogEntity` maps `audit_log` exactly as `V1` defines it: `id BIGSERIAL`, `entity_id VARCHAR` (the UUID as text), `created_at` set by the database. Mark it `@org.hibernate.annotations.Immutable`.
- **Repository:** `audit.repository.AuditLogRepository extends org.springframework.data.repository.Repository<AuditLogEntity, Long>` with `save` and read methods only. No `delete*` and no update query.
- **Service:** `audit.service.AuditServiceImpl implements AuditService`, `@Transactional` (default propagation, so the row joins the caller's transaction). It rejects a null `actorId`, `actorRole`, `entityType`, `entityId` or `action` with `IllegalArgumentException`. It stores `actorRole.name()` and `entityId.toString()`. It never logs `newValue` or `oldValue`.
- **Placeholder:** delete `AuditServicePlaceholder`. `ContractStubsTest` then skips `AuditService` by design.
