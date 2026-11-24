## ADDED Requirements

### Requirement: REQ-SL-01 Project declares Superpowers
**THE SYSTEM SHALL** declare the Superpowers plugin in `.claude/settings.json`, so Claude Code offers it to every teammate who trusts the project.

#### Scenario: Fresh clone
- **WHEN** a teammate opens a fresh clone in Claude Code
- **THEN** the project settings list the Superpowers plugin as enabled.

### Requirement: REQ-SL-02 RED before GREEN evidence
**WHEN** a builder completes a code task, **THE SYSTEM SHALL** require the Handoff Log Verification line to show the RED command with its failure and then the GREEN command with exit 0. The cold reviewer **SHALL** report missing RED evidence as Blocking.

#### Scenario: Missing RED
- **WHEN** a code task's Handoff entry shows only a passing test run
- **THEN** the reviewer verdict lists it under Blocking.

### Requirement: REQ-SL-03 Systematic debugging on failure
**WHEN** a build or test fails during a task, **THE SYSTEM SHALL** direct the builder to investigate, state a hypothesis, write a reproducing test, and then fix the root cause.

#### Scenario: Failing build
- **WHEN** `./mvnw -B test` fails during a task
- **THEN** the role prompt tells the builder to use systematic debugging before it edits production code.

### Requirement: REQ-SL-04 Archive gate
**IF** a change folder outside `openspec/changes/archive/` has at least one task and no unchecked task above its `## Delivery` heading, **THEN THE SYSTEM SHALL** report that change: the Claude `Stop` hook blocks with a reason, `.githooks/pre-push` blocks pushes of non-protected branches, and the repo-invariant test fails in CI.

#### Scenario: Forgotten archive
- **WHEN** every task of `openspec/changes/foo/tasks.md` above `## Delivery` is checked and the folder is not archived
- **THEN** the Stop hook output is `{"decision":"block"}` with a reason that names `foo`.

#### Scenario: Delivery steps open
- **WHEN** only checkboxes below `## Delivery` are unchecked
- **THEN** the change still counts as complete for the gate.

#### Scenario: No loop
- **WHEN** the Stop hook input has `stop_hook_active: true`
- **THEN** the hook allows the stop.

#### Scenario: Mirror sync
- **WHEN** `node scripts/forge.mjs sync dev` pushes to `dev`
- **THEN** pre-push does not run the archive gate.

### Requirement: REQ-SL-05 Resumable tasks
**THE SYSTEM SHALL** require every `tasks.md` to have a `## Status` block that is overwritten at every stop, tasks with nested step checkboxes, a `## Delivery` checklist, and a `## Handoff Log`. The builder **SHALL** commit and push the feature branch after every task.

#### Scenario: Usage limit mid-task
- **WHEN** a session stops in the middle of a task
- **THEN** the Status block names the branch, worktree, current step and next action, so another tool resumes from the repo alone.

### Requirement: REQ-SL-06 Cross-tool parity
**WHERE** a tool cannot load Superpowers skills (Antigravity, Codex, Kiro), **THE SYSTEM SHALL** give the same loop steps inline in the role prompts.

#### Scenario: Codex builder
- **WHEN** Codex follows `.claude/agents/spec-builder.md`
- **THEN** the prompt lists the RED, GREEN, debug and verify steps without needing a skill call.
