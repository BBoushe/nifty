package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure, zero-Stream validator for core questions, time ranges, skill bindings, and gap-free positions. */
final class QuestionRules {

    private static final int MAX_FOLLOW_UP_LIMIT = 3;

    private QuestionRules() {
    }

    static void validate(
            List<QuestionInput> questions,
            Map<String, Integer> definedSkills,
            List<FieldErrorDetail> errors) {
        Set<String> usedSkills = new HashSet<>();
        if (questions != null && !questions.isEmpty()) {
            Set<Integer> positions = new HashSet<>();
            boolean positionsValidRange = true;
            for (int i = 0; i < questions.size(); i++) {
                QuestionInput q = questions.get(i);
                String prefix = "questions[" + i + "]";
                if (q == null) {
                    errors.add(new FieldErrorDetail(prefix, "must not be null", "REQUIRED"));
                    positionsValidRange = false;
                    continue;
                }
                if (q.position() == null) {
                    errors.add(new FieldErrorDetail(prefix + ".position", "must not be null", "REQUIRED"));
                    positionsValidRange = false;
                } else if (q.position() < 1) {
                    errors.add(new FieldErrorDetail(prefix + ".position", "must be at least 1", "OUT_OF_RANGE"));
                    positionsValidRange = false;
                } else {
                    positions.add(q.position());
                }

                SettingsRules.requireText(q.text(), prefix + ".text", errors);
                validateWeight(q.weight(), prefix + ".weight", errors);
                SettingsRules.requireUnitInterval(q.confidenceThreshold(), prefix + ".confidenceThreshold", errors);
                validateTimes(q.minTimeSec(), q.maxTimeSec(), prefix, errors);
                validateFollowUpLimit(q.followUpLimit(), prefix + ".followUpLimit", errors);
                validateTargetSkills(q.targetSkillNames(), prefix + ".targetSkillNames", definedSkills, usedSkills,
                        errors);
            }
            if (positionsValidRange && !hasGapFreePositions(positions, questions.size())) {
                errors.add(new FieldErrorDetail(
                        "questions",
                        "question positions must be gap-free from 1 to " + questions.size(),
                        "ORDER_GAP"));
            }
        }

        for (Map.Entry<String, Integer> entry : definedSkills.entrySet()) {
            if (!usedSkills.contains(entry.getKey())) {
                errors.add(new FieldErrorDetail(
                        "skills[" + entry.getValue() + "].name",
                        "skill is not targeted by any core question",
                        "UNUSED_SKILL"));
            }
        }
    }

    private static void validateWeight(BigDecimal weight, String field, List<FieldErrorDetail> errors) {
        if (weight == null) {
            errors.add(new FieldErrorDetail(field, "must not be null", "REQUIRED"));
        } else if (weight.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new FieldErrorDetail(field, "must be greater than 0", "OUT_OF_RANGE"));
        }
    }

    private static void validateTimes(
            Integer minTimeSec, Integer maxTimeSec, String prefix, List<FieldErrorDetail> errors) {
        boolean minOk = true;
        boolean maxOk = true;
        if (minTimeSec == null) {
            errors.add(new FieldErrorDetail(prefix + ".minTimeSec", "must not be null", "REQUIRED"));
            minOk = false;
        } else if (minTimeSec <= 0) {
            errors.add(new FieldErrorDetail(prefix + ".minTimeSec", "must be greater than 0", "OUT_OF_RANGE"));
            minOk = false;
        }
        if (maxTimeSec == null) {
            errors.add(new FieldErrorDetail(prefix + ".maxTimeSec", "must not be null", "REQUIRED"));
            maxOk = false;
        } else if (maxTimeSec <= 0) {
            errors.add(new FieldErrorDetail(prefix + ".maxTimeSec", "must be greater than 0", "OUT_OF_RANGE"));
            maxOk = false;
        }
        if (minOk && maxOk && minTimeSec >= maxTimeSec) {
            errors.add(new FieldErrorDetail(
                    prefix + ".maxTimeSec", "must be greater than minTimeSec", "TIME_RANGE"));
        }
    }

    private static void validateFollowUpLimit(Integer limit, String field, List<FieldErrorDetail> errors) {
        if (limit == null) {
            errors.add(new FieldErrorDetail(field, "must not be null", "REQUIRED"));
        } else if (limit < 0 || limit > MAX_FOLLOW_UP_LIMIT) {
            errors.add(new FieldErrorDetail(field, "must be between 0 and 3", "OUT_OF_RANGE"));
        }
    }

    private static void validateTargetSkills(
            List<String> targetSkillNames,
            String field,
            Map<String, Integer> definedSkills,
            Set<String> usedSkills,
            List<FieldErrorDetail> errors) {
        if (targetSkillNames == null || targetSkillNames.isEmpty()) {
            errors.add(new FieldErrorDetail(field, "must target at least one defined skill", "NO_SKILL"));
            return;
        }
        for (int i = 0; i < targetSkillNames.size(); i++) {
            String raw = targetSkillNames.get(i);
            if (raw == null || raw.isBlank()) {
                errors.add(new FieldErrorDetail(field, "target skill name must not be blank", "NO_SKILL"));
                continue;
            }
            String normalized = SkillRules.normalizeSkillName(raw);
            if (!definedSkills.containsKey(normalized)) {
                errors.add(new FieldErrorDetail(field, "references unknown skill: " + raw.trim(), "NO_SKILL"));
            } else {
                usedSkills.add(normalized);
            }
        }
    }

    private static boolean hasGapFreePositions(Set<Integer> positions, int count) {
        if (positions.size() != count) {
            return false;
        }
        for (int expected = 1; expected <= count; expected++) {
            if (!positions.contains(expected)) {
                return false;
            }
        }
        return true;
    }
}
