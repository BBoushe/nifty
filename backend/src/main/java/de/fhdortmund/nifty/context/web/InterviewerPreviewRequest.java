package de.fhdortmund.nifty.context.web;

import de.fhdortmund.nifty.context.service.dto.CoreQuestionPromptView;
import de.fhdortmund.nifty.context.service.dto.InterviewerContextInput;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Dry-run input of the professor preview: one question, no conversation. Same limits as the studio (REQ-CC-08). */
public record InterviewerPreviewRequest(
        @NotBlank String vision,
        @NotEmpty List<@NotBlank String> targetSkillNames,
        @NotBlank String questionText,
        // Boxed on purpose: Jackson 3 rejects a missing primitive with an unnamed 400. A boxed @NotNull names the field.
        @NotNull @Min(0) @Max(3) Integer followUpLimit,
        Boolean inventedQuestionsEnabled // absent means false
) {

    private static final UUID PREVIEW_ID = new UUID(0, 0);

    // shortcut: previews show the opening prompt only (no prior turns, no later core questions). Add both
    // fields when professors ask to preview mid-conversation prompts.
    InterviewerContextInput toInput() {
        CoreQuestionPromptView question =
                new CoreQuestionPromptView(PREVIEW_ID, questionText, targetSkillNames, followUpLimit, 0);
        return new InterviewerContextInput(PREVIEW_ID, "preview", vision, targetSkillNames, question, 0,
                Boolean.TRUE.equals(inventedQuestionsEnabled), List.of());
    }
}
