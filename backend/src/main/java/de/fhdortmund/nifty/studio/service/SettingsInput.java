package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import java.math.BigDecimal;
import java.util.Map;

/** Assessment-level settings for one-shot immutable creation (REQ-IS-05, Decision 2.23). */
public record SettingsInput(
        String title,
        String vision,
        BigDecimal minCoverage,
        Integer maxAttempts,
        Boolean inventedQuestions,
        Boolean bfi10Enabled,
        Integer teamSize,
        GroupingMode groupingMode,
        SoftPreset softPreset,
        BigDecimal combinedHardWeight,
        BigDecimal alpha,
        Map<String, BigDecimal> constraintWeights
) {
}
