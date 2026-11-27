package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Full professor-only detail view including hidden 5-level rubrics (REQ-IS-10, Decision 1.19). */
public record AssessmentDetailResponse(
        UUID configId,
        String title,
        String vision,
        BigDecimal minCoverage,
        int maxAttempts,
        boolean bfi10Enabled,
        int teamSize,
        GroupingMode groupingMode,
        SoftPreset softPreset,
        BigDecimal combinedHardWeight,
        BigDecimal alpha,
        Map<String, BigDecimal> constraintWeights,
        String status,
        int version,
        Instant createdAt,
        List<SkillDetail> skills,
        List<QuestionDetail> questions
) {

    public record SkillDetail(UUID skillId, String name, List<String> rubricLevels) {
    }

    public record QuestionDetail(
            UUID coreQuestionId,
            int position,
            String text,
            BigDecimal weight,
            BigDecimal confidenceThreshold,
            int minTimeSec,
            int maxTimeSec,
            int followUpLimit,
            List<UUID> targetSkillIds,
            List<String> targetSkillNames
    ) {
    }
}
