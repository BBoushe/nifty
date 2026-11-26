package de.fhdortmund.nifty.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.shared.config.Role;
import de.fhdortmund.nifty.shared.exception.DomainException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Contract pack §3.3: currentProfessorId() answers only for a signed-in professor. */
class AuthContextServiceImplTest {

    private final AuthContextServiceImpl service = new AuthContextServiceImpl();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void professorPrincipalGivesItsId() {
        UUID id = UUID.randomUUID();
        signIn(new NiftyPrincipal(id, Role.PROFESSOR));

        assertThat(service.currentProfessorId()).isEqualTo(id);
    }

    @Test
    void noAuthenticationIsForbidden() {
        assertForbidden(() -> service.currentProfessorId());
    }

    @Test
    void candidatePrincipalIsForbiddenForProfessorId() {
        signIn(new NiftyPrincipal(UUID.randomUUID(), Role.CANDIDATE));

        assertForbidden(() -> service.currentProfessorId());
    }

    @Test
    void currentCandidateIsForbiddenUntilInviteLinks() {
        signIn(new NiftyPrincipal(UUID.randomUUID(), Role.PROFESSOR));

        assertForbidden(() -> service.currentCandidate());
    }

    private static void signIn(NiftyPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
