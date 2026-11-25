package de.fhdortmund.nifty.evaluation.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CandidateHardSkillProfile(
        UUID inviteId,
        UUID attemptId,
        ReleaseStatus releaseStatus,
        BigDecimal fitScore,               // 0.00..4.00 or null
        BigDecimal coverage,               // 0.0000..1.0000
        List<SkillScoreView> skillScores
) {
}
