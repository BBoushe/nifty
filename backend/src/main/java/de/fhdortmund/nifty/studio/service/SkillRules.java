package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure, zero-Stream validator for skill definitions and 5-level hidden rubrics (REQ-IS-05, REQ-IS-06). */
final class SkillRules {

    static final int RUBRIC_LEVEL_COUNT = 5;

    private SkillRules() {
    }

    /**
     * Validates all skills and returns a map of normalized skill name -> original list index
     * for downstream cross-checking in {@link QuestionRules}.
     */
    static Map<String, Integer> validate(List<SkillInput> skills, List<FieldErrorDetail> errors) {
        Map<String, Integer> defined = new LinkedHashMap<>();
        if (skills == null) {
            return defined;
        }
        for (int i = 0; i < skills.size(); i++) {
            SkillInput skill = skills.get(i);
            String prefix = "skills[" + i + "]";
            if (skill == null) {
                errors.add(new FieldErrorDetail(prefix, "must not be null", "REQUIRED"));
                continue;
            }
            if (skill.name() == null || skill.name().isBlank()) {
                errors.add(new FieldErrorDetail(prefix + ".name", "must not be blank", "REQUIRED"));
            } else {
                String normalized = normalizeSkillName(skill.name());
                if (defined.containsKey(normalized)) {
                    errors.add(new FieldErrorDetail(prefix + ".name", "duplicate skill name", "DUPLICATE_SKILL"));
                } else {
                    defined.put(normalized, i);
                }
            }
            if (!hasFiveNonBlankLevels(skill.rubricLevels())) {
                errors.add(new FieldErrorDetail(
                        prefix + ".rubricLevels",
                        "must contain exactly 5 non-blank rubric levels (0..4)",
                        "RUBRIC_INCOMPLETE"));
            }
        }
        return defined;
    }

    static String normalizeSkillName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasFiveNonBlankLevels(List<String> levels) {
        if (levels == null || levels.size() != RUBRIC_LEVEL_COUNT) {
            return false;
        }
        for (int i = 0; i < RUBRIC_LEVEL_COUNT; i++) {
            String level = levels.get(i);
            if (level == null || level.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
