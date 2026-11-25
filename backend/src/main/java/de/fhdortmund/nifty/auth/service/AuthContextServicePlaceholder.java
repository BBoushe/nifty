package de.fhdortmund.nifty.auth.service;

import java.util.UUID;
import org.springframework.stereotype.Service;

/** Compile-only placeholder. auth-accounts deletes this class when the real bean lands. */
@Service
class AuthContextServicePlaceholder implements AuthContextService {

    private static final String MESSAGE = "AuthContextService is not implemented yet: owner auth-accounts";

    @Override
    public UUID currentProfessorId() {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public CandidateSessionPrincipal currentCandidate() {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
