# Design: Traceable Decisions, Living Coding Standards & Parallel GitLab Support

## 1. Hierarchical Decision Registry (`docs/decisions/`)

```text
docs/
├── decisions.md                  # 15-line redirect stub -> docs/decisions/index.md (zero broken links)
└── decisions/
    ├── index.md                  # Master registry table (ID, Legacy ID, Category, Topic, Status, Date, Trigger, Link) + Open O1-O7 + Code Pointer guide
    ├── 1-architecture.md         # Category 1.x (migrated D7, D8, D11, D12, D13, D14 as 1.1-1.6 + 1.7 forge switch)
    ├── 2-design.md               # Category 2.x (migrated D1-D6, D9, D10 as 2.1-2.8 + 2.9 docs/api.md)
    ├── 3-team-process.md         # Category 3.x (3.1 OpenSpec + cold review, 3.2 coding-standards.md, 3.3 decision pointers)
    └── 4-direction.md            # Category 4.x (lecturer/stakeholder/prompter direction shifts)
```

- **`scripts/record-decision.mjs` (zero-dependency Node ESM):**
  - Maps `--category 1|2|3|4` to `{ file, name }`.
  - Scans existing `^## <cat>\.(\d+)\b` headings in that category file to compute `<cat>.<max + 1>`.
  - Appends a standardized section (`## <id> — <topic>` with `Date`, `Triggered By`, `Context`, `Decision`, `Alternatives Considered`, `Rationale / Justification`, `Affected Files / Pointers`).
  - Inserts a Markdown table row into `docs/decisions/index.md` right above `<!-- DECISION_INDEX_END -->` marker.
  - When `--resolves O<k>` is passed, updates the matching row in the Open Decisions table in `docs/decisions/index.md` with `Resolved -> <id>`.
  - Supports `--dry-run` and `--root <dir>` for isolated `node:test` testing.

## 2. Canonical Living Coding Standards & API Documentation

- **`.agents/rules/coding-standards.md`** (`trigger: always_on`): Single canonical file containing the 6 mandatory rules plus the Living Rule Growth Protocol.
- **Cross-Tool Zero-Duplication Wiring:**
  - Antigravity (`agy`): auto-loaded via `trigger: always_on`.
  - Claude Code (`claude`): `@.agents/rules/coding-standards.md` import in `CLAUDE.md`, plus explicit references in `.claude/agents/spec-builder.md` and `.claude/agents/reviewer.md`.
  - Codex / Gemini CLI / Kiro Crew / T3 Code: explicit pointer in `AGENTS.md` (and `.kiro/steering/nifty-core.md` already directs to `AGENTS.md` + `.agents/rules/`).
- **`docs/api.md`:** Living human/agent API reference complementing live `springdoc-openapi` (`/v3/api-docs`, `/swagger-ui.html`), documenting auth roles, `@RestControllerAdvice` `ProblemDetail` envelope, implemented health endpoints, and MVP module contract stubs.

## 3. Secret-Safe Forge Switch & Parallel GitLab (`glab`) Support

- **`.env.example`:** Adds `GIT_FORGE=github` (`github` | `gitlab`) and `GITLAB_HOST=gitlab.com`.
- **`scripts/check-setup.mjs` (zero-dependency Node ESM):**
  - Strictly allowlists **only** `GIT_FORGE`, `GITLAB_HOST`, `AI_EVALUATOR_PROVIDER`, `AI_EVALUATOR_MODEL` when reading `.env` (falling back to `.env.example` / defaults). Checks `KICONNECT_API_KEY` only as boolean `configured` vs `placeholder` — **never** returns or prints any secret value!
  - `--forge`: outputs `GIT_FORGE=<val>\nGITLAB_HOST=<val>\nCLI=<gh|glab>`.
  - Default preflight mode: verifies active forge CLI (`gh` or `glab`), `core.hooksPath=.githooks` + executable hooks, `.env` presence + placeholder status, `.claude/settings.json` JSON validity, `.agents/rules/coding-standards.md`, `docs/decisions/index.md`, Node 22+, Java 21, Docker, and OpenSpec CLI.
- **`.gitlab-ci.yml`:** Full 5-job GitLab CI/CD mirror of `.github/workflows/ci.yml` (`backend`, `frontend`, `harness-tests`, `conventional-commits`, `branch-policy`).
- **Tests:** `scripts/harness-scripts.test.mjs` (`node:test`) wired into both `.github/workflows/ci.yml` and `.gitlab-ci.yml`.
