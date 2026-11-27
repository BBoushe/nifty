# Proposal: Evaluation Review Hardening (`evaluation-review-hardening`)

## Context & Problem

PR #33 (`fix/evaluation-review-and-tests`) applies Decision 2.30. The professor approves and overrides evaluations and releases attempts one by one. Bulk approval is gone. The PR also makes four related changes:

1. A release of an attempt that is already released answers `409` and writes no audit row.
2. An unknown review status now falls back to `pending` and never to `auto_released`.
3. The Evidence Drawer shows the full conversation ledger.
4. The review tests cover the new rules.

The PR edited `openspec/specs/skills-matrix-review/spec.md` in place, without an OpenSpec change. This left two problems:

- REQ-SM-03 says the system SHALL require individual professor review "for every evaluation and attempt release". REQ-SM-05 says an attempt that meets all thresholds is auto-released without professor action (Decision 2.10, kept by Decision 2.30). The two requirements contradict each other.
- Two concurrent `POST /attempts/{attemptId}/release` calls can both pass the already-released check. Both then write an `ATTEMPT_RELEASED` audit row.

## Scope

- Limit REQ-SM-03 to attempts that are not auto-released. Add scenarios for the auto-released case, the second release, and two concurrent releases.
- Lock the summary row in the release transaction (`PESSIMISTIC_WRITE`). The second caller waits and then gets `409`.
- Record the RED evidence for the re-release guard and the fail-closed status.

## Out of Scope

- A lock for approve and override. They load the response evaluation, not the summary row. A later change can add one if a race shows up.
- Any change to the threshold gate (REQ-SM-05, REQ-SM-06).
