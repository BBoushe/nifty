package de.fhdortmund.nifty.studio.service.view;

import java.util.List;
import java.util.UUID;

public record EvaluatorView(
        UUID configId,
        List<EvaluatorSkill> skills,
        List<EvaluatorQuestion> questions
) {
}
