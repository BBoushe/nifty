package de.fhdortmund.nifty.teams.service;

import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A team proposal with backend-computed diagnostics (zero client-side maths). Skill values are on the 0..4 scale,
 * traits on the already reverse-scored 1..5 scale (Decision 4.3); {@code null} means N/A and is never 0.
 * Traits are present for the professor only (REQ-TB-07); every endpoint of this slice is PROFESSOR-only.
 */
public record TeamProposalResponse(
        UUID proposalId,
        UUID configId,
        String status,
        int numTeams,
        GroupingMode groupingMode,
        double alpha,
        double combinedHardWeight,
        long seed,
        Instant createdAt,
        Instant publishedAt,
        Diagnostics diagnostics,
        List<TeamCard> teams,
        List<UUID> pendingInviteIds) { // latest attempt waits for review: shown as waiting, never scored (REQ-TB-11)

    /** {@code naCount} totals the missing hard-skill scores over all teams. */
    public record Diagnostics(double imbalance, double diversity, double cost, int naCount) {
    }

    /** {@code skillNaCounts} counts members without a score per skill; {@code traitAverages} is empty without BFI-10. */
    public record TeamCard(
            int teamNo,
            int size,
            List<TeamMember> members,
            Map<String, Double> skillAverages,
            Map<String, Integer> skillNaCounts,
            Map<String, Double> traitAverages) {
    }

    public record TeamMember(
            UUID inviteId,
            String candidateRef,
            Map<String, Double> skillScores,
            Map<String, Double> traits) {
    }
}
