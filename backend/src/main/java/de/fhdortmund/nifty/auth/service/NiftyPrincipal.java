package de.fhdortmund.nifty.auth.service;

import de.fhdortmund.nifty.shared.config.Role;
import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The authentication principal of a Nifty session (Decision 1.23).
 * For a professor, {@code id} is the account ID. For a candidate, {@code id} equals {@code inviteId}
 * and {@code inviteId}, {@code configId}, {@code candidateRef} bind the session to one invite.
 * {@code tokenHash} is the invite token hash at sign-in. A re-invite rotates it (Decision 2.34), which ends the session.
 * A session saved before this field existed deserializes with {@code null}, and the checks treat that as invalid.
 */
public record NiftyPrincipal(
        UUID id,
        Role role,
        UUID inviteId,
        UUID configId,
        String candidateRef,
        String tokenHash
) implements Serializable {

    public NiftyPrincipal(UUID id, Role role) {
        this(id, role, null, null, null, null);
    }

    public static NiftyPrincipal professor(UUID professorId) {
        return new NiftyPrincipal(professorId, Role.PROFESSOR, null, null, null, null);
    }

    public static NiftyPrincipal candidate(UUID inviteId, UUID configId, String candidateRef, String tokenHash) {
        return new NiftyPrincipal(inviteId, Role.CANDIDATE, inviteId, configId, candidateRef, tokenHash);
    }

    /** The principal of the current request, or empty when nobody signed in through Nifty. */
    public static Optional<NiftyPrincipal> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof NiftyPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }
}
