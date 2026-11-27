package de.fhdortmund.nifty.context.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.fhdortmund.nifty.context.service.dto.CoreQuestionPromptView;
import de.fhdortmund.nifty.context.service.dto.InterviewerContextInput;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** REQ-CC-02, REQ-CC-10, Decision 2.32: wording of the interviewer system prompt. */
class PromptTemplatesTest {

    private static String prompt(int followUpLimit, int followUpsUsed) {
        CoreQuestionPromptView q = new CoreQuestionPromptView(UUID.randomUUID(), "Explain a merge conflict.",
                List.of("Git"), followUpLimit, followUpsUsed);
        return PromptTemplates.interviewerSystem(new InterviewerContextInput(UUID.randomUUID(), "v1", "vision",
                List.of("Git"), q, 2, List.of()));
    }

    @Test
    void promptAlwaysForbidsInventedQuestions() {
        assertThat(prompt(2, 0)).contains("Do not invent new questions").doesNotContain("You may invent");
        assertThat(prompt(0, 0)).contains("Do not invent new questions");
    }

    @Test
    void followUpLimitZeroTellsTheInterviewerToAskTheCoreQuestionNow() {
        assertThat(prompt(0, 0)).contains("Ask this question now. Do not ask a follow-up after the answer.")
                .doesNotContain("the next question comes next");
    }
}
