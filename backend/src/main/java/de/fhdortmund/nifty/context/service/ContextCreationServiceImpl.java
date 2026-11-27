package de.fhdortmund.nifty.context.service;

import de.fhdortmund.nifty.audit.service.AuditActorRole;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.context.service.dto.BuiltPromptContext;
import de.fhdortmund.nifty.context.service.dto.InterviewerContextInput;
import de.fhdortmund.nifty.context.service.dto.JudgeContextInput;
import de.fhdortmund.nifty.context.service.dto.SanitizationReport;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Sole prompt boundary (Decision 1.18). Stateless, so there is no {@code @Transactional}: AuditService owns the
 * transaction of its own insert.
 */
@Service
class ContextCreationServiceImpl implements ContextCreationService {

    private static final String ACTOR = "context";
    private static final String AUDIT_ACTION = "PROMPT_INJECTION_DETECTED";

    // Builders hold no state, so one instance per role is safe to share between threads (REQ-CC-09).
    private final PromptContextBuilder<InterviewerContextInput> interviewer = new InterviewerContextBuilder();
    private final PromptContextBuilder<JudgeContextInput> judge = new JudgeContextBuilder();
    private final AuditService audit;

    ContextCreationServiceImpl(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public BuiltPromptContext buildInterviewerContext(InterviewerContextInput input) {
        return interviewer.build(input);
    }

    @Override
    public BuiltPromptContext buildJudgeContext(JudgeContextInput input) {
        return judge.build(input);
    }

    // Decision 2.24: the only place that audits. build* only reports, so one answer writes one row, not one per turn.
    @Override
    public SanitizationReport inspectCandidateInput(UUID attemptId, String rawCandidateText) {
        SanitizationReport report = PromptSanitizer.sanitize(rawCandidateText).report();
        if (report.injectionSuspected()) {
            audit.record(ACTOR, AuditActorRole.SYSTEM, "ATTEMPT", attemptId, AUDIT_ACTION, null,
                    auditValue(rawCandidateText, report.matchedRuleIds()), null);
        }
        return report;
    }

    // Hash and rule IDs only. The raw candidate text never enters the audit log (no PII).
    private static String auditValue(String rawCandidateText, List<String> ruleIds) {
        return "promptSha256=" + PromptHasher.sha256(rawCandidateText) + ";matchedRuleIds=" + String.join(",", ruleIds);
    }
}
