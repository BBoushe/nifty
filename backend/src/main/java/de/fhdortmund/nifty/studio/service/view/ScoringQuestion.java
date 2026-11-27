package de.fhdortmund.nifty.studio.service.view;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ScoringQuestion(
        UUID coreQuestionId,
        BigDecimal weight,                 // > 0 (Decision 2.22: no SKILL.weight)
        BigDecimal confidenceThreshold,    // 0.0000..1.0000
        List<UUID> skillIds
) {
}
