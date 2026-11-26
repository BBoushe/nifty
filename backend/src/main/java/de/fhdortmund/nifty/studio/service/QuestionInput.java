package de.fhdortmund.nifty.studio.service;

import java.math.BigDecimal;
import java.util.List;

/** Weighted core question targeting one or more defined skills by name (REQ-IS-05, Decision 2.22). */
public record QuestionInput(
        Integer position,
        String text,
        BigDecimal weight,
        BigDecimal confidenceThreshold,
        Integer minTimeSec,
        Integer maxTimeSec,
        Integer followUpLimit,
        List<String> targetSkillNames
) {
}
