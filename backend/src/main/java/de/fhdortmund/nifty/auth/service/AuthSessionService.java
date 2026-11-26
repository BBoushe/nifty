package de.fhdortmund.nifty.auth.service;

import de.fhdortmund.nifty.shared.config.Role;
import de.fhdortmund.nifty.shared.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** REQ-AA-02: checks professor credentials and builds the session principal (Decision 1.23). */
@Service
@Transactional(readOnly = true)
public class AuthSessionService {

    // REQ-AA-02: one detail for an unknown email and a wrong password.
    static final String BAD_CREDENTIALS = "The email or password is not correct.";
    private static final String NOT_SIGNED_IN = "Sign in to use this resource.";

    private final AuthenticationManager authenticationManager;

    AuthSessionService(ProfessorUserDetailsService users, PasswordEncoder encoder) {
        // DaoAuthenticationProvider also hashes for unknown emails, so timing does not reveal which part was wrong.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        this.authenticationManager = new ProviderManager(provider);
    }

    /** Returns an authenticated token whose principal is a professor {@link NiftyPrincipal}, or throws 401. */
    public Authentication login(String email, String password) {
        Authentication checked;
        try {
            checked = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (AuthenticationException ex) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        // ProfessorUserDetailsService puts the account ID into the username.
        UUID accountId = UUID.fromString(((UserDetails) checked.getPrincipal()).getUsername());
        NiftyPrincipal principal = new NiftyPrincipal(accountId, Role.PROFESSOR);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, checked.getAuthorities());
    }

    /** The principal of the current session, or 401. */
    public NiftyPrincipal me() {
        return NiftyPrincipal.current().orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, NOT_SIGNED_IN));
    }
}
