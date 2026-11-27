package de.fhdortmund.nifty.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.auth.service.LoginRateLimiter;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * REQ-AA-13 (CSRF renewal on professor sign-in) and REQ-AA-14 (sign-in rate limit). These tests send real
 * {@code XSRF-TOKEN} cookies and headers, not the {@code csrf()} test shortcut.
 */
@SpringBootTest(properties = {
        "nifty.bootstrap.professor.email=hardening@example.test",
        "nifty.bootstrap.professor.password=synthetic-hardening-pass"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginHardeningTest extends PostgresContainerBase {

    private static final String EMAIL = "hardening@example.test";
    private static final String PASSWORD = "synthetic-hardening-pass";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LoginRateLimiter limiter;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void resetLimiter() {
        limiter.reset(EMAIL);
        limiter.reset("nobody@example.test");
        limiter.reset("other@example.test");
    }

    @Test
    void theTokenFromBeforeSignInGets403AfterSignInAndTheNewTokenWorks() throws Exception {
        Cookie before = csrfCookieOfAnonymousRequest();

        MvcResult login = login(EMAIL, PASSWORD, before).andExpect(status().isNoContent()).andReturn();
        Cookie after = lastCookie(login, "XSRF-TOKEN");
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        assertThat(after).as("sign-in sets a new XSRF-TOKEN cookie").isNotNull();
        assertThat(after.getValue()).isNotBlank().isNotEqualTo(before.getValue());
        mockMvc.perform(post("/api/v1/auth/logout").session(session).cookie(after)
                        .header(CSRF_HEADER, before.getValue()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/logout").session(session).cookie(after)
                        .header(CSRF_HEADER, after.getValue()))
                .andExpect(status().isNoContent());
    }

    // The invite row is inserted with SQL, because csrf() in other tests swaps the CSRF repository of the shared filter.
    @Test
    void inviteSignInRenewsTheCsrfTokenToo() throws Exception {
        String plainToken = "synthetic-invite-token-for-csrf-test";
        jdbc.update("INSERT INTO invite (id, config_id, candidate_ref, token_hash, created_by) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), "csrf.candidate", sha256Hex(plainToken), UUID.randomUUID());
        Cookie before = csrfCookieOfAnonymousRequest();

        MvcResult signIn = mockMvc.perform(post("/api/v1/auth/invite/" + plainToken).cookie(before)
                        .header(CSRF_HEADER, before.getValue()))
                .andExpect(status().isOk()).andReturn();
        Cookie after = lastCookie(signIn, "XSRF-TOKEN");
        MockHttpSession session = (MockHttpSession) signIn.getRequest().getSession(false);

        assertThat(after).isNotNull();
        assertThat(after.getValue()).isNotEqualTo(before.getValue());
        mockMvc.perform(post("/api/v1/auth/logout").session(session).cookie(after)
                        .header(CSRF_HEADER, before.getValue()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/logout").session(session).cookie(after)
                        .header(CSRF_HEADER, after.getValue()))
                .andExpect(status().isNoContent());
    }

    private static String sha256Hex(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theSixthFailureAnswers429WithRetryAfterAlsoForTheCorrectPassword() throws Exception {
        Cookie csrf = csrfCookieOfAnonymousRequest();
        for (int i = 0; i < 5; i++) {
            login(EMAIL.toUpperCase(), "wrong-password", csrf).andExpect(status().isUnauthorized());
        }

        login(EMAIL, PASSWORD, csrf)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("\\d+")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429));
        login("other@example.test", "wrong-password", csrf).andExpect(status().isUnauthorized());
    }

    @Test
    void aSuccessfulSignInResetsTheCount() throws Exception {
        Cookie csrf = csrfCookieOfAnonymousRequest();
        for (int i = 0; i < 4; i++) {
            login(EMAIL, "wrong-password", csrf).andExpect(status().isUnauthorized());
        }
        login(EMAIL, PASSWORD, csrf).andExpect(status().isNoContent());

        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong-password", csrf).andExpect(status().isUnauthorized());
        }
    }

    private Cookie csrfCookieOfAnonymousRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/health")).andReturn();
        Cookie cookie = lastCookie(result, "XSRF-TOKEN");
        assertThat(cookie).as("the first response issues XSRF-TOKEN").isNotNull();
        return cookie;
    }

    private ResultActions login(String email, String password, Cookie csrf) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").cookie(csrf).header(CSRF_HEADER, csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    // If a response sets the cookie twice, a browser keeps the last one. A cookie with max age 0 is a delete.
    private static Cookie lastCookie(MvcResult result, String name) {
        Cookie found = null;
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (name.equals(cookie.getName()) && cookie.getMaxAge() != 0) {
                found = cookie;
            }
        }
        return found;
    }
}
