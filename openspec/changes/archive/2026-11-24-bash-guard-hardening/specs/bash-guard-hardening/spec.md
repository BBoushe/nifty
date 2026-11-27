## ADDED Requirements

### Requirement: REQ-BG-01 Wrapped commands are guarded
**WHEN** an agent Bash command wraps a guarded command in `env`, `command`, `nice`, `time`, `sudo`, `xargs`, `eval`, or an RTK subcommand (`git`, `gh`, `glab`, `err`, `test`, `summary`, `run`, `proxy`, `bypass`), **THE SYSTEM SHALL** evaluate the wrapped command exactly as if it were run directly.

#### Scenario: Wrapper hides a protected push
- **WHEN** the agent runs `nice -n 5 git push origin master`, `sudo -u x git push origin master`, `env -u VAR git push origin master` or `rtk err git push origin master` on branch `feat/x`
- **THEN** the guard blocks each command with the same reason as `git push origin master`.

#### Scenario: Wrapper runs a harmless command
- **WHEN** the agent runs `rtk git status`, `rtk err npm run build`, `rtk test ./mvnw -B test` or `time ./mvnw -B test`
- **THEN** the guard allows each command.

### Requirement: REQ-BG-02 Command strings are guarded
**WHEN** an agent Bash command passes a command string to `sh`, `bash` or `zsh` via `-c` (including combined flags such as `-lc`), to `rtk run` via `-c`/`--command`, or to `env -S`, **THE SYSTEM SHALL** evaluate that string with the same guard rules.

#### Scenario: Shell string reads .env
- **WHEN** the agent runs `sh -c "cat .env"` or `rtk run -c "cat .env"`
- **THEN** the guard blocks the command as a `.env` access.

#### Scenario: Shell string runs a harmless command
- **WHEN** the agent runs `sh -c "npm run lint"`
- **THEN** the guard allows the command.

### Requirement: REQ-BG-03 Recursion cap
**IF** nested command strings exceed a depth of 3, **THEN THE SYSTEM SHALL** block the command.

#### Scenario: Deep nesting
- **WHEN** the agent runs a command with four nested `sh -c` levels around `echo hi`
- **THEN** the guard blocks it with a reason that names the depth cap.

### Requirement: REQ-BG-04 No hook skipping on protected targets
**IF** an agent runs `git push` with `--no-verify` and any destination is `dev`, `alpha`, `master` or `main` on any remote, **THEN THE SYSTEM SHALL** block it.

#### Scenario: Hook skip to dev
- **WHEN** the agent runs `git push --no-verify origin dev` from `feat/x`
- **THEN** the guard blocks it.

#### Scenario: Hook skip to a feature branch and dry run
- **WHEN** the agent runs `git push --no-verify origin feat/x` or `git push -n origin dev`
- **THEN** the guard does not block for hook skipping (`-n` is `--dry-run`).

### Requirement: REQ-BG-05 Data is not a command
**WHILE** a command only carries text as data (for example a commit message), **THE SYSTEM SHALL** not treat that text as a command.

#### Scenario: Commit message mentions wrappers
- **WHEN** the agent runs `git commit -m "docs: mention sh -c and .env"` on `feat/x`
- **THEN** the guard allows it.
