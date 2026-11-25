# Proposal: Five-Service Convergence (`five-service-convergence`)

## Context & Problem

Five teammates designed five backend services on their own. The designs disagreed on ID types, event names, route prefixes, validation codes, weights and the team solver. The repo also had nine finer slices that the designs did not match. Builders cannot start in parallel until one contract exists.

## Scope

- Record Decisions 1.18–1.21 and 2.21–2.25: 7 slices, shared contracts, Simulated Annealing team optimizer, separate per-attempt contexts, one-shot immutable assessments, question-weighted scoring, grouping settings, injection flags, machine-parsable evaluator output.
- Update 7 domain specs and add `context-creation` through delta specs (this folder).
- Sync `docs/architecture.md`, `docs/api.md`, `docs/feature_backlog.md`, the AI log and the rules. Remove Timefold from the stack and the build.
- Add one implementation change per teammate (`studio-assessment-creation`, `context-creation`, `assessment-chat`, `evaluation-service`, `team-builder`) and the tickets in `docs/tickets/`.

## Out of Scope

- Production code for the five slices. Each implementation change delivers its own code.
- Inferred tags, config versions, LLM team Modes B and C (Post-MVP).
