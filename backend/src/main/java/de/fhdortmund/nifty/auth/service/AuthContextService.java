package de.fhdortmund.nifty.auth.service;

import java.util.UUID;

/** Cross-slice contract (00-parallel-waves-and-contracts.md §3.3). Owner: auth-accounts. */
public interface AuthContextService {

    UUID currentProfessorId();

    CandidateSessionPrincipal currentCandidate(); // throws 403 if not CANDIDATE
}
