package de.fhdortmund.nifty.studio.service.view;

import java.util.List;
import java.util.UUID;

/** STRICT RUBRIC GUARD: this view and InterviewerQuestion NEVER carry rubric, weight, threshold or coverage. */
public record InterviewerView(
        UUID configId,
        String configVersion,              // "v1" in records; the DB stores version INT 1
        String vision,
        int maxAttempts,
        boolean inventedQuestionsEnabled,
        boolean bfi10Enabled,
        GroupingMode groupingMode,
        List<InterviewerQuestion> questions // ordered by position (empty only for SOFT_SKILLS-only)
) {
}
