package de.fhdortmund.nifty.context.service;

import de.fhdortmund.nifty.context.service.dto.AgentRole;
import de.fhdortmund.nifty.context.service.dto.BuiltPromptContext;
import de.fhdortmund.nifty.context.service.dto.JudgeContextInput;
import de.fhdortmund.nifty.context.service.dto.PromptMessage;
import java.util.List;

/**
 * Agent 2 prompt for one (entry, skill) pair, always in a fresh context (Decision 1.19, 1.21). It holds no
 * BFI-10 data and no other entry (REQ-CC-05). The output contract is the JSON of Decision 2.25.
 */
final class JudgeContextBuilder implements PromptContextBuilder<JudgeContextInput> {

    static final String TEMPLATE_VERSION = "evaluator-judge-v1";
    private static final int LEVELS = 5;

    @Override
    public AgentRole role() {
        return AgentRole.EVALUATOR_JUDGE;
    }

    @Override
    public BuiltPromptContext build(JudgeContextInput in) {
        // Decision 2.20: blank entries are skipped upstream. A non-blank timed-out answer is judged like any other.
        if (in.answerText() == null || in.answerText().isBlank()) {
            throw new IllegalArgumentException("answerText must not be blank: skip such entries before judging");
        }
        if (in.rubricLevels() == null || in.rubricLevels().size() != LEVELS) {
            throw new IllegalArgumentException("rubricLevels must hold exactly " + LEVELS + " levels (0..4)");
        }
        PromptSanitizer.Sanitized answer = PromptSanitizer.sanitize(in.answerText());
        String system = PromptTemplates.judgeSystem(in.skillName(), in.rubricLevels());
        List<PromptMessage> messages = List.of(new PromptMessage("user",
                PromptTemplates.judgeUser(in.questionText(), in.entryId(), in.timedOut(), answer.text())));
        return new BuiltPromptContext(AgentRole.EVALUATOR_JUDGE, TEMPLATE_VERSION, system, messages,
                PromptHasher.sha256(TEMPLATE_VERSION, system, messages), answer.report());
    }
}
