package de.fhdortmund.nifty.assessment.service;

import java.util.Map;
import java.util.UUID;

public record StudentSoftSkillProfile(
        UUID inviteId,
        UUID attemptId,
        Map<String, Double> softSkillScores // already reverse-scored 1.0..5.0 means for:
                                            // "conscientiousness", "agreeableness", "neuroticism", "extraversion", "openness"
                                            // null when bfi10Enabled == false
) {
}
