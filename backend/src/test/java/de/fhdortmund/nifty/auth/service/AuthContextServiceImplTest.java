package de.fhdortmund.nifty.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        signIn(NiftyPrincipal.candidate(UUID.randomUUID(), UUID.randomUUID(), "cand_1", "hash"));

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
        signIn(NiftyPrincipal.candidate(inviteId, configId, "cand_42", "hash-1"));

        when(inviteRepository.existsByIdAndTokenHashAndRevokedFalse(inviteId, "hash-1")).thenReturn(true);
        assertThat(service.currentCandidate())
                .isEqualTo(new CandidateSessionPrincipal(inviteId, configId, "cand_42", "hash-1"));

        when(inviteRepository.existsByIdAndTokenHashAndRevokedFalse(inviteId, "hash-1")).thenReturn(false);
        assertStatus(() -> service.currentCandidate(), HttpStatus.UNAUTHORIZED);
    }

    // Decision 2.34: a rotated token changes the stored hash, so the old session's hash no longer matches.
    @Test
    void sessionWithAnOldTokenHashGets401() {
        UUID inviteId = UUID.randomUUID();
        signIn(NiftyPrincipal.candidate(inviteId, UUID.randomUUID(), "cand_42", "old-hash"));
        when(inviteRepository.existsByIdAndTokenHashAndRevokedFalse(inviteId, "old-hash")).thenReturn(false);
        when(inviteRepository.existsByIdAndTokenHashAndRevokedFalse(inviteId, "new-hash")).thenReturn(true);

        assertStatus(() -> service.currentCandidate(), HttpStatus.UNAUTHORIZED);
    }

    // A session saved before this change has no token hash. It must fail closed, without a repository call.
    @Test
    void sessionWithoutATokenHashFailsClosed() {
        UUID inviteId = UUID.randomUUID();
        signIn(NiftyPrincipal.candidate(inviteId, UUID.randomUUID(), "cand_42", null));
        when(inviteRepository.existsByIdAndTokenHashAndRevokedFalse(inviteId, null)).thenReturn(true);

        assertStatus(() -> service.currentCandidate(), HttpStatus.UNAUTHORIZED);
        verify(inviteRepository, never()).existsByIdAndTokenHashAndRevokedFalse(inviteId, null);
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
