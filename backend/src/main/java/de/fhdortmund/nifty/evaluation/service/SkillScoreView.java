package de.fhdortmund.nifty.evaluation.service;

import java.math.BigDecimal;
import java.util.UUID;

public record SkillScoreView(
        UUID skillId,
        String skillName,
        ScoreStatus status,
        BigDecimal score                   // 0.00..4.00 or null when "N/A"
) {
}
