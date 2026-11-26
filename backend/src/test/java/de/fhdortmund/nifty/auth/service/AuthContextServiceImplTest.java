package de.fhdortmund.nifty.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.auth.repository.InviteRepository;
import de.fhdortmund.nifty.shared.config.Role;
import de.fhdortmund.nifty.shared.exception.DomainException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Contract pack §3.3: currentProfessorId() and currentCandidate() enforce role and active-invite invariants. */
class AuthContextServiceImplTest {

    private final InviteRepository inviteRepository = mock(InviteRepository.class);
    private final AuthContextServiceImpl service = new AuthContextServiceImpl(inviteRepository);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void professorPrincipalGivesItsId() {
        UUID id = UUID.randomUUID();
        signIn(NiftyPrincipal.professor(id));

        assertThat(service.currentProfessorId()).isEqualTo(id);
    }

    @Test
    void noAuthenticationIsForbidden() {
        assertStatus(() -> service.currentProfessorId(), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.currentCandidate(), HttpStatus.FORBIDDEN);
    }

    @Test
    void candidatePrincipalIsForbiddenForProfessorId() {
        signIn(NiftyPrincipal.candidate(UUID.randomUUID(), UUID.randomUUID(), "cand_1"));

        assertStatus(() -> service.currentProfessorId(), HttpStatus.FORBIDDEN);
    }

    @Test
    void professorPrincipalIsForbiddenForCurrentCandidate() {
        signIn(NiftyPrincipal.professor(UUID.randomUUID()));

        assertStatus(() -> service.currentCandidate(), HttpStatus.FORBIDDEN);
    }

    @Test
    void activeCandidateReturnsBoundPrincipalAndRevokedCandidateGets401() {
        UUID inviteId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        signIn(NiftyPrincipal.candidate(inviteId, configId, "cand_42"));

        when(inviteRepository.existsByIdAndRevokedFalse(inviteId)).thenReturn(true);
        assertThat(service.currentCandidate()).isEqualTo(new CandidateSessionPrincipal(inviteId, configId, "cand_42"));

        when(inviteRepository.existsByIdAndRevokedFalse(inviteId)).thenReturn(false);
        assertStatus(() -> service.currentCandidate(), HttpStatus.UNAUTHORIZED);
    }

    private static void signIn(NiftyPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private static void assertStatus(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(expected);
    }
}
