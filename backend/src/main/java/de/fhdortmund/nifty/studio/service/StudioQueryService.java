package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import de.fhdortmund.nifty.studio.service.view.InterviewerView;
import de.fhdortmund.nifty.studio.service.view.ScoringView;
import de.fhdortmund.nifty.studio.service.view.TeamView;
import java.util.UUID;

/** Cross-slice contract (00-parallel-waves-and-contracts.md §3.4). Owner: studio-assessment-creation. */
public interface StudioQueryService {

    boolean exists(UUID configId);

    boolean ownedBy(UUID configId, UUID professorId);

    InterviewerView interviewerView(UUID configId); // Throws DomainException(404) if missing

    EvaluatorView evaluatorView(UUID configId);

    ScoringView scoringView(UUID configId);

    TeamView teamView(UUID configId);
}
