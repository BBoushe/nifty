package de.fhdortmund.nifty.auth.service;

import de.fhdortmund.nifty.shared.config.Role;
import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The authentication principal of a Nifty session (Decision 1.23). For a professor, {@code id} is the account ID.
 * Serializable, because the server session stores it.
 */
public record NiftyPrincipal(UUID id, Role role) implements Serializable {

    /** The principal of the current request, or empty when nobody signed in through Nifty. */
    public static Optional<NiftyPrincipal> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof NiftyPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }
}
