# Proposal: Reconcile Specs and Docs with the Nov 24 Feature List (`nov24-reconciliation`)

## Context & Problem

The Nov 23 team meeting produced [`docs/feature-list-2026-11-24.md`](../../../docs/feature-list-2026-11-24.md). It contradicts the discovery canvas, older decisions, the seed specs, the design system and the invariants in `AGENTS.md`. Open decisions O1–O7 blocked 6 of 7 MVP features. The professor's PDFs (`docs/pdfs/`) add gaps: dual-agent rubric isolation, a domain model, an `auth` slice, `docs/ai_logs/`, and Java/Spring conventions.

The 2026-11-25 decisions close all of this: 1.11–1.17, 2.10–2.19, 3.12–3.14 and 4.1–4.4. O1–O7 are resolved. This change writes those decisions into the specs and docs, so builders can start the MVP features without guessing.

## Scope

- **Domain specs (delta specs in this change):** `interviewer-studio`, `assessment-chat`, `evidence-pipeline`, `formula-engine`, `skills-matrix-review`, `candidate-results`, `team-builder`, and the new capability `auth-access`.
- **Discovery canvas:** archive the 2026-11-22 version as OUTDATED in `docs/archive/`, and update `docs/discovery-canvas.md` (Decision 4.1).
- **Invariants and rules:** `AGENTS.md`, `CLAUDE.md`, `README.md`, `openspec/config.yaml`, `.agents/rules/{spec-driven,backend-java,frontend-vue}.md`.
- **Architecture and API:** `docs/architecture.md` gets the dual-agent model, the conversation ledger, the domain model with an ER diagram, the enum table and the slice map. `docs/api.md` gets the role table and the planned endpoints.
- **Design system:** `docs/design_system.md` uses the 0–4 scale with hatched `N/A`, Tailwind v4 and PrimeVue (Decision 1.14).
- **Process:** `docs/ai_logs/` (Decision 3.12) and the rebuilt `docs/feature_backlog.md` (Decision 3.14).

## Out of Scope

- Code changes (`backend/**`, `frontend/**`, CI). The backlog lists them as features: foundation, auth, audit, and the domain slices.
- Post-MVP items (Decision 4.2): case-study matching, LLM-suggested Studio questions, the SpEL editor, the dispute workflow, Team Builder Mode C.
- A rewrite of `docs/decisions/` entries older than 2026-11-25. They stay as history; newer decisions supersede them.
