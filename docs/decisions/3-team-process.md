# Category 3 — Team & Process Decisions (`3.x`)

Engineering workflow, AI harness governance, coding standards, and review policies. See [index.md](./index.md) for the full registry.

---

## 3.1 — Single `openspec/changes/` plan tree & cold review separation
- **Date:** 2026-11-23
- **Triggered By:** prompter-change
- **Context:** Multi-agent development risks spec/plan drift and blind self-approval when builders review their own diffs.
- **Decision:** Use `openspec/changes/<change>/` (`tasks.md` + `## Handoff Log`) as our sole spec & execution plan tree (no separate `plans/` folder) and enforce cold, read-only `reviewer` audits (`guard-agent.mjs --mode readonly`) with zero builder chat history.
- **Alternatives Considered:** Separate `.claude/plans/` folder; allowing builders to self-approve PRs.
- **Rationale / Justification:** Prevents checklist drift during a 1-week sprint and reliably catches security and domain-invariant regressions.
- **Affected Files / Pointers:** `openspec/config.yaml`, `.claude/agents/reviewer.md`, `docs/setup.md`, `docs/workflow.md`

## 3.2 — Canonical living coding standards
- **Date:** 2026-11-23
- **Triggered By:** prompter-change
- **Context:** Course grading evaluates code maintainability, DRY/ETC discipline, minimal code footprint, hotspot performance, and frontend structure — and rules discovered mid-sprint must reach every AI harness.
- **Decision:** Store all living coding standards in a single canonical file (`.agents/rules/coding-standards.md`, `trigger: always_on`), imported by `CLAUDE.md` (`@` import), linked from `AGENTS.md`, followed by `spec-builder`, and enforced by cold `reviewer` (blocking on DRY/duplicate classes, hotspot Stream misuse, missing `docs/api.md` updates, or un-grouped bespoke views; Convention Candidates for subjective style).
- **Alternatives Considered:** Duplicating coding rules into `.claude/rules/` or scattering them across chat prompts.
- **Rationale / Justification:** One living source of truth across Antigravity, Claude Code, Codex, Kiro Crew, and T3 Code with zero rule drift.
- **Affected Files / Pointers:** `.agents/rules/coding-standards.md`, `AGENTS.md`, `CLAUDE.md`, `.claude/agents/spec-builder.md`, `.claude/agents/reviewer.md`

## 3.3 — Mandatory decision recording & code pointers
- **Date:** 2026-11-23
- **Triggered By:** prompter-change
- **Context:** Course grading rewards explicit traceability and justification for architectural, design, team, and direction decisions — especially when requirements evolve mid-sprint.
- **Decision:** Link `question-round` directly to `record-decision` (`scripts/record-decision.mjs`), mandate invoking `record-decision` (`--trigger prompter-change`) whenever the prompter changes or redirects any choice, and require builders to leave concise `// Decision X.Y` pointers in code and specs.
- **Alternatives Considered:** Ad-hoc manual edits to a single flat Markdown table without automated IDs or code pointers.
- **Rationale / Justification:** Guarantees every non-obvious implementation choice can be traced straight back to its rationale, alternatives considered, and trigger event.
- **Affected Files / Pointers:** `docs/decisions/index.md`, `scripts/record-decision.mjs`, `.agents/skills/record-decision/SKILL.md`, `.agents/skills/question-round/SKILL.md`

## 3.4 — Unified PR/MR template and cross-tool scoped rule dispatch
- **Date:** 2026-11-23
- **Triggered By:** prompter-change
- **Context:** A fresh Claude Code session created an ad-hoc PR summary instead of using .github/PULL_REQUEST_TEMPLATE.md because pr-memo had a competing format and non-Antigravity tools do not auto-parse .agents/rules YAML frontmatter.
- **Decision:** Unify .github/PULL_REQUEST_TEMPLATE.md, .gitlab/merge_request_templates/Default.md, and pr-memo/SKILL.md onto one concise template; auto-import spec-driven.md and git-workflow.md in CLAUDE.md via @-imports; and add a Scoped Rules & Templates Dispatch table in AGENTS.md.
- **Alternatives Considered:** Duplicating rules into .claude/rules/ or maintaining separate GitHub vs. pr-memo formats.
- **Rationale / Justification:** Guarantees every fresh session across Claude Code, Codex, Kiro, and Antigravity loads the exact same canonical rules and PR/MR template with zero duplication.
- **Affected Files / Pointers:** .github/PULL_REQUEST_TEMPLATE.md, .gitlab/merge_request_templates/Default.md, .agents/skills/pr-memo/SKILL.md, .agents/rules/git-workflow.md, CLAUDE.md, AGENTS.md, docs/setup.md

## 3.5 — Adopt ASD-STE100 for specs and docs; reject Caveman
- **Date:** 2026-11-24
- **Triggered By:** prompter-change
- **Context:** The course toolkit suggests Caveman (terse terminal replies) and ASD-STE100 (Simplified Technical English with a linter) for docs.
- **Decision:** Install the vetted asd-ste100 skill and apply ASD-STE100 to new or changed spec and doc prose only (reviewer level: Convention Candidate). Rewrite existing docs later in one dedicated change (docs-ste-rewrite) after the parallel harness changes merge. Do not adopt Caveman.
- **Alternatives Considered:** Caveman for terminal chat; rewrite all docs now; own minimal STE rule without the third-party skill.
- **Rationale / Justification:** Clear docs today are lean, unambiguous input for agents tomorrow. A mass rewrite now would conflict with every parallel PR. Caveman saves few tokens and makes replies harder for teammates to read.
- **Affected Files / Pointers:** openspec/changes/ai-context-docs/, openspec/changes/docs-ste-rewrite/, .agents/rules/spec-driven.md

## 3.6 — AI context docs: feature history, feature backlog and ai_docs
- **Date:** 2026-11-24
- **Triggered By:** prompter-change
- **Context:** Agents lack a single place for what was built (history), what comes next (backlog), and current library docs for versions newer than model training (Spring Boot 4, Spring AI 2.0, Timefold 2).
- **Decision:** Add docs/feature_history.md (one entry per archived OpenSpec change, enforced by a harness test), docs/feature_backlog.md (planned features with status, dependencies and blast radius) and docs/ai_docs/ (curated, versioned library notes with source URL and retrieval date). AGENTS.md dispatch tells agents when to read them.
- **Alternatives Considered:** Rely on CHANGELOG.md and openspec/changes/archive only; fetch docs ad hoc each session.
- **Rationale / Justification:** History and backlog give fast, low-token orientation. ai_docs stop agents from guessing APIs of library versions they were not trained on.
- **Affected Files / Pointers:** openspec/changes/ai-context-docs/, AGENTS.md

## 3.7 — Superpowers loops wired into agent roles
- **Date:** 2026-11-24
- **Triggered By:** prompter-change
- **Context:** Superpowers (TDD, systematic debugging, verification) is installed per developer only, and the role prompts do not require proof that a test failed before the fix.
- **Decision:** Declare the Superpowers plugin in .claude/settings.json. Spec builders follow brainstorming before propose, TDD for code tasks, systematic debugging on failures and verification before checking off. The Handoff Log Verification line shows the RED command and failure, then the GREEN command with exit 0. The cold reviewer marks missing RED evidence as Blocking. Non-Claude tools follow the same steps written inline in the role prompts.
- **Alternatives Considered:** A hook that guesses whether TDD happened (unreliable); leave Superpowers optional.
- **Rationale / Justification:** Proving RED before GREEN is the cheapest first-time-quality lever; a cold reviewer can check it from the diff without trusting the builder.
- **Affected Files / Pointers:** openspec/changes/superpowers-loops/, .claude/agents/

## 3.8 — Completed OpenSpec changes must be archived
- **Date:** 2026-11-24
- **Triggered By:** prompter-change
- **Context:** Four completed changes stayed in openspec/changes/ after their work merged, which made it unclear what was still active.
- **Decision:** An archive gate reports every change outside openspec/changes/archive/ whose tasks above the Delivery heading are all checked: a Claude Code Stop hook blocks with a reason, .githooks/pre-push blocks feature-branch pushes, and a repo-invariant test fails in CI. Archiving is a Delivery step, never a task in tasks.md.
- **Alternatives Considered:** Manual discipline; an archive task in tasks.md (an unchecked task would hide the change from the gate).
- **Rationale / Justification:** A deterministic gate on three layers works for every tool and human, so finished changes cannot linger.
- **Affected Files / Pointers:** openspec/changes/superpowers-loops/, .claude/hooks/archive-gate.mjs

## 3.9 — Parallel, resumable delivery by blast radius
- **Date:** 2026-11-24
- **Triggered By:** prompter-change
- **Context:** The team runs several agent sessions at once and sometimes runs out of usage mid-task, then continues in another tool or subscription.
- **Decision:** Split upgrades into OpenSpec changes with disjoint file ownership: one change, one branch off origin/dev, one git worktree, one cold review session, one PR. Shared files are append-only and rebased before the PR. Decisions for parallel changes are recorded before they start. Each tasks.md keeps an overwritten Status block, nested step checkboxes, a Delivery checklist and the Handoff Log, and the branch is committed and pushed after every task, so any tool can resume from the repo alone.
- **Alternatives Considered:** One large harness PR; progress kept only in chat history.
- **Rationale / Justification:** Disjoint ownership avoids merge conflicts and gives each PR a focused review. Progress stored in the repo survives usage limits and tool switches.
- **Affected Files / Pointers:** docs/prompts/harness-upgrade-board.md, openspec/config.yaml

## 3.10 — All changes reach dev through a pull request
- **Date:** 2026-11-24
- **Triggered By:** prompter-change
- **Context:** forge-ci-parity applied a GitHub dev ruleset with a pull_request rule and no bypass actors, so GitHub rejects direct pushes to dev, including docs: and hotfix: commits that git-workflow.md still allowed.
- **Decision:** Every change reaches dev through a PR (GitHub) / MR (GitLab mirror). Small docs or status fixes go in a docs/<slug> PR. The local hooks keep their docs:/hotfix: exemption; GitHub is the gate that rejects the push.
- **Alternatives Considered:** Add a ruleset bypass for docs: pushes (needs an admin bypass actor and a second policy layer); tighten the local hooks too (more code and tests for no extra safety, since GitHub already rejects).
- **Rationale / Justification:** Matches the server behaviour that already exists, keeps one rule, and needs no ruleset or hook change.
- **Affected Files / Pointers:** .agents/rules/git-workflow.md, AGENTS.md, docs/workflow.md, .github/rulesets/dev.json

## 3.11 — Orchestrated multi-agent delivery from one session
- **Date:** 2026-11-25
- **Triggered By:** prompter-change
- **Context:** Running one terminal session per change and managing GitHub PR numbers next to different GitLab MR numbers was too much manual work. The F6 rewrite already ran as subagents from one session and worked.
- **Decision:** One orchestrator session runs the parallel changes: a builder subagent per change in its own ../nifty-<change> worktree (pwd check on every command), a fresh cold reviewer subagent per PR, PR/MR handling keyed by branch name (forge.mjs prs/pr/push/sync), and the merge order. Agents never merge. The orchestrator asks the user before every merge and first gives a short summary of the high-level architecture decisions of that change.
- **Alternatives Considered:** One terminal session per change (the 2026-11-24 board); merging automatically on green CI plus an approving review.
- **Rationale / Justification:** The user talks to one session and never needs PR/MR numbers; the per-merge architecture summary keeps a human steering direction; the tasks.md Status block keeps every change resumable from any tool.
- **Affected Files / Pointers:** .agents/skills/orchestrate-changes/SKILL.md, scripts/forge.mjs, docs/workflow.md
