package de.fhdortmund.nifty.auth.service;

import de.fhdortmund.nifty.auth.repository.InviteRepository;
import de.fhdortmund.nifty.shared.config.Role;
import de.fhdortmund.nifty.shared.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contract pack §3.3. Reads the {@link NiftyPrincipal} of the current session and checks invite revocation. */
@Service
@Transactional(readOnly = true)
class AuthContextServiceImpl implements AuthContextService {

    private static final String NOT_PROFESSOR = "This action needs a signed-in professor.";
    private static final String NOT_CANDIDATE = "This action needs a candidate session.";
    private static final String REVOKED_INVITE = "This link is not valid.";

    private final InviteRepository inviteRepository;

    AuthContextServiceImpl(InviteRepository inviteRepository) {
        this.inviteRepository = inviteRepository;
    }

    @Override
    public UUID currentProfessorId() {
        NiftyPrincipal principal = NiftyPrincipal.current()
                .orElseThrow(() -> new DomainException(HttpStatus.FORBIDDEN, NOT_PROFESSOR));
        if (principal.role() != Role.PROFESSOR) {
            throw new DomainException(HttpStatus.FORBIDDEN, NOT_PROFESSOR);
        }
        return principal.id();
    }

    @Override
    public CandidateSessionPrincipal currentCandidate() {
        NiftyPrincipal principal = NiftyPrincipal.current()
                .orElseThrow(() -> new DomainException(HttpStatus.FORBIDDEN, NOT_CANDIDATE));
        if (principal.role() != Role.CANDIDATE || principal.inviteId() == null) {
            throw new DomainException(HttpStatus.FORBIDDEN, NOT_CANDIDATE);
        }
        // Decision 2.34: a null hash (a session from before the hash existed) or a rotated hash fails closed.
        if (principal.tokenHash() == null
                || !inviteRepository.existsByIdAndTokenHashAndRevokedFalse(principal.inviteId(), principal.tokenHash())) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, REVOKED_INVITE);
        }
        return new CandidateSessionPrincipal(
                principal.inviteId(), principal.configId(), principal.candidateRef(), principal.tokenHash());
    }
}
