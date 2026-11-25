package de.fhdortmund.nifty.studio.service.view;

import java.util.List;
import java.util.UUID;

public record EvaluatorQuestion(
        UUID coreQuestionId,
        String text,
        List<UUID> skillIds
) {
}
