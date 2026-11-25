package de.fhdortmund.nifty.studio.service.view;

import java.util.List;
import java.util.UUID;

public record InterviewerQuestion(
        UUID coreQuestionId,
        int position,
        String text,
        int minTimeSec,
        int maxTimeSec,
        int followUpLimit,                 // 0..3
        List<UUID> targetSkillIds,
        List<String> targetSkillNames
) {
}
