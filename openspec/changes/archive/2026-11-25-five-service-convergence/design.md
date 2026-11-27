# Design: Five-Service Convergence

## 1. Decision Map

| Decision | Spec effect |
|---|---|
| 1.18 Seven slices | `evaluation` holds evidence, formula, matrix and results. `context` is the only prompt builder. |
| 1.19 Shared contracts | UUID keys, `AttemptCompletedEvent`, professor routes under `/api/v1/ranking/**`, `400` ProblemDetail. REQ-AC-18, REQ-IS-09, REQ-IS-10. |
| 1.20 Simulated Annealing | REQ-TB-09..12. REQ-TB-01/02 (LLM modes) removed. REQ-TB-06 keeps the BFI-10 balance as a trait term. |
| 1.21 Per-attempt contexts | REQ-CC-01, REQ-AC-06, REQ-AC-12, REQ-AC-18 scenario. |
| 2.21 One-shot assessments | REQ-IS-01, REQ-IS-08. REQ-IS-02/03/04 removed. |
| 2.22 Question weights only | REQ-FE-03, REQ-FE-06, REQ-CR-01. |
| 2.23 Grouping settings | REQ-AC-01, REQ-AC-14, REQ-IS-05, REQ-IS-11, REQ-TB-10. |
| 2.24 Injection flags | REQ-AC-09, REQ-EP-07, REQ-SM-01, REQ-SM-08, REQ-CC-03, REQ-CC-04. |
| 2.25 Machine-parsable output | REQ-EP-08, REQ-FE-01, REQ-CC-06. |

## 2. Requirement IDs

An ID keeps its meaning. New content gets a new ID. A requirement that no longer applies is REMOVED with a reason. The teammate changes add REQ-AC-17, REQ-CC-09, REQ-EV-01/02 and REQ-TB-08 on top of this change.

## 3. Order

Archive this change first. The five teammate changes then apply their deltas to the synced specs.
