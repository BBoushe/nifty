package de.fhdortmund.nifty.assessment.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** The five BFI-10 trait means (1.0..5.0), already reverse-scored (Decision 4.3). */
record Bfi10Traits(
        BigDecimal conscientiousness,
        BigDecimal agreeableness,
        BigDecimal neuroticism,
        BigDecimal extraversion,
        BigDecimal openness
) {
    /** Keys of {@link StudentSoftSkillProfile#softSkillScores()}. */
    Map<String, Double> asMap() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("conscientiousness", conscientiousness.doubleValue());
        map.put("agreeableness", agreeableness.doubleValue());
        map.put("neuroticism", neuroticism.doubleValue());
        map.put("extraversion", extraversion.doubleValue());
        map.put("openness", openness.doubleValue());
        return map;
    }
}
