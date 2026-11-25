package de.fhdortmund.nifty.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.auth.service.NiftyPrincipal;
import de.fhdortmund.nifty.shared.config.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** REQ-AA-02 and REQ-AA-06 (Decision 1.23): session login, CSRF, logout and me. */
@SpringBootTest(properties = {
        "nifty.bootstrap.professor.email=auth.controller@example.test",
        "nifty.bootstrap.professor.password=synthetic-login-pass"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest extends PostgresContainerBase {

    private static final String EMAIL = "auth.controller@example.test";
    private static final String PASSWORD = "synthetic-login-pass";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Environment environment;

    @Test
    void loginStartsAProfessorSession() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROFESSOR"))
                .andExpect(jsonPath("$.professorId").isNotEmpty());
    }

    @Test
    void loginAcceptsTheEmailInAnyCase() throws Exception {
        login("Auth.Controller@Example.TEST", PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
        String wrongPassword = login(EMAIL, "not-the-password")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.test", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(EMAIL, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutNeedsCsrfAndEndsTheSession() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/v1/auth/logout").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutSessionIs401Problem() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void sessionCookieIsHttpOnlyAndLax() {
        assertThat(environment.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
        assertThat(environment.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
    }

    @Test
    void loginRotatesPreExistingSessionIdToPreventSessionFixation() throws Exception {
        MockHttpSession existing = new MockHttpSession();
        String oldId = existing.getId();

        MockHttpSession afterLogin = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .session(existing)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);

        assertThat(afterLogin).isNotNull();
        assertThat(afterLogin.getId()).isNotEqualTo(oldId);
    }

    @Test
    void loginRejectsBlankEmailAndPasswordWith400ProblemErrors() throws Exception {
        login("   ", "")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void meForCandidateOmitsProfessorId() throws Exception {
        var candidateAuth = UsernamePasswordAuthenticationToken.authenticated(
                new NiftyPrincipal(UUID.randomUUID(), Role.CANDIDATE),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CANDIDATE")));

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(candidateAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.professorId").value(org.hamcrest.Matchers.nullValue()));
    }

    private MockHttpSession signIn() throws Exception {
        MockHttpSession session = (MockHttpSession) login(EMAIL, PASSWORD)
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(email, password)));
    }

    private static String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
