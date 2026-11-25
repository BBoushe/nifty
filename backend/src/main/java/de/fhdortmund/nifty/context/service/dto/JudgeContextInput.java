package de.fhdortmund.nifty.context.service.dto;

import java.util.List;
import java.util.UUID;

/** Agent 2 per-(entryId, skillId) fresh-context judge input (Decision 1.19, 2.20). */
public record JudgeContextInput(
        UUID attemptId,
        UUID entryId,
        UUID skillId,
        String skillName,
        List<String> rubricLevels,         // exact size 5, index 0..4 ascending
        String questionText,
        String answerText,                 // non-blank only; blank entries skipped upstream
        boolean timedOut
) {
}
