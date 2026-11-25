package de.fhdortmund.nifty.auth.service;

import de.fhdortmund.nifty.auth.repository.ProfessorAccountEntity;
import de.fhdortmund.nifty.auth.repository.ProfessorAccountRepository;
import de.fhdortmund.nifty.shared.config.Role;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only {@link UserDetailsService} bean, so Spring Boot creates no generated-password user.
 * The username of the returned user is the account ID; {@link AuthSessionService} turns it into a {@link NiftyPrincipal}.
 */
@Service
@Transactional(readOnly = true)
public class ProfessorUserDetailsService implements UserDetailsService {

    private final ProfessorAccountRepository repository;

    ProfessorUserDetailsService(ProfessorAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        ProfessorAccountEntity account = repository.findByEmail(ProfessorAccountEntity.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
        return User.withUsername(account.getId().toString())
                .password(account.getPasswordHash())
                .roles(Role.PROFESSOR.name())
                .build();
    }
}
