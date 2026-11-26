package de.fhdortmund.nifty.context.service;

import de.fhdortmund.nifty.context.service.dto.AgentRole;
import de.fhdortmund.nifty.context.service.dto.BuiltPromptContext;
import de.fhdortmund.nifty.context.service.dto.DialogueTurnView;
import de.fhdortmund.nifty.context.service.dto.InterviewerContextInput;
import de.fhdortmund.nifty.context.service.dto.PromptMessage;
import de.fhdortmund.nifty.context.service.dto.SanitizationReport;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 1 prompt (Decision 1.12, 1.21). The input type has no rubric field, so no rubric text can reach this
 * builder. The caller passes only the turns of the current attempt, which keeps a retake context empty.
 */
final class InterviewerContextBuilder implements PromptContextBuilder<InterviewerContextInput> {

    static final String TEMPLATE_VERSION = "interviewer-v1";
    private static final String INTERVIEWER = "interviewer";
    private static final String CANDIDATE = "candidate";

    @Override
    public AgentRole role() {
        return AgentRole.INTERVIEWER;
    }

    @Override
    public BuiltPromptContext build(InterviewerContextInput in) {
        String system = PromptTemplates.interviewerSystem(in);
        List<PromptMessage> messages = new ArrayList<>(in.priorTurns().size());
        SanitizationReport report = PromptSanitizer.CLEAN;
        for (int i = 0; i < in.priorTurns().size(); i++) {
            DialogueTurnView turn = in.priorTurns().get(i);
            if (CANDIDATE.equals(turn.author())) {
                PromptSanitizer.Sanitized safe = PromptSanitizer.sanitize(turn.content());
                report = PromptSanitizer.merge(report, safe.report());
                messages.add(new PromptMessage("user", PromptTemplates.candidateAnswer(safe.text())));
            } else if (INTERVIEWER.equals(turn.author())) {
                messages.add(new PromptMessage("assistant", turn.content()));
            } else {
                throw new IllegalArgumentException("Unknown dialogue author: " + turn.author());
            }
        }
        return new BuiltPromptContext(AgentRole.INTERVIEWER, TEMPLATE_VERSION, system, messages,
                PromptHasher.sha256(TEMPLATE_VERSION, system, messages), report);
    }
}
