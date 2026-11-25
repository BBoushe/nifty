package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import de.fhdortmund.nifty.studio.service.view.InterviewerView;
import de.fhdortmund.nifty.studio.service.view.ScoringView;
import de.fhdortmund.nifty.studio.service.view.TeamView;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Compile-only placeholder. studio-assessment-creation deletes this class when the real bean lands. */
@Service
class StudioQueryServicePlaceholder implements StudioQueryService {

    private static final String MESSAGE =
            "StudioQueryService is not implemented yet: owner studio-assessment-creation";

    @Override
    public boolean exists(UUID configId) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public InterviewerView interviewerView(UUID configId) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public EvaluatorView evaluatorView(UUID configId) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public ScoringView scoringView(UUID configId) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public TeamView teamView(UUID configId) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
