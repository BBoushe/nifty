package de.fhdortmund.nifty.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * REQ-AA-01: two roles, and each view's routes allow only its role (Decision 1.15).
 * No controllers exist yet, so an allowed request ends in 404 and a denied one in 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mvpHasExactlyTwoRoles() {
        assertThat(Role.values()).containsExactly(Role.PROFESSOR, Role.CANDIDATE);
    }

    @Test
    void candidateCannotReachRankingRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/ranking/matrix").with(user("cand").roles("CANDIDATE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void professorReachesRankingRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/ranking/matrix").with(user("prof").roles("PROFESSOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void professorCannotReachAssessmentRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/assessment/sessions/1").with(user("prof").roles("PROFESSOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void candidateReachesAssessmentRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/assessment/sessions/1").with(user("cand").roles("CANDIDATE")))
                .andExpect(status().isNotFound());
    }
}
