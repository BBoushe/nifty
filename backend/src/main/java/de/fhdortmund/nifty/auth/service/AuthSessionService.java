package de.fhdortmund.nifty.auth.service;

import de.fhdortmund.nifty.auth.repository.InviteEntity;
import de.fhdortmund.nifty.auth.repository.InviteRepository;
import de.fhdortmund.nifty.shared.config.Role;
import de.fhdortmund.nifty.shared.exception.DomainException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** REQ-AA-02, REQ-AA-04: checks professor credentials or invite tokens and builds session principals (Decision 1.23). */
@Service
@Transactional(readOnly = true)
public class AuthSessionService {

    // REQ-AA-02: one detail for an unknown email and a wrong password.
    static final String BAD_CREDENTIALS = "The email or password is not correct.";
    // REQ-AA-04: one detail for an unknown token and a revoked token.
    static final String INVALID_INVITE = "This link is not valid.";
    private static final String NOT_SIGNED_IN = "Sign in to use this resource.";

    private final AuthenticationManager authenticationManager;
    private final InviteRepository inviteRepository;

    AuthSessionService(
            ProfessorUserDetailsService users,
            PasswordEncoder encoder,
            InviteRepository inviteRepository) {
        // DaoAuthenticationProvider also hashes for unknown emails, so timing does not reveal which part was wrong.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        this.authenticationManager = new ProviderManager(provider);
        this.inviteRepository = inviteRepository;
    }

    /** Returns an authenticated token whose principal is a professor {@link NiftyPrincipal}, or throws 401. */
    public Authentication login(String email, String password) {
        Authentication checked;
        try {
            checked = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (AuthenticationException ex) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        // ProfessorUserDetailsService puts the account ID into the username.
        UUID accountId = UUID.fromString(((UserDetails) checked.getPrincipal()).getUsername());
        NiftyPrincipal principal = NiftyPrincipal.professor(accountId);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, checked.getAuthorities());
    }

    /** REQ-AA-04: validates a plain invite token by SHA-256 hash and builds a CANDIDATE session token, or throws 401. */
    public Authentication loginWithInvite(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, INVALID_INVITE);
        }
        String hash = TokenHasher.sha256Hex(plainToken.trim());
        InviteEntity invite = inviteRepository.findByTokenHash(hash)
                .filter(found -> !found.isRevoked())
                .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, INVALID_INVITE));
        NiftyPrincipal principal = NiftyPrincipal.candidate(
                invite.getId(), invite.getConfigId(), invite.getCandidateRef(), invite.getTokenHash());
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + Role.CANDIDATE.name())));
    }

    /** The principal of the current session, or 401. */
    public NiftyPrincipal me() {
        return NiftyPrincipal.current().orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, NOT_SIGNED_IN));
    }

    /**
     * True if the invite exists, is not revoked, and still has the token hash of the session (used by
     * RevokedInviteFilter). A null hash fails closed (Decision 2.34).
     */
    public boolean isInviteActive(UUID inviteId, String tokenHash) {
        return inviteId != null && tokenHash != null
                && inviteRepository.existsByIdAndTokenHashAndRevokedFalse(inviteId, tokenHash);
    }
}
