# AI Harness Setup Guide (SDD & ADD)

This document explains **only** our **Spec-Driven Development (SDD)** and **Agent-Driven Development (ADD)** setup — how specifications, multi-agent roles, rules, skills, and automated safety hooks fit together. Application tech-stack details live elsewhere; this harness is stack-agnostic by design.

---

## For Teammates

### What This Setup Actually Is

Every rule, feature spec, agent role, and safety hook lives as **plain text files in Git**. There is no proprietary cloud workspace and zero lock-in: whether you use **Claude Code**, **Antigravity (`agy`)**, **OpenAI Codex**, **Kiro Crew**, **T3 Code**, or a plain IDE, every tool reads the same brain and obeys the same safety checks.

### Why We Built It This Way

- **Specs before code (`openspec/`):** Vibe-coding fast creates silent bugs and misaligned assumptions across a team. Writing short, testable acceptance criteria first (in EARS format: `WHEN … THE SYSTEM SHALL …`) gives humans and AI agents one shared contract.
- **One plan folder, not two:** `openspec/changes/<feature>/` already holds the proposal, spec, design, and task checklist (plus a 5-line `## Handoff Log` at the bottom of `tasks.md`). We intentionally have **no** separate `plans/` folder so checklists never drift out of sync.
- **One rule file, thin pointers everywhere else:** Engineering rules live in `AGENTS.md` and `.agents/rules/`. Tool entrypoints (`CLAUDE.md`, `GEMINI.md`, `.kiro/steering/`) are short pointers — never copy-pasted duplicates.
- **Traceable decisions (`docs/decisions/`):** Every answered `question-round` and every prompter direction change is recorded with a stable `X.Y` ID via `node scripts/record-decision.mjs`, and builders leave `// Decision X.Y` pointers in code so anyone can trace *why* something works the way it does.
- **One living coding-rules file:** Team coding standards live in one canonical file (`.agents/rules/coding-standards.md`), automatically loaded across tools and checked by our sandboxed cold **Reviewer**.
- **Parallel GitHub / GitLab switch:** Set `GIT_FORGE=github`, `gitlab`, or `both` in `.env` and run `node scripts/check-setup.mjs` — agents read only the non-secret forge setting and never see your API keys.
- **Cold, separate-session review:** The agent that wrote the code should never grade its own homework. A fresh, read-only **Reviewer** session inspects only the Git diff, specs, coding standards, and decisions, separating hard **Blocking** defects from non-blocking **Convention Candidates**.
- **Portable hooks for everyone:** Zero-dependency Node `.mjs` hooks (tested by `node --test`) + `.githooks/` block accidental `.env` secret leaks, bad commit messages, and direct pushes to protected branches identically on **macOS, Windows, and Linux**.
- **Optional RTK token savings (Claude Code, per developer):** [RTK](https://github.com/rtk-ai/rtk) compresses noisy shell output (`git`, `gh`, `npm run ...`) before it reaches the model, cutting context cost. It lives entirely in *your* `~/.claude/settings.json` and RTK config, never in the repo. RTK auto-allows the commands it rewrites, so our guard is the only check before execution; it sees through wrappers and `-c` strings up to depth 3. Prefer absolute paths over `cd <dir> && …`, because the RTK rewrite may drop the `cd`. Setup steps and the optional `./mvnw` safeguard are in the root [`README.md`](../README.md#optional-rtk-token-savings-for-claude-code).

### Guard Layers

Three layers protect secrets and protected branches; each catches what the one before cannot:

1. **Claude PreToolUse guard** (`.claude/hooks/guard-*.mjs`): agents only, before the command runs. It sees through wrappers (`rtk`, `env`, `nice`, `time`, `sudo`, `xargs`, `eval`) and `-c` strings up to depth 3, and blocks deeper nesting. RTK auto-allows what it rewrites, so this is the only check on agent intent.
2. **Git hooks** (`.githooks/`): every client and human, at commit and push time. They can be skipped with `--no-verify`, so the agent guard blocks `git push --no-verify` to `dev`, `alpha`, `master` and `main`.
3. **Server rules** (GitHub rulesets in `.github/rulesets/`, GitLab protected branches): cannot be skipped from a client.

### The 6-Step Daily Loop

1. **Once per clone:** Enable shared Git hooks and verify your environment (including active `gh` or `glab` forge):
   ```sh
   git config core.hooksPath .githooks
   node scripts/check-setup.mjs
   ```
2. **Branch cleanly:** Branch off `dev` (`feat/<slug>`, `fix/<slug>`, `chore/<slug>`).
3. **Check or create the spec:** Read (or draft) `openspec/changes/<change>/` (`proposal.md`, `specs/`, `design.md`, `tasks.md`). If blocked by an unresolved team decision, use `question-round` (which records the answer in `docs/decisions/`).
4. **Build incrementally:** Implement unchecked tasks in `tasks.md` using `spec-builder`, follow `.agents/rules/coding-standards.md`, leave `// Decision X.Y` pointers, record any prompter direction shifts via `scripts/record-decision.mjs`, stage (`git add`), commit separately with Conventional Commits, and add a 5-line entry to `## Handoff Log`.
5. **Run a cold review:** Start a fresh read-only `reviewer` session with *only* `git diff dev...HEAD`, `openspec/`, `.agents/rules/coding-standards.md`, and `docs/decisions/index.md`. Fix any `Blocking` findings.
6. **Open a short PR / MR into `dev`:** Use `pr-memo` (auto-adapts to `gh`, `glab`, or both via `scripts/check-setup.mjs --forge` and `scripts/forge.mjs`) to produce a <=10-line plain-language summary.

---

## For Agents

This section is the **canonical source of truth** for any AI agent tasked with understanding, adjusting, auditing, or replicating this SDD/ADD harness in another repository. Stack-specific rules and test commands are pluggable slots — keep the structural invariants below intact.

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
| `.claude/hooks/archive-gate.mjs` | Archive gate (Decision 3.8): lists changes whose tasks above `## Delivery` are all `[x]` but still sit outside `openspec/changes/archive/`. Modes `--stop-hook` and `--pre-push`; repo-invariant test in `hooks.test.mjs` | Claude Code `Stop` hook, `.githooks/pre-push`, CI | **Canonical (Enforcement)** |
| `.claude/settings.json` `hooks.Stop` | Runs `node .claude/hooks/archive-gate.mjs --stop-hook`. It blocks the stop with a reason naming each unarchived completed change, and allows the stop when `stop_hook_active` is set | Claude Code | **Adapter** |
| `.claude/settings.json` `enabledPlugins` | Declares `superpowers@claude-plugins-official` (Decision 3.7). Declaring it does not install it: each teammate runs `claude plugin install superpowers@claude-plugins-official --scope project` once per clone. Other tools use the inline Build Loop in `.claude/agents/spec-builder.md` | Claude Code | **Adapter** |

### 2. Modification Rules (Zero-Drift Policy)

1. **Adding or changing a project invariant or coding rule:** Edit `AGENTS.md`, `openspec/config.yaml`, or `.agents/rules/coding-standards.md` (with an explicit `Blocking` vs. `Convention Candidate` reviewer level). **Never** copy rule prose into `CLAUDE.md` (use `@` import instead), `GEMINI.md`, `.kiro/steering/`, or a separate `.claude/rules/` folder. Whenever a new cross-tool rule or template is added to `.agents/rules/`, register it in `AGENTS.md`'s Scoped Rules & Templates Dispatch table (and `@`-import it in `CLAUDE.md` if always/workflow-critical) so fresh sessions outside Antigravity load it automatically (Decision 3.4).
2. **Recording a decision (mandatory on `question-round` & prompter changes):** Run `node scripts/record-decision.mjs --category 1|2|3|4 ...` so `X.Y` IDs and `docs/decisions/index.md` stay deterministic, then add `// Decision X.Y` pointers in affected files.
3. **Adding a new workflow skill:** Create `.agents/skills/<name>/SKILL.md` first, then create a relative symlink `.claude/skills/<name> -> ../../.agents/skills/<name>`.
4. **Adding or changing a hook or helper script:** Implement shared logic in `.claude/hooks/lib.mjs` or `scripts/*.mjs`, add `node:test` coverage in `.claude/hooks/hooks.test.mjs` or `scripts/harness-scripts.test.mjs`, verify tests pass, and never weaken `.env` secret isolation (agents always use `node scripts/check-setup.mjs --forge`, never reading `.env`).
   - **RTK invariant:** RTK's hook rewrites `git ...` to `rtk git ...`, so `stripLeadingEnvAssignments` in `lib.mjs` also strips a leading `rtk` (path-qualified, with flags, and the `proxy`/`bypass`/`err`/`test`/`summary`/`run` subcommands) plus the `env`, `command`, `nice`, `time`, `sudo` and `xargs` wrappers, and `guard-bash.mjs` re-evaluates `sh|bash|zsh -c`, `rtk run -c`, `env -S` and `eval` strings up to depth 3 (deeper nesting is blocked). Every guard must keep checking the *unwrapped* command; `hooks.test.mjs` covers this ("sees through the rtk wrapper", "blocks guarded commands hidden behind wrappers and command strings"). RTK auto-allows the commands it rewrites, so this guard is the only pre-execution check. Never add a guard that matches on a raw command string without going through that helper, and never weaken it. `./mvnw` stays in RTK's `exclude_commands` as a safeguard: RTK 1.2.1 rewrites bare `mvn` but not `./mvnw`.
5. **Adding a new AI tool or orchestrator:** Add a **thin pointer file** (<=25 lines) in that tool's native config directory pointing to `AGENTS.md`, `openspec/`, and `.githooks/`. Never fork specs into a second spec folder (e.g., avoid `.kiro/specs/` or `.claude/plans/`).
6. **Changing the app tech stack (pluggable slots):** Only swap stack-specific `.agents/rules/<stack>.md` files, stack check skills, `docs/api.md` contract content, `test-gate.mjs` path commands, and CI build jobs. Keep OpenSpec, decision registry, coding-rule structure, roles, forge switch, and secret/branch hooks untouched.

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
   - *(Optional, per developer)* If teammates use RTK, keep the `rtk` unwrapping in `lib.mjs` and its test, install with `rtk init -g --hook-only`, and exclude any version-pinning wrapper (`./mvnw`) via `[hooks] exclude_commands` in RTK's `config.toml`. Do not commit RTK output or RTK's `~/.claude` files.
6. **Verify End-to-End:**
   - Run `OPENSPEC_TELEMETRY=0 openspec validate --all` -> exit `0`.
   - Run `node --test .claude/hooks/hooks.test.mjs scripts/harness-scripts.test.mjs` -> exit `0`.
   - Run `node scripts/check-setup.mjs` -> 0 failures; validate `.claude/settings.json` JSON and `.gitlab-ci.yml` YAML; execute a cold `reviewer` pass to confirm zero rule drift across all canonical and pointer files.
