package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pure, zero-Stream validator that collects every configuration error at once and throws
 * {@link FieldValidationException} (400 RFC 9457 ProblemDetail, REQ-IS-06, REQ-IS-11, Decision 2.23).
 */
@Component
class ConfigValidator {

    void validateOrThrow(CreateAssessmentRequest request) {
        List<FieldErrorDetail> errors = validate(request);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    List<FieldErrorDetail> validate(CreateAssessmentRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (request == null) {
            errors.add(new FieldErrorDetail("request", "must not be null", "REQUIRED"));
            return errors;
        }

        SettingsInput settings = request.settings();
        List<SkillInput> skills = request.skills() == null ? List.of() : request.skills();
        List<QuestionInput> questions = request.questions() == null ? List.of() : request.questions();

        SettingsRules.validate(settings, errors);

        // Decision 2.23 / REQ-IS-11: allow 0 skills and 0 core questions ONLY for SOFT_SKILLS + bfi10Enabled.
        boolean softSkillsOnlyExempt = settings != null
                && settings.groupingMode() == GroupingMode.SOFT_SKILLS
                && Boolean.TRUE.equals(settings.bfi10Enabled());

        if (!softSkillsOnlyExempt) {
            if (skills.isEmpty()) {
                errors.add(new FieldErrorDetail("skills", "at least one skill is required", "EMPTY_CONFIG"));
            }
            if (questions.isEmpty()) {
                errors.add(new FieldErrorDetail("questions", "at least one core question is required", "EMPTY_CONFIG"));
            }
        }

        Map<String, Integer> definedSkills = SkillRules.validate(skills, errors);
        QuestionRules.validate(questions, definedSkills, errors);
        return errors;
    }
}
