package de.fhdortmund.nifty.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.PostgresContainerBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Decision 1.23: exercises the real CookieCsrfTokenRepository flow (without spring-security-test's {@code csrf()}
 * helper, which replaces the repository on a shared context).
 */
@SpringBootTest(properties = {
        "nifty.test.context=csrf-cookie",
        "nifty.bootstrap.professor.email=csrf.flow@example.test",
        "nifty.bootstrap.professor.password=synthetic-csrf-pass"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsrfCookieTest extends PostgresContainerBase {

    private static final String LOGIN_BODY =
            "{\"email\":\"csrf.flow@example.test\",\"password\":\"synthetic-csrf-pass\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousProtectedRouteGets401ProblemNoBasicChallengeNoSessionAndCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/ranking/studio/configs"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/ranking/studio/configs"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void realCookieCsrfSupportsLoginLogoutAndReLoginAfterTokenRefresh() throws Exception {
        Cookie firstToken = fetchXsrfCookie();

        // Missing CSRF header yields 403 RFC 9457 ProblemDetail.
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));

        // Login with matching cookie + header succeeds.
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(firstToken)
                        .header("X-XSRF-TOKEN", firstToken.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
        assertThat(session).isNotNull();

        // Logout with matching cookie + header ends the session and clears XSRF-TOKEN.
        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .cookie(firstToken)
                        .header("X-XSRF-TOKEN", firstToken.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0));

        // Re-fetching /me issues a fresh XSRF-TOKEN cookie so re-login succeeds without a page reload.
        Cookie secondToken = fetchXsrfCookie();
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(secondToken)
                        .header("X-XSRF-TOKEN", secondToken.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isNoContent());
    }

    private Cookie fetchXsrfCookie() throws Exception {
        Cookie token = mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        assertThat(token.getValue()).isNotBlank();
        return token;
    }
}
