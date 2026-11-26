package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Pure, zero-Stream validator for assessment settings (REQ-IS-05, REQ-IS-06, REQ-TB-10, Decision 2.23). */
final class SettingsRules {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal WEIGHT_SUM_TOLERANCE = new BigDecimal("0.0001");
    private static final List<String> BIG_FIVE_KEYS = List.of(
            "conscientiousness", "agreeableness", "neuroticism", "extraversion", "openness");

    private SettingsRules() {
    }

    static void validate(SettingsInput settings, List<FieldErrorDetail> errors) {
        if (settings == null) {
            errors.add(new FieldErrorDetail("settings", "must not be null", "REQUIRED"));
            return;
        }
        requireText(settings.title(), "settings.title", errors);
        requireText(settings.vision(), "settings.vision", errors);
        requireUnitInterval(settings.minCoverage(), "settings.minCoverage", errors);
        requireMinInt(settings.maxAttempts(), 1, "settings.maxAttempts", errors);
        requireMinInt(settings.teamSize(), 2, "settings.teamSize", errors);
        requireUnitInterval(settings.combinedHardWeight(), "settings.combinedHardWeight", errors);
        requireUnitInterval(settings.alpha(), "settings.alpha", errors);

        if (settings.groupingMode() == null) {
            errors.add(new FieldErrorDetail("settings.groupingMode", "must not be null", "REQUIRED"));
        } else if ((settings.groupingMode() == GroupingMode.SOFT_SKILLS
                || settings.groupingMode() == GroupingMode.COMBINED)
                && !Boolean.TRUE.equals(settings.bfi10Enabled())) {
            errors.add(new FieldErrorDetail(
                    "settings.bfi10Enabled",
                    "must be enabled for SOFT_SKILLS or COMBINED grouping mode",
                    "BAD_WEIGHTS"));
        }

        if (settings.softPreset() == null) {
            errors.add(new FieldErrorDetail("settings.softPreset", "must not be null", "REQUIRED"));
        }

        validateConstraintWeights(settings.softPreset(), settings.constraintWeights(), errors);
    }

    static void requireUnitInterval(BigDecimal value, String field, List<FieldErrorDetail> errors) {
        if (value == null) {
            errors.add(new FieldErrorDetail(field, "must not be null", "REQUIRED"));
            return;
        }
        if (value.compareTo(ZERO) < 0 || value.compareTo(ONE) > 0) {
            errors.add(new FieldErrorDetail(field, "must be between 0 and 1", "OUT_OF_RANGE"));
        }
    }

    static void requireText(String value, String field, List<FieldErrorDetail> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new FieldErrorDetail(field, "must not be blank", "REQUIRED"));
        }
    }

    private static void requireMinInt(Integer value, int min, String field, List<FieldErrorDetail> errors) {
        if (value == null) {
            errors.add(new FieldErrorDetail(field, "must not be null", "REQUIRED"));
            return;
        }
        if (value < min) {
            errors.add(new FieldErrorDetail(field, "must be at least " + min, "OUT_OF_RANGE"));
        }
    }

    private static void validateConstraintWeights(
            SoftPreset preset, Map<String, BigDecimal> weights, List<FieldErrorDetail> errors) {
        if (weights != null) {
            for (Map.Entry<String, BigDecimal> entry : weights.entrySet()) {
                String key = entry.getKey();
                BigDecimal weight = entry.getValue();
                if (key == null || key.isBlank() || weight == null
                        || weight.compareTo(ZERO) < 0 || weight.compareTo(ONE) > 0) {
                    errors.add(new FieldErrorDetail(
                            "settings.constraintWeights",
                            "every constraint weight must have a non-blank key and a value in 0..1",
                            "BAD_WEIGHTS"));
                    return;
                }
            }
        }
        if (preset == SoftPreset.CUSTOM) {
            if (weights == null) {
                errors.add(new FieldErrorDetail(
                        "settings.constraintWeights",
                        "CUSTOM soft preset requires all 5 Big Five trait weights summing to 1.0",
                        "BAD_WEIGHTS"));
                return;
            }
            BigDecimal sum = ZERO;
            for (String trait : BIG_FIVE_KEYS) {
                BigDecimal value = weights.get(trait);
                if (value == null) {
                    errors.add(new FieldErrorDetail(
                            "settings.constraintWeights",
                            "CUSTOM soft preset requires all 5 Big Five trait weights summing to 1.0",
                            "BAD_WEIGHTS"));
                    return;
                }
                sum = sum.add(value);
            }
            if (sum.subtract(ONE).abs().compareTo(WEIGHT_SUM_TOLERANCE) > 0) {
                errors.add(new FieldErrorDetail(
                        "settings.constraintWeights",
                        "CUSTOM soft preset trait weights must sum to 1.0",
                        "BAD_WEIGHTS"));
            }
        }
    }
}
