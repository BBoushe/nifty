# Domain Spec: Skills Matrix, Review Queue & Audit Log (`skills-matrix-review`)

> **Status:** Seed baseline (from `docs/discovery-canvas.md` §8 UX-04, UX-05, UX-06, UX-07, UX-12 & §14)
> **Primary View:** Ranking View (`PROFESSOR` / `REVIEWER`)

## Purpose

Provide an evidence-first visual review workspace where professors and TAs inspect candidate skill matrices, drill into verified quotes in one click, prioritize uncertain or high-impact scores, record auditable overrides, and inspect SQL-calculated cohort gaps.

## Scope

- **Skills Matrix Heatmap (ECharts):** Candidates × skills; color = level, hatched = `N/A`, red border = flagged, opacity = confidence; hover summary, click opens Evidence Drawer.
- **Evidence Drawer:** Step-by-step breakdown (`formula -> weighted parts -> evidence items -> verbatim quotes` linked to transcript position + question origin); actions: Approve, Override (reason required), Request re-probe.
- **Confidence Bounds & Prioritized Review Queue:** Lower/upper confidence bounds; bulk-approve allowed only above upper bound; flagged items must be approved individually. Queue priority: (1) below lower confidence bound, (2) team-placement-deciding scores, (3) large self-assessment mismatch, (4) remaining items.
- **Rubber-Stamp Detection:** Reviewer override rate and average time per review.
- **Immutable Audit Log:** Records actor, timestamp, entity, old value, new value, and mandatory reason (EU AI Act Annex III readiness).
- **Cohort Gap Overview (UX-12):** Pure SQL aggregation over **approved** scores only (level distribution, `N/A` share, weakest domains) with drill-down to underlying candidates and quotes.

## Seed Requirements (EARS)

- **REQ-SM-01:** **WHEN** a score's confidence is below the configured lower bound **THE SYSTEM SHALL** mark it as flagged and place it in the "Needs attention" queue.
- **REQ-SM-02:** **WHEN** the professor overrides a score **THE SYSTEM SHALL** require a reason and write an audit log entry with user, timestamp, old value and new value.
- **REQ-SM-03:** **WHILE** a score is marked as flagged **THE SYSTEM SHALL** prevent bulk approval and require individual human review.
- **REQ-SM-04:** **THE SYSTEM SHALL** compute all Cohort Gap Overview statistics in deterministic SQL from approved scores only and **SHALL NOT** generate cohort numbers via an LLM.
