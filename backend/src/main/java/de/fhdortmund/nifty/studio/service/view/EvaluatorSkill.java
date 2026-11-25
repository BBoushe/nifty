package de.fhdortmund.nifty.studio.service.view;

import java.util.List;
import java.util.UUID;

public record EvaluatorSkill(
        UUID skillId,
        String name,
        List<String> rubricLevels          // EXACT size 5, index 0..4 ascending (Decision 1.19)
) {
}
