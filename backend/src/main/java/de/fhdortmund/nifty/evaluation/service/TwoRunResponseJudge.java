package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.context.service.ContextCreationService;
import de.fhdortmund.nifty.context.service.dto.BuiltPromptContext;
import de.fhdortmund.nifty.context.service.dto.JudgeContextInput;
import de.fhdortmund.nifty.context.service.dto.PromptMessage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-EP-05, REQ-EP-08 / Decisions 1.12, 1.18, 2.25:
 * Runs two independent fresh-context Spring AI ChatClient calls per (entryId, skillId) pair
 * using prompts from {@link ContextCreationService#buildJudgeContext} and strictly validates
 * the returned JSON schema and level range 0..4 in code.
 */
@Service
@Transactional(readOnly = true)
public class TwoRunResponseJudge {

    private static final Set<String> ALLOWED_KEYS = Set.of("level", "verbatimQuote", "rationale", "suggestion");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ChatClient chatClient;
    private final ContextCreationService contextCreationService;
    private final JsonMapper jsonMapper;

    public TwoRunResponseJudge(
            ChatClient.Builder chatClientBuilder,
            ContextCreationService contextCreationService,
            JsonMapper jsonMapper) {
        this.chatClient = chatClientBuilder.build();
        this.contextCreationService = contextCreationService;
        this.jsonMapper = jsonMapper;
    }

    public record ParsedRun(
            boolean valid,
            Integer level,
            String verbatimQuote,
            String rationale,
            String suggestion
    ) {
        static ParsedRun failed() {
            return new ParsedRun(false, null, null, null, null);
        }
    }

    public record TwoRunResult(String templateVersion, ParsedRun run1, ParsedRun run2) {
    }

    public TwoRunResult judge(JudgeContextInput input) {
        BuiltPromptContext context = contextCreationService.buildJudgeContext(input);
        String userContent = joinUserMessages(context.messages());

        // Two independent fresh-context calls with zero shared conversation state (REQ-EP-05).
        ParsedRun run1 = executeSingleRun(context.systemPrompt(), userContent);
        ParsedRun run2 = executeSingleRun(context.systemPrompt(), userContent);
        return new TwoRunResult(context.templateVersion(), run1, run2);
    }

    private ParsedRun executeSingleRun(String systemPrompt, String userContent) {
        String raw = chatClient.prompt()
                .system(systemPrompt)
                .user(userContent)
                .call()
                .content();
        return parseStrict(raw);
    }

    ParsedRun parseStrict(String raw) {
        if (raw == null || raw.isBlank()) {
            return ParsedRun.failed();
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return ParsedRun.failed();
        }
        Map<String, Object> fields;
        try {
            fields = jsonMapper.readValue(trimmed, MAP_TYPE);
        } catch (JacksonException ex) {
            return ParsedRun.failed();
        }
        if (fields == null || fields.isEmpty()) {
            return ParsedRun.failed();
        }
        // REQ-EP-08: reject any unexpected field (e.g. rank, fit, coverage, confidence, total).
        for (String key : fields.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                return ParsedRun.failed();
            }
        }

        Object rawLevel = fields.get("level");
        Integer level = parseStrictLevel(rawLevel);
        if (level == null) {
            return ParsedRun.failed();
        }

        Object rawQuote = fields.get("verbatimQuote");
        Object rawRationale = fields.get("rationale");
        if (!(rawQuote instanceof String quote) || !(rawRationale instanceof String rationale)) {
            return ParsedRun.failed();
        }

        Object rawSuggestion = fields.get("suggestion");
        String suggestion = null;
        if (rawSuggestion != null) {
            if (!(rawSuggestion instanceof String s)) {
                return ParsedRun.failed();
            }
            suggestion = s;
        }
        return new ParsedRun(true, level, quote, rationale, suggestion);
    }

    private static Integer parseStrictLevel(Object raw) {
        if (raw instanceof Integer i) {
            return (i >= 0 && i <= 4) ? i : null;
        }
        if (raw instanceof Long l) {
            return (l >= 0L && l <= 4L) ? l.intValue() : null;
        }
        return null;
    }

    private static String joinUserMessages(List<PromptMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(messages.get(i).content());
        }
        return sb.toString();
    }
}
