package de.fhdortmund.nifty.context.service.dto;

import java.util.List;
import java.util.UUID;

public record CoreQuestionPromptView(
        UUID coreQuestionId,
        String questionText,
        List<String> targetSkillNames,
        int followUpLimit,
        int followUpsUsed
) {
}
