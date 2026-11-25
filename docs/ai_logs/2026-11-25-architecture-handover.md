# 2026-11-25 — Architecture Handover: Nov 24 Feature List Reconciliation

## Prompt

The prompter gave an audit from another model and asked to reconcile the repo with the Nov 24 feature list. Key instructions:

- The feature list is the "Source of truth for the diverging decisions".
- Keep "0–4 + hatched N/A and Tailwind v4".
- Archive the stale canvas.
- Use "two roles professor and student/candidate but design it so it's easy to extend".
- Add the PDF gaps.

The prompter also added an optional BFI-10 personality test.

## Inputs

- `docs/feature-list-2026-11-24.md`, `docs/discovery-canvas.md`, `docs/decisions/`, `openspec/specs/`, `docs/design_system.md`.
- Course PDFs in `docs/pdfs/`: Spring Boot architecture (PDF 2), spec-driven design with Claude Code (PDF 4), agentic self-assessment architecture (PDF 5).

## Decisions

Three question rounds with the prompter. All answers are recorded with `scripts/record-decision.mjs`.

| ID | Decision |
|---|---|
| 1.11 | Answers are POSTed, and the interviewer reply streams back over SSE. Append-only ledger. Server-stored deadlines. |
| 1.12 | Rubric-blind interviewer. The evaluator runs after commit. |
| 1.13 (O2) | The interviewer also uses ki:connect NRW. |
| 1.14 (O1) | PrimeVue on Tailwind v4. The design system uses 0–4 + hatched `N/A`. |
| 1.15 | Two roles, `PROFESSOR` and `CANDIDATE`, easy to extend. |
| 1.16 | New `auth` and `audit` slices. `studio` owns inferred tags. |
| 1.17 | Students sign in with an invite link only. |
| 2.10 (O6) | Threshold-gated auto-release. Below the thresholds, the professor reviews. |
| 2.11–2.16 | (question, skill) confidence, coverage over weighted questions, latest attempt counts, min/max timers, full log, deterministic advice. |
| 2.17–2.19 (O3, O4, O5) | Everything comes from the professor's form. Presets only. |
| 3.12–3.14 | AI logs, Java/Spring conventions with read order, atomic backlog. |
| 4.1–4.4 (O7) | Feature list wins, Post-MVP items, BFI-10, real data kept until the professor deletes it. |

## Outputs

- OpenSpec change `nov24-reconciliation`: delta specs for 7 domain specs and the new `auth-access` spec.
- Canvas archived to `docs/archive/discovery-canvas-2026-11-22.md` and updated.
- `AGENTS.md`, `CLAUDE.md`, `openspec/config.yaml` and `.agents/rules/*` aligned.
- `docs/architecture.md` (dual-agent pipeline, ledger, domain model), `docs/api.md`, `docs/design_system.md`.
- `docs/feature_backlog.md` rebuilt as stories in delivery waves.

## Human Gates

- The prompter chose every option in three question rounds (11 answers).
- The prompter overrode the feature list once: the BFI-10 step replaces the "personality slider" exclusion.
- The merge into `dev` waits for the prompter's approval (Decision 3.11).

## Open Items

- A timed-out partial answer is logged but not scored (feature list rule). The prompter can reverse this.
- The interview budget per session has no decision yet.
- Code drift: `Role.java` and `SecurityConfig` still have `REVIEWER` until the `auth-accounts` feature ships.
