---
name: record-decision
description: Record any architecture, design, team-process, or direction decision deterministically into docs/decisions/ (with X.Y IDs) after question-round answers, prompter changes/redirections, or O1-O7 resolutions.
---

# Skill: Record Decision

## Mandatory Trigger Conditions

Invoke this skill **immediately** whenever ANY of the following occurs:
1. **End of a `question-round`:** Every answered question round must produce one or more traceable `X.Y` entries in `docs/decisions/`.
2. **Prompter Change / Redirection (`--trigger prompter-change`):** Anytime the user/prompter changes, redirects, rejects, or overrides a design, architecture, coding rule, team process, or product direction mid-conversation.
3. **Open Decision (`O1`–`O7`) Resolution:** Whenever the team resolves an open question in `docs/decisions/index.md` (pass `--resolves O<k>`).

## Category Mapping

- `1` — `docs/decisions/1-architecture.md` (stack, pipeline, infrastructure, LLM provider, forge/CI)
- `2` — `docs/decisions/2-design.md` (domain model, API contracts, scoring/evidence design, UX)
- `3` — `docs/decisions/3-team-process.md` (workflow, harness, coding standards, review rules)
- `4` — `docs/decisions/4-direction.md` (product scope, lecturer feedback, MVP cuts, pivots)

## Procedure

1. **Run the Deterministic Recorder Script:**
   ```sh
   node scripts/record-decision.mjs \
     --category 1|2|3|4 \
     --topic "<short title>" \
     --trigger "question-round|prompter-change|team" \
     --context "<why this decision came up>" \
     --decision "<exact choice made>" \
     --alternatives "<other options considered>" \
     --rationale "<why this choice is justified>" \
     --pointers "<comma-separated affected files>" \
     [--resolves O<k>]
   ```
   The script computes the next sequential `X.Y` ID, appends the structured entry to `docs/decisions/<category>-*.md`, updates `docs/decisions/index.md`, and marks `O<k>` resolved if `--resolves` was provided.
2. **Leave Code / Spec Pointers:**
   - Cite `// Decision X.Y: <short why>` in affected code/migrations and `Decision X.Y` in `openspec/changes/<change>/design.md` and `## Handoff Log`.
3. **Synchronize Downstream Harness Files (Same Commit/PR when affected):**
   - `openspec/config.yaml` — replace affected `TBD` text in `context:`.
   - `AGENTS.md` & `CLAUDE.md` — update affected rules or bootstrap checks.
   - `.agents/rules/*.md` and `.claude/skills/*/SKILL.md` — replace matching `[TBD — see docs/decisions/index.md O<k>]` placeholders.
4. **Verify Consistency:**
   - Run `OPENSPEC_TELEMETRY=0 openspec validate --all`.
5. **Commit Atomically:** Use `docs(decisions): record <X.Y> <short-topic>` (or include in the feature commit implementing the decision).
