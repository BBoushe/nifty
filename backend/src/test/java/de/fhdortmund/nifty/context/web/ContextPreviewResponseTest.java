package de.fhdortmund.nifty.context.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.fhdortmund.nifty.context.service.dto.AgentRole;
import de.fhdortmund.nifty.context.service.dto.BuiltPromptContext;
import de.fhdortmund.nifty.context.service.dto.PromptMessage;
import de.fhdortmund.nifty.context.service.dto.SanitizationReport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** REQ-CC-01 (Decision 1.12): the professor preview leak check covers the system prompt and every message. */
class ContextPreviewResponseTest {

    private static BuiltPromptContext context(String system, String... messages) {
        List<PromptMessage> list = new java.util.ArrayList<>();
        for (String m : messages) {
            list.add(new PromptMessage("user", m));
        }
        return new BuiltPromptContext(AgentRole.INTERVIEWER, "interviewer-v1", system, list, "hash",
                new SanitizationReport(false, List.of(), false));
    }

    @Test
    void shouldPassTheLeakCheckForARubricFreePrompt() {
        assertThat(ContextPreviewResponse.from(context("Ask about caching.", "<candidate_answer>ok</candidate_answer>"))
                .rubricLeakCheckPassed()).isTrue();
    }

    @Test
    void shouldFailTheLeakCheckWhenTheSystemPromptHoldsARubricTerm() {
        assertThat(ContextPreviewResponse.from(context("Use the rubric: level 3", "hi")).rubricLeakCheckPassed())
                .isFalse();
    }

    @Test
    void shouldFailTheLeakCheckWhenAnyMessageHoldsARubricTerm() {
        assertThat(ContextPreviewResponse.from(context("Ask about caching.", "fine", "the score is 3 of 4"))
                .rubricLeakCheckPassed()).isFalse();
        assertThat(ContextPreviewResponse.from(context("Ask about caching.", "weights are 0.4"))
                .rubricLeakCheckPassed()).isFalse();
    }
}
