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
- **Parallel GitHub / GitLab switch:** Set `GIT_FORGE=github` or `GIT_FORGE=gitlab` in `.env` and run `node scripts/check-setup.mjs` — agents read only the non-secret forge setting and never see your API keys.
- **Cold, separate-session review:** The agent that wrote the code should never grade its own homework. A fresh, read-only **Reviewer** session inspects only the Git diff, specs, coding standards, and decisions, separating hard **Blocking** defects from non-blocking **Convention Candidates**.
- **Portable hooks for everyone:** Zero-dependency Node `.mjs` hooks (tested by `node --test`) + `.githooks/` block accidental `.env` secret leaks, bad commit messages, and direct pushes to protected branches identically on **macOS, Windows, and Linux**.

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
6. **Open a short PR / MR into `dev`:** Use `pr-memo` (auto-adapts to `gh` or `glab` via `scripts/check-setup.mjs --forge`) to produce a <=10-line plain-language summary.

---

## For Agents

This section is the **canonical source of truth** for any AI agent tasked with understanding, adjusting, auditing, or replicating this SDD/ADD harness in another repository. Stack-specific rules and test commands are pluggable slots — keep the structural invariants below intact.

### 1. Canonical Inventory & Ownership Table

| Path | Purpose | Read By | Authority |
|---|---|---|---|
| `openspec/config.yaml` | Schema (`spec-driven`), project context, non-negotiable invariants, EARS & Handoff Log rules | OpenSpec CLI, all agents | **Canonical** |
| `openspec/specs/<module>/spec.md` | Long-lived domain requirements in EARS notation + scenarios | All agents & humans | **Canonical** |
| `openspec/changes/<change>/` | Active execution plans (`proposal.md`, `specs/`, `design.md`, `tasks.md` + `## Handoff Log`, `.openspec.yaml`) | All agents & humans | **Canonical** |
| `AGENTS.md` | Universal engineering rules, domain invariants, Git policy, role parity table, cross-tool hook parity table | All agents (universal standard) | **Canonical** |
| `.agents/rules/*.md` | Scoped progressive rules (`always_on`, `glob`, `model_decision`), including canonical `coding-standards.md` — pluggable coding & stack slots | Antigravity (`agy`), imported by `CLAUDE.md` (`@`), referenced by `AGENTS.md` | **Canonical** |
| `.agents/skills/*/SKILL.md` | Shared tool-agnostic workflow skills (OpenSpec skills + `record-decision`, `question-round`, `pr-memo`) | Antigravity (`agy`), Codex, symlinked to Claude Code | **Canonical** |
| `docs/decisions/` | Hierarchical decision registry (`index.md`, `1-architecture.md`, `2-design.md`, `3-team-process.md`, `4-direction.md`) with `X.Y` code pointers | All agents & humans | **Canonical (Decisions)** |
| `docs/api.md` | Living API contract summary (pluggable contract slot synced alongside runtime OpenAPI) | Builders, reviewers, humans | **Canonical (Contract Slot)** |
| `scripts/record-decision.mjs` & `scripts/check-setup.mjs` | Zero-dependency Node helpers: deterministic `X.Y` decision recorder + secret-safe forge reader (`GIT_FORGE`) & setup preflight (`scripts/harness-scripts.test.mjs`) | All agents, humans, CI | **Canonical (Automation)** |
| `.claude/agents/*.md` | Role prompts & tool-boundary hooks (`spec-builder`, cold read-only `reviewer`, `tests-only` `test-writer`) | Claude Code (mirrored by `AGENTS.md` for other tools) | **Canonical (Roles)** |
| `.claude/hooks/*.mjs` | Zero-dependency cross-platform safety guards (`lib`, `guard-files`, `guard-bash`, `guard-agent`, `test-gate`) + `hooks.test.mjs` | Claude Code `PreToolUse`, `.githooks/`, CI | **Canonical (Enforcement)** |
| `.claude/settings.json` | Claude Code `permissions.allow` / `permissions.deny` and `PreToolUse` hook wiring | Claude Code | **Adapter** |
| `.claude/skills/` | Symlinks to `.agents/skills/*` plus Claude-oriented verification skills (`spec-implementer`, stack checks) | Claude Code | **Adapter / Symlinks** |
| `CLAUDE.md` | Claude Code entrypoint (`@` import of canonical `coding-standards.md`) + forge-aware First-Run Bootstrap | Claude Code, T3 Code | **Thin Pointer** |
| `GEMINI.md` | Antigravity / Gemini CLI verifier persona & quota-conscious habits | Antigravity (`agy`), Gemini CLI | **Thin Pointer** |
| `.kiro/steering/*.md` | `inclusion: always` steering bridge (<=25 lines) directing Kiro IDE / Kiro Crew to `AGENTS.md`, `openspec/`, `.githooks/` | Kiro IDE, Kiro Crew | **Thin Pointer** |
| `.githooks/` | Portable Git hooks (`pre-commit` -> `guard-bash.mjs --pre-commit`, `commit-msg`, `pre-push` -> branch guard + `test-gate.mjs`) | Git (every CLI, IDE, wrapper, and human) | **Adapter (Universal Enforcement)** |
| `.github/workflows/ci.yml` & `.gitlab-ci.yml` | Parallel GitHub Actions & GitLab CI pipelines running hook/script tests, commit lint, branch policy, and pluggable build jobs | GitHub Actions / GitLab CI | **Enforcement (Remote)** |
| `.github/rulesets/*.json` | Declarative branch protection rulesets synced to GitHub via `scripts/` | GitHub Repository Governance | **Enforcement (Remote)** |

### 2. Modification Rules (Zero-Drift Policy)

1. **Adding or changing a project invariant or coding rule:** Edit `AGENTS.md`, `openspec/config.yaml`, or `.agents/rules/coding-standards.md` (with an explicit `Blocking` vs. `Convention Candidate` reviewer level). **Never** copy rule prose into `CLAUDE.md` (use `@` import instead), `GEMINI.md`, `.kiro/steering/`, or a separate `.claude/rules/` folder.
2. **Recording a decision (mandatory on `question-round` & prompter changes):** Run `node scripts/record-decision.mjs --category 1|2|3|4 ...` so `X.Y` IDs and `docs/decisions/index.md` stay deterministic, then add `// Decision X.Y` pointers in affected files.
3. **Adding a new workflow skill:** Create `.agents/skills/<name>/SKILL.md` first, then create a relative symlink `.claude/skills/<name> -> ../../.agents/skills/<name>`.
4. **Adding or changing a hook or helper script:** Implement shared logic in `.claude/hooks/lib.mjs` or `scripts/*.mjs`, add `node:test` coverage in `.claude/hooks/hooks.test.mjs` or `scripts/harness-scripts.test.mjs`, verify tests pass, and never weaken `.env` secret isolation (agents always use `node scripts/check-setup.mjs --forge`, never reading `.env`).
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
5. **Wire CI & Governance:**
   - Add `node --test .claude/hooks/hooks.test.mjs`, Conventional Commits validation, and branch promotion checks to `.github/workflows/ci.yml`.
   - Add declarative `.github/rulesets/*.json` matching the team's branch promotion flow.
6. **Verify End-to-End:**
   - Run `OPENSPEC_TELEMETRY=0 openspec validate --all` -> exit `0`.
   - Run `node --test .claude/hooks/hooks.test.mjs` -> exit `0`.
   - Validate `.claude/settings.json` JSON and execute a cold `reviewer` pass to confirm zero rule drift across all canonical and pointer files.
