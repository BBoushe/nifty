package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import java.time.Instant;
import java.util.UUID;

/** Summary row returned by {@code GET /api/v1/ranking/studio/configs} (REQ-IS-10). */
public record AssessmentSummaryResponse(
        UUID configId,
        String title,
        GroupingMode groupingMode,
        boolean bfi10Enabled,
        int teamSize,
        int skillCount,
        int questionCount,
        String status,
        int version,
        Instant createdAt
) {
}
