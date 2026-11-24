## ADDED Requirements

### Requirement: REQ-CD-01 Feature history
**THE SYSTEM SHALL** keep `docs/feature_history.md` with one entry per folder in `openspec/changes/archive/`, newest first. Each entry gives the date, the change name, what was built and why, PR/MR links, REQ IDs and decision IDs.

#### Scenario: Entry format
- **WHEN** a reader opens `docs/feature_history.md`
- **THEN** each archived change has a `## YYYY-MM-DD — <change-name>` heading followed by these fields.

### Requirement: REQ-CD-02 History completeness test
**IF** a folder in `openspec/changes/archive/` has no matching heading in `docs/feature_history.md`, **THEN** `node --test scripts/harness-scripts.test.mjs` **SHALL** fail and name the folder.

#### Scenario: Archive without history
- **WHEN** a change is archived but no history entry is added
- **THEN** the test fails with the archived folder name in its message.

### Requirement: REQ-CD-03 Feature backlog
**THE SYSTEM SHALL** keep `docs/feature_backlog.md` with one row per planned feature: status (`queued`, `in progress`, `blocked`), spec link, blocking O-decisions, dependencies, and blast radius (slice name from the `docs/architecture.md` Feature Slice Map, plus migrations).

#### Scenario: Picking parallel work
- **WHEN** two backlog rows have different slices and no dependency on each other
- **THEN** a reader can start both in parallel without touching the same package.

### Requirement: REQ-CD-04 Curated library notes
**THE SYSTEM SHALL** keep `docs/ai_docs/` with one note per pinned library. Each note gives the source URLs, the library version, the retrieval date, and at most 150 lines. `docs/ai_docs/README.md` indexes the notes.

#### Scenario: Stale note
- **WHEN** `pom.xml` or `package.json` changes a library's major or minor version
- **THEN** the matching note's version line no longer matches, and the README rule says to refresh the note in the same PR.

### Requirement: REQ-CD-05 Agents read library notes first
**WHEN** an agent uses a library API from the pinned stack, **THE SYSTEM SHALL** direct it (AGENTS.md dispatch table) to read the matching `docs/ai_docs/` note first.

#### Scenario: Spring AI work
- **WHEN** a task touches `ChatClient` code
- **THEN** the dispatch row points to `docs/ai_docs/spring-ai-2-kiconnect.md`.

### Requirement: REQ-CD-06 Simplified Technical English for new prose
**WHEN** a change adds or edits spec or doc prose, **THE SYSTEM SHALL** require ASD-STE100 style for that prose (short sentences, active voice, one instruction per sentence, approved terms), at reviewer level Convention Candidate.

#### Scenario: Long passive sentence
- **WHEN** a new spec sentence is passive and longer than 25 words
- **THEN** the reviewer reports a Convention Candidate, not a Blocking item.
