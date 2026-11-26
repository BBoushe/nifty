package de.fhdortmund.nifty.auth.service;

import de.fhdortmund.nifty.shared.config.Role;
import de.fhdortmund.nifty.shared.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contract pack §3.3. Reads the {@link NiftyPrincipal} of the current session. */
@Service
@Transactional(readOnly = true)
class AuthContextServiceImpl implements AuthContextService {

    private static final String NOT_PROFESSOR = "This action needs a signed-in professor.";
    private static final String NOT_CANDIDATE = "This action needs a candidate session.";

    @Override
    public UUID currentProfessorId() {
        NiftyPrincipal principal = NiftyPrincipal.current()
                .orElseThrow(() -> new DomainException(HttpStatus.FORBIDDEN, NOT_PROFESSOR));
        if (principal.role() != Role.PROFESSOR) {
            throw new DomainException(HttpStatus.FORBIDDEN, NOT_PROFESSOR);
        }
        return principal.id();
    }

    // shortcut: always 403 until invite-links adds candidate sessions (auth-accounts proposal, Out of Scope).
    @Override
    public CandidateSessionPrincipal currentCandidate() {
        throw new DomainException(HttpStatus.FORBIDDEN, NOT_CANDIDATE);
    }
}
