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

## 3. Day-to-Day Ticket Loop & Ownership

### Day-to-Day Ticket Loop

1. Pick a Jira ticket (or `openspec/` module slice) and confirm no blocking `O1`–`O7` TBD remains (if blocked, run `question-round`).
2. Sync `dev` (`git checkout dev && git pull origin dev`) and create a branch (`feat/<KEY>-slug`, `fix/<KEY>-slug`, or `chore/<slug>`).
3. Ensure an approved OpenSpec change exists in `openspec/changes/<change>/` (`proposal.md`, `specs/`, `design.md`, `tasks.md`).
4. Implement tasks as **Spec Builder** (`.claude/agents/spec-builder.md`) with real build/test verification and atomic Conventional Commits (never chaining `git add && git commit`).
5. Append a 5-line entry to `## Handoff Log` in `tasks.md` (`Commits`, `Verification`, `Contracts/Migrations`, `Decisions`, `Open Questions`).
6. Run an independent **Cold Reviewer** session (`.claude/agents/reviewer.md`) on `git diff dev...HEAD` + `openspec/` specs (zero builder chat history) and resolve all `Blocking` items.
7. Generate a <=10-line PR body via `pr-memo`, open a PR into `dev`, and tag the module owner from the table below.

### Module Ownership Table (PR Reviewer Routing)

| Module / Area | Primary Paths | OpenSpec Modules | Suggested Reviewer |
|---|---|---|---|
| **Backend Core & Evidence Pipeline** | `backend/src/...` (transcript, extractor, verifier, audit) | `assessment-chat`, `evidence-pipeline` | Backend / AI Lead |
| **Formula Engine & Timefold Teams** | `backend/src/...` (formula, solver, constraints) | `formula-engine`, `team-builder` | Formula / Solver Lead |
| **Interviewer Studio & Auth** | `backend/src/...` (studio, security), `frontend/` studio | `interviewer-studio` | Studio / Security Lead |
| **Frontend Views & ECharts Matrix** | `frontend/src/...` (Assessment, Matrix, Results) | `skills-matrix-review`, `candidate-results` | Frontend Lead |
| **Harness, Specs, Docker & CI** | `openspec/`, `.claude/`, `.agents/`, `.kiro/`, `.githooks/`, `.github/`, `docs/` | `project-setup`, `harness-v2-upgrade` | Lead Setup Architect |

### Review & Documentation Policies

- **Blocking vs. Convention Candidate:** Reviewers **only** block PRs on `AGENTS.md` §2 domain/regulatory invariants, unmet `openspec/` EARS criteria, security/secret leaks, or failing builds/tests. Style preferences, naming polish, and optional refactors are reported under non-blocking **Convention Candidates** so block-week velocity never stalls on bikeshedding.
- **Link, Never Copy (Confluence ↔ GitHub):** Architecture, EARS specs, ADRs (`docs/decisions.md`), and setup guides live canonically in Git. Confluence pages and Jira tickets **link** to GitHub files/PRs instead of pasting copies that drift out of date.

## 4. Tool-Agnostic Multi-Agent Collaboration

No developer is locked into a single harness (Kiro Crew, T3 Code, Claude Code, Antigravity `agy`, Codex, or IDE). All specs (`openspec/`), rules (`AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.agents/rules/`), agent roles (`.claude/agents/`), skills (`.agents/skills/`, `.claude/skills/`), Kiro steering (`.kiro/steering/`), and hooks (`.githooks/`, `.claude/hooks/`) live in Git. See [`docs/setup.md`](./setup.md) for the standalone SDD/ADD harness guide.

### Recommended Builder / Verifier Split

| Role | Suggested Agent(s) | Responsibilities |
|---|---|---|
| **Spec Author / Architect** | Human + Gemini (`agy`) or Claude | Drafts `proposal.md`, EARS `specs/`, and `design.md`; breaks down `tasks.md`. |
| **Spec Builder** | Claude Code / Codex / Kiro Crew / T3 Code | Implements scoped tasks against `openspec/changes/<change>/tasks.md`, runs tests, appends `## Handoff Log`. |
| **Independent Test Writer** | Separate `test-writer` session | Writes JUnit 5 / Vitest tests strictly from `openspec/` EARS criteria (`tests-only` paths). |
| **Cold Reviewer / Auditor** | Fresh Antigravity (`agy`) or Claude `reviewer` session | Read-only audit of `git diff dev...HEAD` against `openspec/` + `AGENTS.md` invariants (<=30-line report). |
| **Approver** | Human Teammate | Reviews PRs, checks evidence/tests, approves merges into `alpha` (>=2) and `master` (>=4). |

### Using Optional Wrappers (Kiro Crew & T3 Code) vs. Direct CLI

- **Kiro Crew (optional multi-agent orchestrator):** `.kiro/steering/nifty-core.md` (`inclusion: always`) automatically points Kiro IDE and Kiro Crew to `AGENTS.md`, `openspec/`, and `.githooks/` without duplicating `.kiro/specs/`. Connect your existing **Claude Code** and/or **Codex** CLI logins as Kiro Crew worker backends (reusing existing subscriptions):
  - **Conductor (interactive):** Picks an approved `openspec/changes/<module>/` change, resolves any `O1`–`O7` open decisions via `question-round`, and dispatches independent branches/worktrees (`feat/evidence-pipeline`, `feat/team-builder`, etc.).
  - **Parallel Builder Workers:** Each background Claude Code / Codex worker owns one `openspec/changes/<module>/` slice, verifies tests locally, and appends to `tasks.md`'s `## Handoff Log`.
  - **Cold Reviewer Worker:** Triggered on completed branches with *only* `git diff dev...HEAD` + `openspec/` specs using `.claude/agents/reviewer.md` before opening the GitHub PR.
  - **Optional Hygiene Check:** On-demand or scheduled task running `OPENSPEC_TELEMETRY=0 openspec validate --all`, `node --test .claude/hooks/hooks.test.mjs`, and scanning `backend/src/` for domain invariant regressions.
- **T3 Code:** Attach your preferred CLI backend (Claude Code, Codex, etc.); it automatically reads `AGENTS.md`, `CLAUDE.md`, and `openspec/`.
- **Direct CLI (`claude`, `agy`, `codex`):** Gets the exact same specs, rules, roles, and `.githooks/` enforcement with zero wrapper dependency.

## 5. Human-in-the-Loop Policy (Product & Engineering)

- **In the product:** Humans approve interviewer configurations, flagged/final candidate scores, and final team assignments (EU AI Act Annex III & GDPR alignment).
- **In our repo:** Agents propose specs, code, and audits; humans review and approve merges to `alpha` and `master`.
