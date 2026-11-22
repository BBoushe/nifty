# Team Workflow & Multi-Agent Operating Model

This repository is structured as a **tool-agnostic, spec-driven AI software factory** for a 5-person team working across macOS, Windows, and Linux.

## 1. Git Branching & Release Promotion

```text
feat/* | fix/* | refactor/* | chore/*
          │  PR (CI passes, 0 approvals required)
          ▼
         dev  ◄── direct push allowed ONLY for docs: / hotfix: commits
          │  PR (CI passes, >= 2 approvals required)
          ▼
        alpha
          │  PR (CI passes, >= 4 approvals required)
          ▼
    master (default branch; rulesets also cover main if renamed)
```

- **Never push directly** to `master`/`main` or `alpha`.
- **Direct pushes to `dev`** are blocked by `.githooks/pre-push` and flagged by CI unless every commit uses a `docs:` or `hotfix:` Conventional Commit prefix.
- **Conventional Commits are mandatory** for every commit:
  `type(scope): short imperative summary` (e.g., `feat(evidence): add verbatim quote verifier`, `fix(frontend): render hatched pattern for N/A cells`, `chore(specs): update team-builder tasks`).
- Keep Pull Requests **small, focused, and readable in under 60 seconds** — explain **What** changed and **Why** in plain language.

## 2. Spec-Driven Development (`openspec/`)

All significant features follow the Kiro-style spec workflow stored as plain Markdown/YAML in `openspec/`:

1. **Propose & Specify (`openspec/changes/<feature>/`):**
   - `proposal.md` — Problem, scope, out-of-scope.
   - `requirements.md` — Acceptance criteria in **EARS notation** (`WHEN … THE SYSTEM SHALL …`, `WHILE …`, `WHERE …`, `IF … THEN …`).
   - `design.md` — Data model, API contracts, UI flows, edge cases.
   - `tasks.md` — Ordered, verifiable implementation checklist.
2. **Human Review Gate:** A teammate reviews and approves the spec before heavy code generation begins.
3. **Implement & Verify:** Agents (or humans) implement tasks one by one, running real builds and tests, checking off items in `tasks.md`.
4. **Sync Domain Specs:** Once shipped, update the canonical domain spec under `openspec/specs/<module>/spec.md`.

## 3. Tool-Agnostic Multi-Agent Collaboration

No developer is locked into a single harness (T3 Code, Claude Code, Antigravity `agy`, Codex, or IDE). All specs (`openspec/`), rules (`AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.agents/rules/`), skills (`.claude/skills/`), and hooks (`.githooks/`) live in Git.

### Recommended Builder / Verifier Split

| Role | Suggested Agent(s) | Responsibilities |
|---|---|---|
| **Spec Author / Architect** | Human + Gemini (`agy`) or Claude | Drafts `proposal.md`, EARS `requirements.md`, and `design.md`; breaks down `tasks.md`. |
| **Builder** | Claude Code / Codex / T3 Code / IDE | Implements scoped tasks against `openspec/changes/<change>/tasks.md`, writes unit & integration tests. |
| **Verifier / Auditor** | Antigravity (`agy` / Gemini) | Audits diffs against EARS requirements, checks domain invariants (no LLM math, `N/A` handling, verbatim quotes, audit logging), verifies builds/tests independently. |
| **Approver** | Human Teammate | Reviews PRs, checks evidence/tests, approves merges into `alpha` (≥2) and `master` (≥4). |

### Using Optional Wrappers (e.g., T3 Code) vs. Direct CLI

- Developers using **T3 Code** can attach their preferred backend agent (Claude Code, Codex, etc.) to this repo; it automatically reads `AGENTS.md`, `CLAUDE.md`, and `openspec/`.
- Developers using **direct CLI** (`claude`, `agy`, `codex`) get the exact same specs, rules, and `.githooks/` enforcement with zero wrapper dependency.

## 4. Human-in-the-Loop Policy (Product & Engineering)

- **In the product:** Humans approve interviewer configurations, flagged/final candidate scores, and final team assignments (EU AI Act Annex III & GDPR alignment).
- **In our repo:** Agents propose specs, code, and audits; humans review and approve merges to `alpha` and `master`.
