# AI Logs (Prompt-as-Code Audit Trail)

This folder records how the team used AI agents to plan and build nifty (Decision 3.12). It is the audit trail for the Friday defense. A reader can follow each important AI handover without the chat history.

## When to Add a Log

Add one file after each of these events:

- An AI session makes or records architecture or product decisions.
- An orchestrator hands a batch of changes to builder agents.
- A debugging loop finds a root cause that changes a spec, rule or design.

Do not log routine tasks. The `## Handoff Log` in each `tasks.md` records them.

## File Format

Name: `YYYY-MM-DD-<topic>.md`. Keep each file under 80 lines. Use these sections:

1. **Prompt:** what the human asked, in one to three sentences. Quote key words.
2. **Inputs:** the files and sources that the agent read.
3. **Decisions:** the `Decision X.Y` IDs, each with one line.
4. **Outputs:** changes, PRs/MRs and files.
5. **Human gates:** each point where a human chose an option or approved a merge.
6. **Open items:** what is still open, with an owner.

## Rules

- Write in ASD-STE100 (`python3 .agents/skills/asd-ste100/scripts/ste-lint.py <file>`).
- Never paste secrets, `.env` content or real student data.
- Link to decisions, specs and PRs. Do not copy their content.

## Index

| Date | Log | Topic |
|---|---|---|
| 2026-11-25 | [2026-11-25-architecture-handover.md](2026-11-25-architecture-handover.md) | Nov 24 feature list reconciliation, O1–O7 closed |
