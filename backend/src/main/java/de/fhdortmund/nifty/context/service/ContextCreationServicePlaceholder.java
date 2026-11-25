package de.fhdortmund.nifty.context.service;

import de.fhdortmund.nifty.context.service.dto.BuiltPromptContext;
import de.fhdortmund.nifty.context.service.dto.InterviewerContextInput;
import de.fhdortmund.nifty.context.service.dto.JudgeContextInput;
import de.fhdortmund.nifty.context.service.dto.SanitizationReport;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Compile-only placeholder. context-creation deletes this class when the real bean lands. */
@Service
class ContextCreationServicePlaceholder implements ContextCreationService {

    private static final String MESSAGE = "ContextCreationService is not implemented yet: owner context-creation";

    @Override
    public BuiltPromptContext buildInterviewerContext(InterviewerContextInput input) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public BuiltPromptContext buildJudgeContext(JudgeContextInput input) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public SanitizationReport inspectCandidateInput(UUID attemptId, String rawCandidateText) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
