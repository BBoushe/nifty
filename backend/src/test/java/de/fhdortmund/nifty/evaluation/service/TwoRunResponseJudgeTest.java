package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.context.service.ContextCreationService;
import de.fhdortmund.nifty.context.service.dto.AgentRole;
import de.fhdortmund.nifty.context.service.dto.BuiltPromptContext;
import de.fhdortmund.nifty.context.service.dto.JudgeContextInput;
import de.fhdortmund.nifty.context.service.dto.PromptMessage;
import de.fhdortmund.nifty.context.service.dto.SanitizationReport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-EP-05, REQ-EP-08 / Decisions 1.18, 2.25:
 * Two independent fresh-context ChatClient calls per (entryId, skillId) via ContextCreationService,
 * strict JSON schema & level 0..4 validation, rejecting rankings/totals/unknown keys.
 */
class TwoRunResponseJudgeTest {

    private static final List<String> RUBRIC = List.of("None", "Basic", "Developing", "Proficient", "Expert");

    @Test
    void makesTwoFreshContextCallsAndParsesValidJudgeJson() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        ContextCreationService contextService = mock(ContextCreationService.class);
        JudgeContextInput input = sampleInput(false);

        when(contextService.buildJudgeContext(input)).thenReturn(sampleBuiltContext());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("{\"level\":3,\"verbatimQuote\":\"I would add an index on email\",\"rationale\":\"Names fix\",\"suggestion\":\"Mention trade-off\"}"))
                .thenReturn(chatResponse("{\"level\":3,\"verbatimQuote\":\"I would add an index on email\",\"rationale\":\"Correct index choice\"}"));

        TwoRunResponseJudge judge = new TwoRunResponseJudge(
                ChatClient.builder(chatModel), contextService, JsonMapper.builder().build());

        TwoRunResponseJudge.TwoRunResult result = judge.judge(input);

        verify(contextService, times(1)).buildJudgeContext(input);
        verify(chatModel, times(2)).call(any(Prompt.class));
        assertThat(result.templateVersion()).isEqualTo("evaluator-judge-v1");
        assertThat(result.run1().valid()).isTrue();
        assertThat(result.run1().level()).isEqualTo(3);
        assertThat(result.run1().verbatimQuote()).isEqualTo("I would add an index on email");
        assertThat(result.run2().valid()).isTrue();
        assertThat(result.run2().level()).isEqualTo(3);
    }

    @Test
    void discardsOutOfRangeLevelOrExtraRankingFieldWithoutThrowing() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        ContextCreationService contextService = mock(ContextCreationService.class);
        JudgeContextInput input = sampleInput(true);

        when(contextService.buildJudgeContext(input)).thenReturn(sampleBuiltContext());
        // REQ-EP-08 scenario: {"level": 5, "rank": 1} -> discarded; second run has extra "confidence" -> discarded
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("{\"level\":5,\"rank\":1}"))
                .thenReturn(chatResponse("{\"level\":2,\"verbatimQuote\":\"valid quote text\",\"rationale\":\"ok\",\"confidence\":0.99}"));

        TwoRunResponseJudge judge = new TwoRunResponseJudge(
                ChatClient.builder(chatModel), contextService, JsonMapper.builder().build());

        TwoRunResponseJudge.TwoRunResult result = judge.judge(input);

        assertThat(result.run1().valid()).isFalse();
        assertThat(result.run1().level()).isNull();
        assertThat(result.run2().valid()).isFalse();
        assertThat(result.run2().level()).isNull();
    }

    private static JudgeContextInput sampleInput(boolean timedOut) {
        return new JudgeContextInput(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SQL",
                RUBRIC, "How do you speed up email lookup?", "I would add an index on email.", timedOut);
    }

    private static BuiltPromptContext sampleBuiltContext() {
        return new BuiltPromptContext(
                AgentRole.EVALUATOR_JUDGE,
                "evaluator-judge-v1",
                "Evaluate strictly with verbatim quotes.",
                List.of(new PromptMessage("user", "<transcript_entry>I would add an index on email.</transcript_entry>")),
                "a".repeat(64),
                new SanitizationReport(false, List.of(), false));
    }

    private static ChatResponse chatResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
