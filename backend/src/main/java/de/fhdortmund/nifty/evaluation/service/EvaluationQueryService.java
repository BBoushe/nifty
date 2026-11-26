package de.fhdortmund.nifty.evaluation.service;

import java.util.UUID;

/** Cross-slice contract (00-parallel-waves-and-contracts.md §3.7). Owner: evaluation-service. */
public interface EvaluationQueryService {

    // Latest attempt per invite. Only released attempts carry scores; queued, running, and failed attempts are
    // pending too (Decisions 2.10, 2.13, REQ-EV-01, REQ-TB-11).
    CandidateProfilesView getCandidateProfilesForConfig(UUID configId);
}
