# Spec Delta: Auth & Access (`invite-links`)

## ADDED Requirements

### Requirement: REQ-AA-08 Revocation Ends Candidate Sessions
**WHEN** the professor revokes an invite link **THE SYSTEM SHALL** reject the next request of every session started with that link with `401` and end those sessions (Decision 1.23).

#### Scenario: Revoked during an interview
- **WHEN** the professor revokes the invite of a candidate who has an open session
- **THEN** the candidate's next request answers `401` and the session ends.

### Requirement: REQ-AA-09 Invites Only for Own Assessments
**IF** a professor creates, lists or revokes invites for an assessment that does not exist or that another professor owns **THEN THE SYSTEM SHALL** answer `404` and change nothing.

#### Scenario: Other professor's assessment
- **WHEN** professor A posts invites for professor B's assessment
- **THEN** the system answers `404` and creates no invite.
