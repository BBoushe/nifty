package de.fhdortmund.nifty.studio.service;

import java.util.List;

/** One-shot assessment creation payload shared by controller and service (Decision 2.21). */
public record CreateAssessmentRequest(
        SettingsInput settings,
        List<SkillInput> skills,
        List<QuestionInput> questions
) {
}
