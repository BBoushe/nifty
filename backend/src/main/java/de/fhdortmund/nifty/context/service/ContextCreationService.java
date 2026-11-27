package de.fhdortmund.nifty.context.service;

import de.fhdortmund.nifty.context.service.dto.BuiltPromptContext;
import de.fhdortmund.nifty.context.service.dto.InterviewerContextInput;
import de.fhdortmund.nifty.context.service.dto.JudgeContextInput;
import de.fhdortmund.nifty.context.service.dto.SanitizationReport;
import java.util.UUID;

/** Cross-slice contract (00-parallel-waves-and-contracts.md §3.5). Owner: context-creation. */
public interface ContextCreationService {

    BuiltPromptContext buildInterviewerContext(InterviewerContextInput input);

    BuiltPromptContext buildJudgeContext(JudgeContextInput input);

    // Only context writes the PROMPT_INJECTION_DETECTED audit row (entityId = attemptId). assessment never audits it.
    SanitizationReport inspectCandidateInput(UUID attemptId, String rawCandidateText);
}
