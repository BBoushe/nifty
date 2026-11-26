package de.fhdortmund.nifty.studio.service;

import java.util.List;

/** Skill with 5 ascending rubric descriptions (0..4) and no weight (Decision 2.22). */
public record SkillInput(
        String name,
        List<String> rubricLevels
) {
}
