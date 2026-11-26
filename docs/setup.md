# AI Harness Setup Guide (SDD & ADD)

This document explains **only** our **Spec-Driven Development (SDD)** and **Agent-Driven Development (ADD)** setup. It shows how specifications, multi-agent roles, rules, skills, and automated safety hooks fit together. Other documents describe the application tech stack. This harness is stack-agnostic by design.

---

## For Teammates

### What This Setup Actually Is

Every rule, feature spec, agent role, and safety hook is a **plain text file in Git**. There is no proprietary cloud workspace and zero lock-in. You can use **Claude Code**, **Antigravity (`agy`)**, **OpenAI Codex**, **Kiro Crew**, **T3 Code**, or a plain IDE. Every tool reads the same files and obeys the same safety checks.

### Why We Built It This Way

- **Specs before code (`openspec/`):** Fast vibe-coding creates silent bugs and misaligned assumptions across a team. First write short, testable acceptance criteria (in EARS format: `WHEN … THE SYSTEM SHALL …`). They give humans and AI agents one shared contract.
- **One plan folder, not two:** `openspec/changes/<feature>/` already holds the proposal, spec, design, and task checklist. `tasks.md` also has a 5-line `## Handoff Log` at the bottom. We intentionally have **no** separate `plans/` folder, so checklists never drift out of sync.
- **One rule file, thin pointers everywhere else:** Engineering rules are in `AGENTS.md` and `.agents/rules/`. Tool entrypoints (`CLAUDE.md`, `GEMINI.md`, `.kiro/steering/`) are short pointers. They are never copy-pasted duplicates.
- **Traceable decisions (`docs/decisions/`):** `node scripts/record-decision.mjs` records every answered `question-round` and every prompter direction change with a stable `X.Y` ID. Builders leave `// Decision X.Y` pointers in code. So anyone can trace *why* something works the way it does.
- **One living coding-rules file:** Team coding standards are in one canonical file (`.agents/rules/coding-standards.md`). The tools load it automatically. Our sandboxed cold **Reviewer** checks against it.
- **Parallel GitHub / GitLab switch:** Set `GIT_FORGE=github`, `gitlab`, or `both` in `.env`. Then run `node scripts/check-setup.mjs`. Agents read only the non-secret forge setting and never see your API keys.
- **Bootstrap professor account (Decision 1.24):** To sign in locally without a sign-up page, set `NIFTY_BOOTSTRAP_PROFESSOR_EMAIL` and `NIFTY_BOOTSTRAP_PROFESSOR_PASSWORD` in `.env` (and `NIFTY_SESSION_COOKIE_SECURE=false` over local HTTP). The backend creates one `PROFESSOR` account on startup if that email does not exist, stores only a hash, and never logs the password. Agents never read `.env`.
- **Cold, separate-session review:** The agent that wrote the code should never review its own work. A fresh, read-only **Reviewer** session inspects only the Git diff, specs, coding standards, and decisions. It separates hard **Blocking** defects from non-blocking **Convention Candidates**.
- **Portable hooks for everyone:** Node `.mjs` hooks with zero dependencies (`node --test` tests them) and `.githooks/` block accidental `.env` secret leaks, bad commit messages, and direct pushes to protected branches. They work the same on **macOS, Windows, and Linux**.
- **Optional RTK token savings (Claude Code, per developer):** [RTK](https://github.com/rtk-ai/rtk) compresses noisy shell output (`git`, `gh`, `npm run ...`) before it reaches the model. This cuts context cost. It lives entirely in *your* `~/.claude/settings.json` and RTK config, never in the repo. RTK auto-allows the commands it rewrites. So our guard is the only check before execution. It sees through wrappers, shell grouping and `-c` strings up to depth 3 (exact list and limits in Guard Layers). Prefer absolute paths over `cd <dir> && …`, because the RTK rewrite may drop the `cd`. Setup steps and the optional `./mvnw` safeguard are in the root [`README.md`](../README.md#optional-rtk-token-savings-for-claude-code).

### Guard Layers

Three layers protect secrets and protected branches. Each layer catches what the one before cannot:

1. **Claude PreToolUse guard** (`.claude/hooks/guard-*.mjs`): It covers agents only and runs before the command. It sees through wrappers (`rtk`, `env`, `command`, `nice`, `time`, `sudo`, `xargs`, `exec`, `timeout`, `nohup`, `eval`), `( … )`, `{ …; }`, shell keywords, `$( … )` and backticks. It also sees through `sh|bash|zsh|dash|ksh -c`, `rtk run -c` and `env -S` strings in every flag form, up to depth 3. It blocks deeper nesting and shells that read commands from stdin. It understands git's abbreviated `--no-verify`/`--force*` flags, `+` refspecs, `HEAD`/`@` destinations and `-c core.hooksPath=…`. A heredoc body counts as text only in the message forms `git|gh|glab … -m "$(cat <<'EOF' …)"` and `git commit -F - <<'EOF'`. A line that names the secret env file still blocks. The guard checks every other heredoc line as a command. It does **not** inspect interpreter one-liners (`python -c`, `node -e`), `find -exec`, scripts run by path, or other shell syntax. Other shell syntax includes `case`, functions, `<( … )`, brace expansion, `$'…'` quoting, `GIT_CONFIG_*` variables, and command names built by a substitution such as `$(echo git) push`. So layers 2 and 3 stay necessary. RTK auto-allows what it rewrites, so this is the only check on agent intent.
2. **Git hooks** (`.githooks/`): They cover every client and human, at commit and push time. You can skip them with `--no-verify`. So the agent guard blocks `git push --no-verify` to `dev`, `alpha`, `master` and `main`.
3. **Server rules** (GitHub rulesets in `.github/rulesets/`, GitLab protected branches): A client cannot skip them.

### The 6-Step Daily Loop

1. **Once per clone:** Enable shared Git hooks and check your environment (including active `gh` or `glab` forge):
   ```sh
   git config core.hooksPath .githooks
   node scripts/check-setup.mjs
   ```
2. **Branch cleanly:** Branch off `dev` (`feat/<slug>`, `fix/<slug>`, `chore/<slug>`).
3. **Check or create the spec:** Read (or draft) `openspec/changes/<change>/` (`proposal.md`, `specs/`, `design.md`, `tasks.md`). If an unresolved team decision blocks you, use `question-round`. It records the answer in `docs/decisions/`.
4. **Build incrementally:** Implement unchecked tasks in `tasks.md` with `spec-builder`. Follow `.agents/rules/coding-standards.md`. Leave `// Decision X.Y` pointers. Record any prompter direction shifts with `scripts/record-decision.mjs`. Stage (`git add`). Commit separately with Conventional Commits. Add a 5-line entry to `## Handoff Log`.
5. **Run a cold review:** Start a fresh read-only `reviewer` session with *only* `git diff dev...HEAD`, `openspec/`, `.agents/rules/coding-standards.md`, and `docs/decisions/index.md`. Fix any `Blocking` findings.
6. **Open a short PR / MR into `dev`:** Use `pr-memo` (it adapts to `gh`, `glab`, or both with `scripts/check-setup.mjs --forge` and `scripts/forge.mjs`) to produce a <=10-line plain-language summary.

---

## For Agents

This section is the **canonical source of truth** for any AI agent that must understand, adjust, audit, or replicate this SDD/ADD harness in another repository. Stack-specific rules and test commands are pluggable slots. Keep the structural invariants below intact.

### 1. Canonical Inventory & Ownership Table

| Path | Purpose | Read By | Authority |
|---|---|---|---|
| `openspec/config.yaml` | Schema (`spec-driven`), project context, non-negotiable invariants, EARS & Handoff Log rules | OpenSpec CLI, all agents | **Canonical** |
| `openspec/specs/<module>/spec.md` | Long-lived domain requirements in EARS notation + scenarios | All agents & humans | **Canonical** |
| `openspec/changes/<change>/` | Active execution plans (`proposal.md`, `specs/`, `design.md`, `tasks.md` + `## Handoff Log`, `.openspec.yaml`) | All agents & humans | **Canonical** |
| `AGENTS.md` | Universal engineering rules, domain invariants, Scoped Rules & Templates Dispatch table, Git policy, role parity table, cross-tool hook parity table | All agents (universal standard) | **Canonical** |
| `.agents/rules/*.md` | Scoped progressive rules (`always_on`, `glob`, `model_decision`), including canonical `coding-standards.md`, `spec-driven.md`, and `git-workflow.md` | Antigravity (`agy`), `@`-imported by `CLAUDE.md`, dispatched by `AGENTS.md` | **Canonical** |
| `.github/PULL_REQUEST_TEMPLATE.md` & `.gitlab/merge_request_templates/Default.md` | Identical unified PR/MR template (`What changed?`, `Why?`, `Spec / Issue Reference`, `Verification` checklist) filled by `pr-memo` | GitHub, GitLab, all agents (`pr-memo`) | **Canonical (PR/MR Template)** |
| `.agents/skills/*/SKILL.md` | Shared tool-agnostic workflow skills (OpenSpec skills + `record-decision`, `question-round`, `pr-memo`) | Antigravity (`agy`), Codex, symlinked to Claude Code | **Canonical** |
| `docs/decisions/` | Hierarchical decision registry (`index.md`, `1-architecture.md`, `2-design.md`, `3-team-process.md`, `4-direction.md`) with `X.Y` code pointers | All agents & humans | **Canonical (Decisions)** |
| `docs/api.md` | Living API contract summary (pluggable contract slot synced alongside runtime OpenAPI) | Builders, reviewers, humans | **Canonical (Contract Slot)** |
| `scripts/record-decision.mjs` & `scripts/check-setup.mjs` | Zero-dependency Node helpers: deterministic `X.Y` decision recorder + secret-safe forge reader (`GIT_FORGE`) & setup preflight (`scripts/harness-scripts.test.mjs`) | All agents, humans, CI | **Canonical (Automation)** |
| `.claude/agents/*.md` | Role prompts & tool-boundary hooks (`spec-builder`, cold read-only `reviewer`, `tests-only` `test-writer`) | Claude Code (mirrored by `AGENTS.md` for other tools) | **Canonical (Roles)** |
| `.claude/hooks/*.mjs` | Zero-dependency cross-platform safety guards (`lib`, `guard-files`, `guard-bash`, `guard-agent`, `test-gate`) + `hooks.test.mjs` | Claude Code `PreToolUse`, `.githooks/`, CI | **Canonical (Enforcement)** |
| `.claude/settings.json` | Claude Code `permissions.allow` / `permissions.deny` and `PreToolUse` hook wiring | Claude Code | **Adapter** |
| `~/.claude/settings.json` + RTK `config.toml` *(outside the repo)* | Optional per-developer RTK hook (`rtk hook claude`, installed with `rtk init -g --hook-only`) rewriting Bash commands to `rtk ...`, and `[hooks] exclude_commands` keeping `./mvnw`/`mvnw`/`mvnw.cmd` unrewritten | Claude Code (the developer's own machine) | **Optional, non-canonical (never committed)** |
| `.claude/skills/` | Symlinks to `.agents/skills/*` plus Claude-oriented verification skills (`spec-implementer`, stack checks) | Claude Code | **Adapter / Symlinks** |
| `CLAUDE.md` | Claude Code entrypoint (`@` imports of canonical `coding-standards.md`, `spec-driven.md`, `git-workflow.md`) + forge-aware First-Run Bootstrap | Claude Code, T3 Code | **Thin Pointer** |
| `GEMINI.md` | Antigravity / Gemini CLI verifier persona & quota-conscious habits | Antigravity (`agy`), Gemini CLI | **Thin Pointer** |
| `.kiro/steering/*.md` | `inclusion: always` steering bridge (<=25 lines) directing Kiro IDE / Kiro Crew to `AGENTS.md`, `openspec/`, `.githooks/` | Kiro IDE, Kiro Crew | **Thin Pointer** |
| `.githooks/` | Portable Git hooks (`pre-commit` -> `guard-bash.mjs --pre-commit`, `commit-msg`, `pre-push` -> branch guard + `test-gate.mjs`) | Git (every CLI, IDE, wrapper, and human) | **Adapter (Universal Enforcement)** |
| `.github/workflows/ci.yml` & `.gitlab-ci.yml` | Parallel GitHub Actions & GitLab CI pipelines running hook/script tests, commit lint, branch policy, and pluggable build jobs | GitHub Actions / GitLab CI | **Enforcement (Remote)** |
| `.github/rulesets/*.json` | Declarative branch protection rulesets synced to GitHub via `scripts/` | GitHub Repository Governance | **Enforcement (Remote)** |
| `docs/feature_history.md` | One entry per archived OpenSpec change: what, why, PR/MR, REQ and decision IDs. `scripts/harness-scripts.test.mjs` fails if an entry is missing. | All agents & humans | **Canonical (History)** |
| `docs/feature_backlog.md` | Planned features in pick order, with status, blockers, dependencies and blast radius (slice + migrations) | All agents & humans | **Canonical (Backlog)** |
| `docs/ai_docs/` | Current API notes for pinned libraries that are newer than model training data. One file per library, each ≤150 lines. | Builders (dispatched by `AGENTS.md` §3) | **Canonical (Library Notes)** |
| `.agents/skills/asd-ste100/` | Vetted ASD-STE100 rewrite skill and linter (`python3 .agents/skills/asd-ste100/scripts/ste-lint.py <file>`), symlinked to `.claude/skills/asd-ste100` | All agents (`spec-driven.md` rule 6) | **Canonical (Writing Style)** |
| `.claude/hooks/archive-gate.mjs` | Archive gate (Decision 3.8): lists changes whose tasks above `## Delivery` are all `[x]` but still sit outside `openspec/changes/archive/`. Modes: `--stop-hook` and `--pre-push`. A repo-invariant test is in `hooks.test.mjs` | Claude Code `Stop` hook, `.githooks/pre-push`, CI | **Canonical (Enforcement)** |
| `.claude/settings.json` `hooks.Stop` | Runs `node .claude/hooks/archive-gate.mjs --stop-hook`. It blocks the stop with a reason that names each unarchived completed change. It allows the stop when `stop_hook_active` is set | Claude Code | **Adapter** |
| `.claude/settings.json` `enabledPlugins` | Declares `superpowers@claude-plugins-official` (Decision 3.7) and `ponytail@ponytail` with its marketplace `DietrichGebert/ponytail` (Decision 3.15). Declaring a plugin does not install it. Each teammate runs `claude plugin install superpowers@claude-plugins-official --scope project` and `claude plugin install ponytail@ponytail --scope project` once per clone. Builders install ponytail themselves when it is missing. Other tools use the inline Build Loop in `.claude/agents/spec-builder.md` | Claude Code | **Adapter** |
| `.agents/skills/orchestrate-changes/` | Orchestrator procedure (Decision 3.11): builder agents per change in `../nifty-<change>` worktrees, a cold reviewer per PR, PR/MR handling by branch, merge order, and a user question with an architecture summary before every merge. Symlinked to `.claude/skills/orchestrate-changes` | Claude Code (other tools follow it by hand) | **Canonical (Delivery)** |
| `scripts/forge.mjs prs` | Prints each open branch's GitHub PR and checks next to its GitLab MR and pipeline. `forge.mjs` also reads the main checkout's `.env` from a worktree | All tools | **Canonical (Forge)** |
| `docs/ai_logs/` | Prompt-as-code audit trail (Decision 3.12): one dated file per architecture handover or decision session. Format and index in `docs/ai_logs/README.md` | All agents and humans | **Canonical (Process)** |

### 2. Modification Rules (Zero-Drift Policy)

1. **Adding or changing a project invariant or coding rule:** Edit `AGENTS.md`, `openspec/config.yaml`, or `.agents/rules/coding-standards.md` (with an explicit `Blocking` vs. `Convention Candidate` reviewer level). **Never** copy rule prose into `CLAUDE.md` (use `@` import instead), `GEMINI.md`, `.kiro/steering/`, or a separate `.claude/rules/` folder. Whenever you add a new cross-tool rule or template to `.agents/rules/`, register it in `AGENTS.md`'s Scoped Rules & Templates Dispatch table. If it is always/workflow-critical, also `@`-import it in `CLAUDE.md`. Then fresh sessions outside Antigravity load it automatically (Decision 3.4).
2. **Recording a decision (mandatory on `question-round` & prompter changes):** Run `node scripts/record-decision.mjs --category 1|2|3|4 ...`. This keeps `X.Y` IDs and `docs/decisions/index.md` deterministic. Then add `// Decision X.Y` pointers in affected files.
3. **Adding a new workflow skill:** Create `.agents/skills/<name>/SKILL.md` first, then create a relative symlink `.claude/skills/<name> -> ../../.agents/skills/<name>`.
4. **Adding or changing a hook or helper script:** Implement shared logic in `.claude/hooks/lib.mjs` or `scripts/*.mjs`. Add `node:test` coverage in `.claude/hooks/hooks.test.mjs` or `scripts/harness-scripts.test.mjs`. Check that the tests pass. Never weaken `.env` secret isolation. Agents always use `node scripts/check-setup.mjs --forge` and never read `.env`.
   - **RTK invariant:** RTK's hook rewrites `git ...` to `rtk git ...`. So `stripLeadingEnvAssignments` in `lib.mjs` also strips a leading `rtk` (path-qualified, with flags, and the `proxy`/`bypass`/`err`/`test`/`summary`/`run` subcommands). It also strips the `env`, `command`, `nice`, `time`, `sudo`, `xargs`, `exec`, `timeout` and `nohup` wrappers and shell group/keyword prefixes. `guard-bash.mjs` re-evaluates `sh|bash|zsh|dash|ksh -c`, `rtk run -c`, `env -S`, `eval` and `$( … )` strings up to depth 3. `guard-bash.mjs` blocks deeper nesting. `commandStringFlag` (`lib.mjs`) defines the command-string flags once. Every guard must keep checking the *unwrapped* command. `hooks.test.mjs` covers this ("sees through the rtk wrapper", "blocks guarded commands hidden behind wrappers and command strings"). RTK auto-allows the commands it rewrites, so this guard is the only pre-execution check. Never add a guard that matches on a raw command string without going through that helper. Never weaken that helper. `./mvnw` stays in RTK's `exclude_commands` as a safeguard. RTK 1.2.1 rewrites bare `mvn` but not `./mvnw`.
5. **Adding a new AI tool or orchestrator:** Add a **thin pointer file** (<=25 lines) in that tool's native config directory. It points to `AGENTS.md`, `openspec/`, and `.githooks/`. Never fork specs into a second spec folder (for example, do not use `.kiro/specs/` or `.claude/plans/`).
6. **Changing the app tech stack (pluggable slots):** Swap only stack-specific `.agents/rules/<stack>.md` files, stack check skills, `docs/api.md` contract content, `test-gate.mjs` path commands, and CI build jobs. Do not change OpenSpec, decision registry, coding-rule structure, roles, forge switch, and secret/branch hooks.

### 3. Replication Checklist (Bootstrapping a New Repository)

Follow these steps in order to replicate this SDD/ADD harness into any greenfield or existing repo:

1. **Initialize Spec Layer:**
   - Install or invoke OpenSpec (`OPENSPEC_TELEMETRY=0 openspec init --scope project --tools claude,antigravity,codex,gemini --no-animation`).
   - Populate `openspec/config.yaml` with `schema: spec-driven`, domain invariants, EARS rules, and the `## Handoff Log` convention.
   - Seed `openspec/specs/<module>/spec.md` with baseline EARS requirements and create an initial `openspec/changes/project-setup/` change.
2. **Create Universal Rules & Thin Tool Bridges:**
   - Write canonical `AGENTS.md` (invariants, workflow, role parity table, cross-tool hook parity table).
   - Add flat `.agents/rules/*.md` files with valid frontmatter (`always_on`, `glob`, `model_decision`) for spec workflow, Git hygiene, and pluggable tech-stack slots.
   - Add thin pointers: `CLAUDE.md` (with First-Run Bootstrap), `GEMINI.md`, and `.kiro/steering/<project>-core.md` (`inclusion: always`, <=25 lines).
3. **Install Tested Cross-Platform Hooks & Permissions:**
   - Copy `.claude/hooks/{lib,guard-files,guard-bash,guard-agent,test-gate,hooks.test}.mjs` and adapt `test-gate.mjs` path-to-compile mappings plus project-specific secret patterns in `lib.mjs`.
   - Configure `.claude/settings.json` (`permissions.allow`, `permissions.deny`, and `PreToolUse` `.mjs` hooks).
   - Copy `.githooks/{pre-commit,commit-msg,pre-push}`, mark executable (`chmod +x`), and document `git config core.hooksPath .githooks`.
4. **Add Role Separation & Shared Skills:**
   - Copy `.claude/agents/{spec-builder,reviewer,test-writer}.md` and adapt test-path globs in `guard-agent.mjs` to the target repo's test directories.
   - Copy `.agents/skills/{record-decision,question-round,pr-memo}/SKILL.md` and symlink them into `.claude/skills/`.
5. **Wire CI, Decision & Preflight Scripts, & Governance:**
   - Copy `scripts/{record-decision,check-setup,harness-scripts.test}.mjs` and `docs/decisions/` templates, plus `.agents/rules/coding-standards.md` and `docs/api.md`.
   - Add `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs`, Conventional Commits validation, and branch promotion checks to `.github/workflows/ci.yml` and parallel `.gitlab-ci.yml`.
   - Add declarative `.github/rulesets/*.json` matching the team's branch promotion flow.
   - *(Optional, per developer)* If teammates use RTK, keep the `rtk` unwrapping in `lib.mjs` and its test. Install with `rtk init -g --hook-only`. Exclude any version-pinning wrapper (`./mvnw`) with `[hooks] exclude_commands` in RTK's `config.toml`. Do not commit RTK output or RTK's `~/.claude` files.
6. **Verify End-to-End:**
   - Run `OPENSPEC_TELEMETRY=0 openspec validate --all` -> exit `0`.
   - Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` -> exit `0`.
   - Run `node scripts/check-setup.mjs` -> 0 failures. Check `.claude/settings.json` JSON and `.gitlab-ci.yml` YAML. Execute a cold `reviewer` pass to check for zero rule drift across all canonical and pointer files.
