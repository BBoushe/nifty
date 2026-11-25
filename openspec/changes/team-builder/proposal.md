# Proposal: Group Creation / Team Builder (`team-builder`)

## Summary

Implement the `teams` vertical slice (`de.fhdortmund.nifty.teams` + `frontend/src/views/teams/`) using a deterministic seeded pure-Java Simulated Annealing + 2-opt swap optimizer that balances inter-team skill/trait averages and maximizes intra-team diversity across `HARD_SKILLS`, `SOFT_SKILLS`, and `COMBINED` modes (Decisions 1.20, 2.23, 4.3, 4.4).

## Scope

- Flyway `V8__teams.sql` (`team_proposal`, `team_member`) with `UUID` primary and foreign keys.
- Pure zero-Stream Java `SimulatedAnnealingTeamOptimizer` (`2-opt` swap preserving `floor(N/G)` / `ceil(N/G)` team sizes, optional `seed`, `ENGINEERING_DEFAULT` `[0.30, 0.25, 0.20, 0.15, 0.10]` soft weights, explicit `N/A` exclusion per skill without coercion or cohort rejection).
- Integration strictly via public `@Service` contracts: `StudioQueryService.teamView`, `EvaluationQueryService.getCandidateProfilesForConfig`, `AssessmentQueryService.getSoftSkillProfilesForConfig` (already reverse-scored 1.0–5.0 trait means — NEVER double-inverted), and `AuditService`.
- `PROFESSOR`-only endpoints under `/api/v1/ranking/teams/**` (generate `DRAFT`, live drag-and-drop recalculate with zero LLM calls, publish `PUBLISHED` with `audit_log` entry, list proposals with professor-only trait scores).
- `AssessmentDeletedEvent` GDPR purge listener.
- Vue 3.5 + PrimeVue + ECharts Team Builder UI in `frontend/src/views/teams/`.

## Out of Scope

- Timefold Solver engine (superseded for MVP by Decision 1.20).
- LLM Mode B (text-to-constraints) and Mode C (LLM proposal) and case-study matching (Post-MVP, Decision 4.2).
- Re-scoring or reverse-coding raw BFI-10 items (`assessment` is the single source of truth).
