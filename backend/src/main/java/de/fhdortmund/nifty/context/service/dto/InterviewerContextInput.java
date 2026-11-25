package de.fhdortmund.nifty.context.service.dto;

import java.util.List;
import java.util.UUID;

/** Compile-time rubric-blind Agent 1 input (ZERO rubric, weight, threshold, score or bfi fields; Decision 1.12). */
public record InterviewerContextInput(
        UUID attemptId,
        String configVersion,
        String vision,
        List<String> targetSkillNames,
        CoreQuestionPromptView activeQuestion,
        int coreQuestionsRemaining,
        boolean inventedQuestionsEnabled,
        List<DialogueTurnView> priorTurns
) {
}
