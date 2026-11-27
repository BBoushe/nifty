# Spec Delta: Backend Foundation (`backend-foundation`)

## ADDED Requirements

### Requirement: REQ-BF-01 Spring Tests on PostgreSQL
**THE SYSTEM SHALL** run every Spring test (`@SpringBootTest`, `@DataJpaTest`) against a PostgreSQL Testcontainers database through `de.fhdortmund.nifty.PostgresContainerBase`, and **SHALL NOT** use H2 in tests.

#### Scenario: Flyway V1 on PostgreSQL
- **WHEN** a `@DataJpaTest` subclass of `PostgresContainerBase` starts
- **THEN** Flyway applies `V1` on PostgreSQL and the `audit_log` table exists.

### Requirement: REQ-BF-02 Multi-Field Validation Errors
**WHEN** a service throws `FieldValidationException` **THE SYSTEM SHALL** answer `400` `application/problem+json` with `errors: [{ field, message, code }]` and no stack trace or exception class name.

#### Scenario: Two invalid fields
- **WHEN** a controller throws `FieldValidationException` with errors for `minTimeSec` and `followUpLimit`
- **THEN** the response is `400` with 2 entries in `errors`, and it contains no `trace` or `exception` key.

### Requirement: REQ-BF-03 Contract Stubs with Placeholder Beans
**THE SYSTEM SHALL** provide every interface, record and enum of `docs/tickets/00-parallel-waves-and-contracts.md` §3 in its owner package, with one placeholder `@Service` per interface that throws `UnsupportedOperationException`.

#### Scenario: Context starts with placeholders
- **WHEN** a `@SpringBootTest` starts
- **THEN** the context holds one bean for each of the 6 shared interfaces, and calling a placeholder method throws `UnsupportedOperationException`.

#### Scenario: Enum literals
- **WHEN** Jackson writes `ScoreStatus.NOT_ASSESSED` and `EntryOrigin.FOLLOW_UP`
- **THEN** the JSON values are `"N/A"` and `"follow_up"`.
