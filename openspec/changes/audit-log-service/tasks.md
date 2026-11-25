# Tasks: `audit-log-service`

## Status
- **Branch:** `feat/audit-log-service`
- **Worktree:** `../nifty-audit-log-service`
- **Current:** not started
- **Next action:** Task 1
- **Uncommitted changes:** no
- **PR / MR:** none yet
- **Open review findings:** none

## Tasks

### Task 1: US-AL-01 AuditService implementation (REQ-AL-01)
- [x] RED: `AuditServiceImplTest` (`@SpringBootTest`, extends `PostgresContainerBase`): `record(...)` stores one row with every field; a null `action` throws `IllegalArgumentException`.
- [x] GREEN: `AuditLogEntity`, `AuditLogRepository`, `AuditServiceImpl` (design §2); delete `AuditServicePlaceholder`.
- [x] Verify: `cd backend && ./mvnw -B test` (incl. `ArchitectureTest`, `ContractStubsTest`) → exit 0; commit `feat(audit): implement AuditService over the V1 audit_log table`

### Task 2: US-AL-02 Immutable rows (REQ-AL-02)
- [x] RED: test that changes a loaded entity's `reason`, flushes, and reads the row back with `JdbcTemplate`; it fails if the entity is not `@Immutable`.
- [x] GREEN: `@Immutable` on the entity; the repository has no delete or update method.
- [x] Verify: `cd backend && ./mvnw -B test` → exit 0; commit `test(audit): prove audit rows cannot change`

### Task 3: Docs
- [ ] `docs/architecture.md` (`AuditService` is real), `docs/tickets/00-parallel-waves-and-contracts.md` §1 conventions (audit placeholder gone), `CHANGELOG.md`
- [ ] Verify: harness tests → exit 0; commit `docs: record audit-log-service`

## Delivery
- [ ] `git fetch origin && git merge origin/dev` (never rebase a pushed branch, no force pushes)
- [ ] Run `cd backend && ./mvnw -B test`, `cd frontend && npm run typecheck && npm run test && npm run lint && npm run build`, `OPENSPEC_TELEMETRY=0 openspec validate --all --strict` and `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` → exit 0
- [ ] Archive: `OPENSPEC_TELEMETRY=0 openspec archive audit-log-service -y --skip-specs`, set its `docs/feature_backlog.md` row to `done`, add a `docs/feature_history.md` entry (`PR/MR: pending`), commit `chore(specs): archive audit-log-service`
- [ ] Ask the human, then `node scripts/forge.mjs push` and open the PR/MR with `pr-memo` (`node scripts/forge.mjs pr ... --base dev`)
- [ ] Stop. Teammate A runs the cold review, checks CI and merges.

## Handoff Log

### Task 1 (US-AL-01)
- Commits: `feat(audit): implement AuditService over the V1 audit_log table`
- Verification: RED: `./mvnw -B test -Dtest=AuditServiceImplTest` → FAIL (placeholder throws `UnsupportedOperationException`); GREEN: `./mvnw -B test` → exit 0 (33 run, 1 skipped: the audit placeholder check in `ContractStubsTest`)
- Contracts/Migrations added: none (`AuditService` impl over `V1`; `AuditServicePlaceholder` deleted)
- Decisions: none
- Open Questions: none

### Task 2 (US-AL-02)
- Commits: `test(audit): prove audit rows cannot change`
- Verification: RED: `./mvnw -B test -Dtest=AuditLogImmutabilityTest` with `@Immutable` removed → FAIL (`expected "Clear explanation" but was "Tampered"`); GREEN: `./mvnw -B test` with `@Immutable` → exit 0 (35 run, 1 skipped)
- Contracts/Migrations added: none
- Decisions: none
- Open Questions: none
