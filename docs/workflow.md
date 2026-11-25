# Team Workflow & Multi-Agent Operating Model

The team structures this repository as a **tool-agnostic, spec-driven AI software factory**. The 5-person team works across macOS, Windows, and Linux.

## 1. Git Branching & Release Promotion

```text
feat/* | fix/* | refactor/* | chore/*
          │  PR (CI passes, 0 approvals required)
          ▼
         dev  ◄── no direct pushes; docs: / hotfix: also go through a PR
          │  PR (CI passes, >= 2 approvals required)
          ▼
        alpha
          │  PR (CI passes, >= 4 approvals required)
          ▼
    master (default branch; rulesets also cover main if renamed)
```

- **Never push directly** to `master`/`main` or `alpha`.
- **Direct pushes to `dev`:** The GitHub `dev` ruleset rejects them for every commit type (Decision 3.10). Open a `docs/<slug>` or `hotfix/<slug>` PR instead. The local `.githooks/pre-push` and CI still let `docs:`/`hotfix:` commits through. GitHub is the gate.
- **Conventional Commits are mandatory** for every commit:
  `type(scope): short imperative summary` (for example, `feat(evidence): add verbatim quote verifier`, `fix(frontend): render hatched pattern for N/A cells`, `chore(specs): update team-builder tasks`).
- Keep Pull Requests **small, focused, and readable in under 60 seconds**. Explain **What** changed and **Why** in plain language.

## 2. Spec-Driven Development (`openspec/`)

All significant features follow the Kiro-style spec workflow. The workflow files are plain Markdown/YAML in `openspec/`:

1. **Propose & Specify (`openspec/changes/<feature>/`):**
   - `proposal.md` — Problem, scope, out-of-scope.
   - `specs/<capability>/spec.md` — Acceptance criteria in **EARS notation** (`WHEN … THE SYSTEM SHALL …`, `WHILE …`, `WHERE …`, `IF … THEN …`).
   - `design.md` — Data model, API contracts, UI flows, edge cases.
   - `tasks.md` — Ordered, verifiable implementation checklist.
2. **Human Review Gate:** A teammate reviews and approves the spec before heavy code generation begins.
3. **Implement & Verify:** Agents (or humans) implement tasks one by one. They run real builds and tests and mark items as done in `tasks.md`.
4. **Sync Domain Specs:** Once shipped, update the canonical domain spec under `openspec/specs/<module>/spec.md`.

## 3. Day-to-Day Ticket Loop & Ownership

### Day-to-Day Ticket Loop

1. Run `node scripts/check-setup.mjs --forge` if you are unsure whether the repo currently targets GitHub (`gh`), GitLab (`glab`), or both.
2. Pick a ticket (or `openspec/` module slice). Ensure that no blocking `O1`–`O7` TBD remains. If a TBD blocks you, run `question-round`. `scripts/record-decision.mjs` records the answer automatically.
3. Sync `dev` (`git checkout dev && git pull origin dev`). Create a branch (`feat/<KEY>-slug`, `fix/<KEY>-slug`, or `chore/<slug>`).
4. Ensure an approved OpenSpec change exists in `openspec/changes/<change>/` (`proposal.md`, `specs/`, `design.md`, `tasks.md`).
5. Implement tasks as **Spec Builder** (`.claude/agents/spec-builder.md`). Obey `.agents/rules/coding-standards.md`. Keep `docs/api.md` updated on endpoint changes. Leave `// Decision X.Y` pointers. Record any prompter direction shift with `node scripts/record-decision.mjs --trigger prompter-change ...`.
6. Append a 5-line entry to `## Handoff Log` in `tasks.md` (`Commits`, `Verification`, `Contracts/Migrations`, `Decisions`, `Open Questions`).
7. Run an independent, sandboxed **Cold Reviewer** session (`.claude/agents/reviewer.md`) on `git diff dev...HEAD` + `openspec/` + `.agents/rules/coding-standards.md` + `docs/decisions/index.md` (zero builder chat history). Resolve all `Blocking` items.
8. Generate a <=10-line summary with `pr-memo`. Open a PR (`gh`) and/or MR (`glab`) into `dev` (`node scripts/forge.mjs pr` does both when `GIT_FORGE=both`). Tag the module owner from the table below.

### Parallel GitHub & GitLab Forge Switch (Decision 1.7)

- The repo supports both **GitHub** (`.github/workflows/ci.yml`, `.github/rulesets/`, `gh`) and **GitLab** (`.gitlab-ci.yml`, `glab`) in parallel.
- Set `GIT_FORGE=github` or `GIT_FORGE=gitlab` (plus `GITLAB_HOST` for university GitLab) in your local `.env`.
- Run `node scripts/check-setup.mjs` to check CLI installation, authentication, `.githooks/` activation, and harness readiness for the active forge. It never prints `.env` secrets.

### Working With Both Forges (`GIT_FORGE=both`, Decision 1.8)

GitHub (`origin`) is primary. GitLab (`gitlab`) is the secondary course repo. Never hand-roll pushes or PRs for one side only:

| Step | Command |
|---|---|
| Push current branch to both (GitHub first. Stops if GitHub fails) | `node scripts/forge.mjs push` |
| Show open PRs and MRs side by side, with CI state | `node scripts/forge.mjs prs` |
| Open PR **and** MR with the same text | `node scripts/forge.mjs pr --title "<conventional title>" --body-file <file>` |
| After merging the PR **on GitHub**, fast-forward GitLab (`dev`/`alpha`/`master`) | `node scripts/forge.mjs sync` |
| Check for drift between the two | `node scripts/forge.mjs status` |
| Apply GitLab branch protections (equivalent of `.github/rulesets/`) | `scripts/apply-gitlab-protections.sh` |

Rules that prevent drift: merge only on GitHub. Use a merge commit, not squash, so GitLab can mark the MR as merged after `sync`. Do not merge MRs on GitLab. Every command has a dry-run mode: `--dry-run`. `sync` never force-pushes. So a diverged GitLab branch makes `sync` fail; it is never overwritten.

Only a GitLab **Maintainer** can run `forge.mjs sync`, because it pushes to protected branches (`dev`, `alpha`, `master`, `main`).

**CI parity (Decision 1.9).** Any job added to one CI file must be added to the other in the same PR. The `CI parity` drift test in `scripts/harness-scripts.test.mjs` fails when the two files run different build, test or image commands.

| GitHub job (`.github/workflows/ci.yml`) | GitLab job (`.gitlab-ci.yml`) |
|---|---|
| `Backend (Java 21 & Maven)` | `backend` |
| `Frontend (Vue 3.5 & Vite 8)` | `frontend` |
| `Hook Tests (Node)` | `harness-tests` |
| `Conventional Commits` | `conventional-commits` |
| `Branch Promotion Policy` | `branch-policy` |
| `Backend Image (Docker)` | `backend-image` |
| `Frontend Image (Docker)` | `frontend-image` |

### Module Ownership Table (PR Reviewer Routing)

| Module / Area | Primary Paths | OpenSpec Modules | Suggested Reviewer |
|---|---|---|---|
| **Backend Core & Evidence Pipeline** | `backend/src/...` (transcript, extractor, verifier, audit) | `assessment-chat`, `evidence-pipeline` | Backend / AI Lead |
| **Formula Engine & Timefold Teams** | `backend/src/...` (formula, solver, constraints) | `formula-engine`, `team-builder` | Formula / Solver Lead |
| **Interviewer Studio & Auth** | `backend/src/...` (studio, security), `frontend/` studio | `interviewer-studio` | Studio / Security Lead |
| **Frontend Views & ECharts Matrix** | `frontend/src/...` (Assessment, Matrix, Results) | `skills-matrix-review`, `candidate-results` | Frontend Lead |
| **Harness, Specs, Docker & CI** | `openspec/`, `.claude/`, `.agents/`, `.kiro/`, `.githooks/`, `.github/`, `docs/` | `project-setup`, `harness-v2-upgrade` | Lead Setup Architect |

### Review & Documentation Policies

- **Blocking vs. Convention Candidate:** Reviewers **only** block PRs/MRs for these reasons:
  - A violation of the `AGENTS.md` §2 domain/regulatory invariants.
  - Unmet `openspec/` EARS criteria.
  - A violation of a clear blocking rule in `.agents/rules/coding-standards.md` (DRY/duplicate classes, hotspot Java Streams, missing `docs/api.md` updates, un-grouped bespoke Vue views).
  - An undocumented architectural/direction shift that has no `docs/decisions/` entry.
  - Security/secret leaks.
  - Failing builds/tests.

  Subjective function-splitting, optional HOF refactors, and naming polish go under non-blocking **Convention Candidates**.
- **Link, Never Copy (Confluence ↔ Git):** The canonical home of architecture, EARS specs, hierarchical decisions (`docs/decisions/`), API docs (`docs/api.md`), and setup guides is Git. Wiki/Confluence pages and tickets **link** to repository files and PRs/MRs. They do not paste copies that drift out of date.

## 4. Tool-Agnostic Multi-Agent Collaboration

No developer is tied to a single harness (Kiro Crew, T3 Code, Claude Code, Antigravity `agy`, Codex, or IDE). All specs (`openspec/`), rules (`AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.agents/rules/`), agent roles (`.claude/agents/`), skills (`.agents/skills/`, `.claude/skills/`), Kiro steering (`.kiro/steering/`), and hooks (`.githooks/`, `.claude/hooks/`) are files in Git. See [`docs/setup.md`](./setup.md) for the standalone SDD/ADD harness guide.

### Recommended Builder / Verifier Split

| Role | Suggested Agent(s) | Responsibilities |
|---|---|---|
| **Spec Author / Architect** | Human + Gemini (`agy`) or Claude | Drafts `proposal.md`, EARS `specs/`, and `design.md`. Divides the work into tasks in `tasks.md`. |
| **Spec Builder** | Claude Code / Codex / Kiro Crew / T3 Code | Implements scoped tasks against `openspec/changes/<change>/tasks.md`, runs tests, appends `## Handoff Log`. |
| **Independent Test Writer** | Separate `test-writer` session | Writes JUnit 5 / Vitest tests strictly from `openspec/` EARS criteria (`tests-only` paths). |
| **Cold Reviewer / Auditor** | Fresh Antigravity (`agy`) or Claude `reviewer` session | Read-only audit of `git diff dev...HEAD` against `openspec/` + `AGENTS.md` invariants (<=30-line report). |
| **Approver** | Human Teammate | Reviews PRs, checks evidence/tests, approves merges into `alpha` (>=2) and `master` (>=4). |

### Orchestrated Delivery (Decision 3.11)

One Claude Code session can run several changes at once with the [`orchestrate-changes`](../.agents/skills/orchestrate-changes/SKILL.md) skill. You talk to that session only:

- A builder agent per change works in its own `../nifty-<change>` worktree. Every command starts with a `pwd` check.
- A fresh cold reviewer agent checks each PR.
- The orchestrator opens and tracks PRs/MRs by branch name. `node scripts/forge.mjs prs` shows each branch's GitHub PR and checks next to its GitLab MR and pipeline.
- Agents never merge. Before every merge, the orchestrator asks you and first summarises the change's high-level architecture decisions, the review verdict and the CI state.

Other tools follow the same steps by hand, with one session per change.

### Using Optional Wrappers (Kiro Crew & T3 Code) vs. Direct CLI

- **Kiro Crew (optional multi-agent orchestrator):** `.kiro/steering/nifty-core.md` (`inclusion: always`) automatically points Kiro IDE and Kiro Crew to `AGENTS.md`, `openspec/`, and `.githooks/` without duplicating `.kiro/specs/`. Connect your existing **Claude Code** and/or **Codex** CLI logins as Kiro Crew worker backends. This reuses existing subscriptions:
  - **Conductor (interactive):** Picks an approved `openspec/changes/<module>/` change. Resolves any `O1`–`O7` open decisions with `question-round`. Dispatches independent branches/worktrees (`feat/evidence-pipeline`, `feat/team-builder`, etc.).
  - **Parallel Builder Workers:** Each background Claude Code / Codex worker owns one `openspec/changes/<module>/` slice. It checks tests locally and appends to `tasks.md`'s `## Handoff Log`.
  - **Cold Reviewer Worker:** Runs on completed branches, before you open the GitHub PR. It gets *only* `git diff dev...HEAD` + `openspec/` specs. It uses `.claude/agents/reviewer.md`.
  - **Optional Hygiene Check:** On-demand or scheduled task. It runs `OPENSPEC_TELEMETRY=0 openspec validate --all` and `node --test .claude/hooks/hooks.test.mjs`. It also scans `backend/src/` for domain invariant regressions.
- **T3 Code:** Attach your preferred CLI backend (Claude Code, Codex, etc.). It automatically reads `AGENTS.md`, `CLAUDE.md`, and `openspec/`.
- **Direct CLI (`claude`, `agy`, `codex`):** Gets the same specs, rules, roles, and `.githooks/` enforcement with zero wrapper dependency.

## 5. Human-in-the-Loop Policy (Product & Engineering)

- **In the product:** Humans approve interviewer configurations and final team assignments. A human approves every attempt below the professor's thresholds before release (Decision 2.10; EU AI Act Annex III & GDPR alignment).
- **In our repo:** Agents propose specs, code, and audits. Humans review and approve merges to `alpha` and `master`.
