# Design: Team Builder (`teams`)

> **Canonical Contract Reference:** `docs/architecture.md`, `docs/api.md`, `docs/tickets/00-parallel-waves-and-contracts.md`, `docs/tickets/05-teams.md`.

## Package Layout (`de.fhdortmund.nifty.teams`)
- `web/`: `TeamBuilderController` (`/api/v1/ranking/configs/{configId}/teams/**` and `/api/v1/ranking/teams/**`, `PROFESSOR` only), `@Valid` record DTOs (`GenerateTeamsRequest`, `RecalculateTeamsRequest`, `TeamProposalResponse`, `TeamCardDto`, `TeamMemberDto`, `TeamDiagnosticsDto`).
- `service/`:
  - `TeamBuilderService` (`@Transactional(readOnly = true)` class default; `@Transactional` on generate/publish)
  - `SimulatedAnnealingTeamOptimizer` (**pure Java, ZERO Java Streams in inner 2-opt loop!**)
  - `TeamObjectiveCalculator` (pure Java: normalizes hard skills `x / 4.0`, BFI-10 means `(y - 1.0) / 4.0`, computes `inter_team_imbalance`, `intra_team_diversity`, and blended `cost = alpha * imbalance - (1 - alpha) * diversity` while excluding `N/A` values per dimension)
  - `TeamCleanupListener` (`AssessmentDeletedEvent`).
- `repository/`: Private `UUID`-keyed `@Entity` and `JpaRepository` classes (`TeamProposalEntity`, `TeamMemberEntity`).

## Flyway Migration (`V8__teams.sql`)
- `team_proposal(id UUID PK, config_id UUID NOT NULL, status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED')), num_teams INT NOT NULL CHECK (num_teams >= 1), grouping_mode VARCHAR(16) NOT NULL, alpha NUMERIC(5,4) NOT NULL, combined_hard_weight NUMERIC(5,4) NOT NULL, seed BIGINT NOT NULL, cost_metrics JSONB NOT NULL, created_by VARCHAR(128) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, published_at TIMESTAMPTZ)`
- `team_member(id UUID PK, proposal_id UUID NOT NULL REFERENCES team_proposal(id) ON DELETE CASCADE, team_no INT NOT NULL CHECK (team_no >= 1), invite_id UUID NOT NULL, UNIQUE (proposal_id, invite_id))`

## Mathematical Rules (Decisions 1.20, 2.23, 4.3)
- Default `numTeams = Math.max(1, (int) Math.ceil((double) N / teamView.teamSize()))` (overrideable at run time).
- **Never double-invert BFI-10:** `AssessmentQueryService` delivers already reverse-scored `1.0..5.0` trait means (`conscientiousness`, `agreeableness`, `neuroticism`, `extraversion`, `openness`).
- **Explicit `N/A` handling:** For each hard skill dimension `h`, compute cohort mean, team mean, and pairwise diversity strictly over members with `status == SCORED` (`score != null`). If a team has `0` scored members for skill `h`, skip `h` in that team's imbalance term and report `naCount` in `TeamDiagnosticsDto`. Never coerce `N/A` to `0` or `1` and never reject a candidate for having `N/A` skills!
- **Hotspot performance:** Pre-convert student profiles to primitive `double[][]` + `boolean[][]` (`hasScore`) arrays before running Simulated Annealing so 2-opt iterations allocate zero objects.
