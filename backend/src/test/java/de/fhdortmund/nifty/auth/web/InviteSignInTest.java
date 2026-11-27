package de.fhdortmund.nifty.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.auth.service.CandidateSessionPrincipal;
import de.fhdortmund.nifty.auth.service.CreatedInvite;
import de.fhdortmund.nifty.auth.service.InviteService;
import de.fhdortmund.nifty.auth.service.NiftyPrincipal;
import de.fhdortmund.nifty.shared.config.Role;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** REQ-AA-04 (Decision 1.17, 1.23): invite-link sign-in, candidate principal, and currentCandidate(). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InviteSignInTest extends PostgresContainerBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InviteService inviteService;
    @Autowired
    private AuthContextService authContextService;

    @MockitoBean
    private StudioQueryService studioQueryService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void validInviteStartsCandidateSessionRotatesSessionIdAndExposesMeAndCurrentCandidate() throws Exception {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        CreatedInvite invite = createInviteAsProfessor(professorId, configId, "cand_042");
        String token = extractToken(invite.link());

        MockHttpSession preExisting = new MockHttpSession();
        String oldSessionId = preExisting.getId();

        MockHttpSession candidateSession = (MockHttpSession) mockMvc.perform(
                        post("/api/v1/auth/invite/" + token).session(preExisting).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId").value(configId.toString()))
                .andReturn().getRequest().getSession(false);

        assertThat(candidateSession).isNotNull();
        assertThat(candidateSession.getId()).isNotEqualTo(oldSessionId);

        mockMvc.perform(get("/api/v1/auth/me").session(candidateSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.inviteId").value(invite.inviteId().toString()))
                .andExpect(jsonPath("$.configId").value(configId.toString()));

        // Decision 2.34: sign-in stores the token hash in the session principal.
        NiftyPrincipal signedIn = (NiftyPrincipal) ((org.springframework.security.core.context.SecurityContext)
                candidateSession.getAttribute("SPRING_SECURITY_CONTEXT")).getAuthentication().getPrincipal();
        assertThat(signedIn.tokenHash()).isNotBlank();

        // Verify currentCandidate() resolves the bound invite when authenticated as CANDIDATE.
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        NiftyPrincipal.candidate(invite.inviteId(), configId, "cand_042", signedIn.tokenHash()),
                        null,
                        List.of()));
        CandidateSessionPrincipal bound = authContextService.currentCandidate();
        assertThat(bound).isEqualTo(
                new CandidateSessionPrincipal(invite.inviteId(), configId, "cand_042", signedIn.tokenHash()));
    }

    @Test
    void unknownAndRevokedTokensReturnIdentical401AndStartNoSession() throws Exception {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        CreatedInvite invite = createInviteAsProfessor(professorId, configId, "cand_revoked");
        String token = extractToken(invite.link());

        signInProfessor(professorId);
        inviteService.revokeInvite(configId, invite.inviteId());
        clearSecurityContext();

        var revokedResult = mockMvc.perform(post("/api/v1/auth/invite/" + token).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        assertNoAuthenticatedSession(revokedResult.getRequest().getSession(false));

        var unknownResult = mockMvc.perform(post("/api/v1/auth/invite/unknown-token-value").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        assertNoAuthenticatedSession(unknownResult.getRequest().getSession(false));

        // Both responses carry the exact same detail (REQ-AA-04).
        String revokedDetail = revokedResult.getResponse().getContentAsString()
                .replace(token, "<TOKEN>");
        String unknownDetail = unknownResult.getResponse().getContentAsString()
                .replace("unknown-token-value", "<TOKEN>");
        assertThat(revokedDetail).isEqualTo(unknownDetail);

        // currentCandidate() on a revoked invite gives 401.
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        NiftyPrincipal.candidate(invite.inviteId(), configId, "cand_revoked", "any-hash"),
                        null,
                        List.of()));
        assertThatThrownBy(() -> authContextService.currentCandidate())
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private CreatedInvite createInviteAsProfessor(UUID professorId, UUID configId, String candidateRef) {
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        signInProfessor(professorId);
        try {
            return inviteService.createInvites(configId, List.of(candidateRef)).getFirst();
        } finally {
            clearSecurityContext();
        }
    }

    private void assertNoAuthenticatedSession(jakarta.servlet.http.HttpSession session) throws Exception {
        if (session == null) {
            return;
        }
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        mockMvc.perform(get("/api/v1/auth/me").session((MockHttpSession) session))
                .andExpect(status().isUnauthorized());
    }

    private static void signInProfessor(UUID professorId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new NiftyPrincipal(professorId, Role.PROFESSOR), null, List.of()));
    }

    private static String extractToken(String link) {
        return link.substring(link.lastIndexOf('/') + 1);
    }
}
