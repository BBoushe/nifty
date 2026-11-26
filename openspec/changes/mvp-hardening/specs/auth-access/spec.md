## ADDED Requirements

### Requirement: REQ-AA-10 New Link After a Revoke
**WHEN** the professor creates an invite for a candidate with a revoked invite in that assessment **THE SYSTEM SHALL** issue a new token for the same invite (Decision 2.34). The old token and its sessions stay invalid. **THE SYSTEM SHALL** write an `INVITE_REISSUED` audit log entry. The candidate's attempts and results stay linked to the invite.

#### Scenario: Leaked link replaced
- **WHEN** the professor revokes the invite of `anna.schmidt` and then creates an invite for `anna.schmidt` again
- **THEN** the new link signs in, the old link answers `401`, and the attempt count of `anna.schmidt` stays the same.

### Requirement: REQ-AA-11 Concurrent Invite Creation
**WHEN** two requests create the same invite at the same time **THE SYSTEM SHALL** create one invite and answer the other request with `409`. **THE SYSTEM SHALL NOT** answer `500`.

#### Scenario: Double click on Add
- **WHEN** two add requests for `anna.schmidt` arrive within 10 ms
- **THEN** one invite exists, one response is `201`, and the other is `409`.

### Requirement: REQ-AA-12 CSV Quoted Fields
**WHEN** the professor imports a CSV **THE SYSTEM SHALL** read quoted fields per RFC 4180, including commas, doubled quotes and line breaks inside quotes.

#### Scenario: Line break inside quotes
- **WHEN** a CSV row has the candidate field `"Schmidt,\nAnna"`
- **THEN** the import creates one invite for that row and no extra row.

### Requirement: REQ-AA-13 CSRF Token Renewal on Sign-In
**WHEN** a professor or candidate signs in **THE SYSTEM SHALL** issue a new CSRF token and reject the token from before the sign-in with `403`.

#### Scenario: Old token after login
- **WHEN** a client signs in and then sends a POST with the CSRF token it held before the sign-in
- **THEN** the system answers `403`.

### Requirement: REQ-AA-14 Sign-In Rate Limit
**IF** professor sign-in fails 5 times for one email within 15 minutes **THEN THE SYSTEM SHALL** answer `429` ProblemDetail with `Retry-After`. A successful sign-in resets the count.

#### Scenario: Sixth wrong password
- **WHEN** a client sends a sixth wrong password for `prof@example.org` within 15 minutes
- **THEN** the system answers `429` with a `Retry-After` header, also for a correct password.

### Requirement: REQ-AA-15 Bootstrap Password Length
**IF** the bootstrap professor password is longer than 72 bytes in UTF-8 **THEN THE SYSTEM SHALL** stop at startup with an error that names the limit and not the password.

#### Scenario: 80-byte password
- **WHEN** the backend starts with an 80-byte bootstrap password
- **THEN** startup fails, and the log names the 72-byte limit without the password.
