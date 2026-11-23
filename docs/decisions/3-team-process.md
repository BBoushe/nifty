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
