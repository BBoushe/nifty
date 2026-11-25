package de.fhdortmund.nifty.studio.service.view;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ScoringView(
        UUID configId,
        BigDecimal minCoverage,            // 0.0000..1.0000
        List<ScoringQuestion> questions
) {
}
