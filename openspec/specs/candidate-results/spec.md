# Domain Spec: Candidate Results (`candidate-results`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-10, §12 D10 & §14)
> **Primary View:** Assessment View (`CANDIDATE`)

## Purpose

Provide candidates with full post-approval transparency into their skill profile—showing approved scores, `N/A` explanations, confidence indicators, verbatim evidence quotes, question/skill weights, and the exact formula version used—alongside a dispute entry point.

## Scope

- Gated visibility: results remain hidden until the professor approves the candidate's scores (exact timing relative to team publication tracked in Open Decision O6).
- Candidate profile breakdown: per-skill approved level (or explicit `N/A`), confidence badge, linked verbatim transcript quotes with question origin, applied weights, and exact formula version.
- Export own `*.transcript.json` (`assessment-transcript/v1`) where permitted.
- **Dispute** action (UI mock in MVP) routing disputed items to the reviewer queue in future iterations.

## Seed Requirements (EARS)

- **REQ-CR-01:** **WHEN** the professor approves a candidate's scores **THE SYSTEM SHALL** make the candidate's own scores, evidence, weights and formula visible on their results page.
- **REQ-CR-02:** **WHILE** a candidate's scores are not yet approved by the professor **THE SYSTEM SHALL** deny access to scores, weights, and formula details on the candidate results page.
- **REQ-CR-03:** **THE SYSTEM SHALL** restrict candidate result queries so a candidate can view only their own approved profile and **SHALL NOT** access another candidate's transcript or scores.
