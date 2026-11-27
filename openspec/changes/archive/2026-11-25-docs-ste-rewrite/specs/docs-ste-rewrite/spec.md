## ADDED Requirements

### Requirement: REQ-SR-01 No semantic change
**WHEN** a file is rewritten, **THE SYSTEM SHALL** keep every obligation keyword (`MUST`, `MUST NOT`, `SHALL`, `NEVER`, `SHOULD`), every REQ ID, every number, every path and every command from the original.

#### Scenario: Obligation count check
- **WHEN** the obligation check (design §2) runs on the file before and after the rewrite
- **THEN** both outputs are identical.

#### Scenario: Reviewer meaning check
- **WHEN** the cold reviewer compares the old and new text of a rule
- **THEN** a changed meaning is reported as Blocking.

### Requirement: REQ-SR-02 STE compliance
**WHEN** a file is rewritten, **THE SYSTEM SHALL** pass the `asd-ste100` linter for that file, or list each accepted exception in the Handoff Log with its reason.

#### Scenario: Linter run
- **WHEN** the linter runs on a rewritten file
- **THEN** it reports no errors, or every remaining finding appears in the Handoff Log as an accepted exception.

### Requirement: REQ-SR-03 Specs stay valid
**WHEN** an `openspec/specs/*/spec.md` file is rewritten, **THE SYSTEM SHALL** keep `OPENSPEC_TELEMETRY=0 openspec validate --all` passing.

#### Scenario: Rewritten domain spec
- **WHEN** `openspec/specs/evidence-pipeline/spec.md` is rewritten
- **THEN** `openspec validate --all` exits 0.
