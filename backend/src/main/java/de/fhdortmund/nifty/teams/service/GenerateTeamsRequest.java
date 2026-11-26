package de.fhdortmund.nifty.teams.service;

import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.util.Map;

/**
 * Run-time overrides for one team generation (REQ-TB-05, REQ-TB-08, REQ-TB-10). Every field is optional: a missing
 * field takes the value of the assessment configuration ({@code StudioQueryService.teamView}).
 * {@code traitWeights} (keys: the five Big Five names) replaces the soft preset weights for this run.
 */
public record GenerateTeamsRequest(
        @Min(1) Integer numTeams,
        GroupingMode groupingMode,
        @DecimalMin("0.0") @DecimalMax("1.0") Double alpha,
        @DecimalMin("0.0") @DecimalMax("1.0") Double combinedHardWeight,
        Long seed,
        Map<String, Double> traitWeights) {

    public static GenerateTeamsRequest defaults() {
        return new GenerateTeamsRequest(null, null, null, null, null, null);
    }
}
