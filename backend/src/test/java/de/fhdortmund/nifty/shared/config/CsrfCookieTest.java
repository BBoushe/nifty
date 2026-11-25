package de.fhdortmund.nifty.shared.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.PostgresContainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Decision 1.23: the first response already sets the XSRF-TOKEN cookie, so the SPA can send its first POST (login).
 * The unique property gives this class its own Spring context: spring-security-test's {@code csrf()} replaces the
 * CSRF token repository of a shared context with a test repository that sets no cookie.
 */
@SpringBootTest(properties = "nifty.test.context=csrf-cookie")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsrfCookieTest extends PostgresContainerBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousRequestGetsAReadableCsrfCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }
}
