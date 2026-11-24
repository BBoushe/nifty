---
trigger: always_on
description: Enforce spec-driven development via OpenSpec and core Evidence-First domain invariants across all tasks.
---

# Spec-Driven & Domain Invariant Rule

1. **Specs First:** Before writing production code for any feature, verify a corresponding spec exists in `openspec/specs/` and an active change plan (`proposal.md`, `requirements.md` in EARS notation, `design.md`, `tasks.md`) exists in `openspec/changes/<change>/`.
2. **EARS Traceability:** Every acceptance criterion must use EARS notation (`WHEN`, `WHILE`, `WHERE`, `IF ... THEN`, `THE SYSTEM SHALL`).
3. **Deterministic Core:**
   - Never let an LLM compute totals, fit, coverage, rankings, cohort statistics, or team-balance numbers.
   - Missing verified evidence MUST produce `N/A` (never `0` or `1`) and be excluded from fit calculations.
   - Every evidence quote MUST pass deterministic word-for-word substring verification against `assessment-transcript/v1`.
   - Confidence MUST be computed from measurable signals, never LLM self-report.
4. **Human Oversight:** Configurations, flagged/final scores, and team placements require explicit human approval; overrides require a reason and an immutable audit log entry.
5. **Verify Before Checking Off:** Only check off a task in `tasks.md` after executing and verifying the relevant test/build command.
6. **Plain Writing (Decision 3.5):**
   - Write new or changed spec and doc prose in ASD-STE100 (use the `asd-ste100` skill).
   - Keep instructions at 20 words or fewer per sentence. Keep descriptions at 25 words or fewer.
   - Use active voice. Write one instruction per sentence. Define a term before you use it.
   - Do not change EARS keywords (`WHEN`, `WHILE`, `WHERE`, `IF ... THEN`, `SHALL`).
   - Do not rewrite unchanged text. The `docs-ste-rewrite` change does that.
   - Check prose with `python3 .agents/skills/asd-ste100/scripts/ste-lint.py <file>`.
   - *Reviewer level:* **Convention Candidate**.
