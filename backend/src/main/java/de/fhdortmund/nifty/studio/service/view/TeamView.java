package de.fhdortmund.nifty.studio.service.view;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record TeamView(
        UUID configId,
        int teamSize,
        GroupingMode groupingMode,
        SoftPreset softPreset,
        BigDecimal combinedHardWeight,     // 0.0000..1.0000
        BigDecimal alpha,                  // 0.0000..1.0000
        Map<String, BigDecimal> constraintWeights,
        boolean bfi10Enabled
) {
}
