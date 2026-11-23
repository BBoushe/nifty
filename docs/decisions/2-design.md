# Category 2 — Design Decisions (`2.x`)

Domain model, scoring/evidence contracts, API documentation, and UX design decisions. See [index.md](./index.md) for the full registry.

---

## 2.1 — Inferred tags from answers
- **Legacy ID:** D1
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Free-form candidate answers mention technologies and concepts outside the pre-defined skill taxonomy.
- **Decision:** Evidence extractor suggests inferred tags with `status: pending`; professors approve, merge, or reject them into the controlled taxonomy.
- **Alternatives Considered:** Auto-accepting LLM-extracted tags directly into the taxonomy.
- **Rationale / Justification:** Prevents tag sprawl (e.g., "Docker" vs. "containers") and keeps cross-candidate calibration consistent.
- **Affected Files / Pointers:** `docs/architecture.md`, `openspec/specs/evidence-pipeline/spec.md`

## 2.2 — Multi-skill answers
- **Legacy ID:** D2
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** A single conversational answer frequently demonstrates competency across multiple skills simultaneously.
- **Decision:** Allow one Q/A entry to produce evidence for multiple skills, each with its own level and confidence; cap single-Q/A impact per skill in the default formula.
- **Alternatives Considered:** Restricting each Q/A pair to a single target skill.
- **Rationale / Justification:** Reflects realistic interviews without allowing one verbose answer to dominate all scores.
- **Affected Files / Pointers:** `openspec/specs/evidence-pipeline/spec.md`, `openspec/specs/formula-engine/spec.md`

## 2.3 — Question & skill weights
- **Legacy ID:** D3
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Professors need to weight both individual core questions and overall skill importance while keeping scores explainable.
- **Decision:** Support both question weights (`w_q`) and skill weights (`w_s`) and display every multiplication step inside the Evidence Drawer.
- **Alternatives Considered:** Skill weights only without per-question weighting.
- **Rationale / Justification:** Full numerical transparency for professors and candidates.
- **Affected Files / Pointers:** `openspec/specs/formula-engine/spec.md`, `openspec/specs/skills-matrix-review/spec.md`

## 2.4 — Invented questions
- **Legacy ID:** D4
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Adaptive probing helps clarify edge-case answers, but unstandardized questions can skew cohort comparisons.
- **Decision:** Make invented questions optional via an Interviewer Studio toggle; tag them `origin: invented` with lower default weight and optional exclusion from cross-candidate ranking.
- **Alternatives Considered:** Disallowing adaptive follow-ups completely; treating invented questions identically to core questions.
- **Rationale / Justification:** Preserves cohort comparability while allowing targeted adaptive probing.
- **Affected Files / Pointers:** `openspec/specs/interviewer-studio/spec.md`, `docs/architecture.md`

## 2.5 — Turn budget vs. skill count
- **Legacy ID:** D5
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Finite interview turn budgets mean not every skill can always be probed deeply.
- **Decision:** Core questions guarantee baseline coverage; any untested skill with zero verified evidence MUST be recorded as explicit `N/A` (never `0` or `1`) and excluded from fit calculations.
- **Alternatives Considered:** Scoring unasked skills as `0` or `1`.
- **Rationale / Justification:** Prevents penalizing candidates for unasked domains and separates `fit` from `coverage`.
- **Affected Files / Pointers:** `AGENTS.md`, `docs/architecture.md`, `openspec/specs/formula-engine/spec.md`

## 2.6 — Post-interview config changes
- **Legacy ID:** D6
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Professors may tweak weights/formulas or add new skills/questions after some candidates have already completed interviews.
- **Decision:** Weight and formula changes recompute deterministically immediately; adding skills or core questions creates a new config version and flags earlier candidates as incomplete.
- **Alternatives Considered:** Mutating existing config versions in place.
- **Rationale / Justification:** Protects evidence integrity and auditability across versioned configurations.
- **Affected Files / Pointers:** `openspec/specs/interviewer-studio/spec.md`, `openspec/specs/formula-engine/spec.md`

## 2.7 — Ranking View UX framing
- **Legacy ID:** D9
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** EU AI Act Annex III and educational fairness discourage opaque, single-number student leaderboards.
- **Decision:** Keep the internal module name "Ranking View" for continuity, while emphasizing skill-spec fit, coverage, and team balance in the UI instead of a global leaderboard.
- **Alternatives Considered:** Single-score ranked leaderboard table.
- **Rationale / Justification:** Reduces EU AI Act / discrimination liability and better serves balanced team formation.
- **Affected Files / Pointers:** `openspec/specs/skills-matrix-review/spec.md`, `frontend/src/views/`

## 2.8 — Candidate transparency timing
- **Legacy ID:** D10
- **Date:** 2026-11-22
- **Triggered By:** baseline
- **Context:** Candidates deserve full explainability of how their assessment was evaluated without gaming live prompts.
- **Decision:** Reveal formula, weights, and verified evidence quotes to the candidate **only after human approval** (with a mocked dispute flow in MVP); never display scores or weights during an active interview.
- **Alternatives Considered:** Showing live running scores during chat; hiding formulas permanently.
- **Rationale / Justification:** Prevents mid-interview gaming while providing full post-approval GDPR/EU AI Act explainability.
- **Affected Files / Pointers:** `AGENTS.md`, `openspec/specs/assessment-chat/spec.md`, `openspec/specs/candidate-results/spec.md`

## 2.9 — Living `docs/api.md` synced with `springdoc-openapi`
- **Date:** 2026-11-23
- **Triggered By:** prompter-change
- **Context:** Frontend/backend teammates, AI agents, and course graders need fast, readable API documentation without booting the backend first.
- **Decision:** Maintain a concise living `docs/api.md` (endpoint table, auth roles, DTO shapes, error envelope, SSE contracts) updated in every PR that modifies backend controllers, complemented by live `springdoc-openapi` (`/v3/api-docs`, `/swagger-ui.html`).
- **Alternatives Considered:** Relying solely on runtime Swagger UI; committing large auto-generated OpenAPI dumps without human summary.
- **Rationale / Justification:** Gives humans and agents immediate offline contract visibility while keeping runtime OpenAPI schemas authoritative for TypeScript codegen.
- **Affected Files / Pointers:** `docs/api.md`, `.agents/rules/coding-standards.md`, `.claude/agents/reviewer.md`
