package de.fhdortmund.nifty.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.PostgresContainerBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-AA-01, REQ-AA-05, REQ-AA-08, Decision 1.23, Decision 4.4:
 * end-to-end professor creates studio config & invites -> candidate signs in -> candidate blocked on /ranking/** ->
 * revocation immediately ends open candidate session (401) -> GDPR assessment delete purges all invites in same tx.
 */
@SpringBootTest(properties = {
        "nifty.bootstrap.professor.email=invite.e2e@example.test",
        "nifty.bootstrap.professor.password=synthetic-e2e-pass"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InviteRevocationAndPurgeTest extends PostgresContainerBase {

    private static final String LOGIN_BODY =
            "{\"email\":\"invite.e2e@example.test\",\"password\":\"synthetic-e2e-pass\"}";

    private static final String CREATE_CONFIG_BODY = """
            {
              "settings": {
                "title": "Invite E2E Assessment",
                "vision": "Verify invite lifecycle",
                "minCoverage": 0.5,
                "maxAttempts": 1,
                "inventedQuestions": false,
                "bfi10Enabled": true,
                "teamSize": 4,
                "groupingMode": "SOFT_SKILLS",
                "softPreset": "ENGINEERING_DEFAULT",
                "combinedHardWeight": 0.5,
                "alpha": 0.5,
                "constraintWeights": {}
              },
              "skills": [],
              "questions": []
            }
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void revocationEndsOpenCandidateSessionAndCandidateCannotReachRankingRoutes() throws Exception {
        MockHttpSession professorSession = signInProfessor();
        UUID configId = createConfig(professorSession);

        String createResp = mockMvc.perform(post("/api/v1/ranking/studio/configs/" + configId + "/invites")
                        .session(professorSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateRefs\":[\"cand_live\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(createResp).get(0);
        UUID inviteId = UUID.fromString(created.get("inviteId").asText());
        String link = created.get("link").asText();
        String token = link.substring(link.lastIndexOf('/') + 1);

        MockHttpSession candidateSession = (MockHttpSession) mockMvc.perform(
                        post("/api/v1/auth/invite/" + token).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
        assertThat(candidateSession).isNotNull();

        // REQ-AA-01 / REQ-AA-05: candidate gets 403 on professor ranking routes.
        mockMvc.perform(get("/api/v1/ranking/studio/configs").session(candidateSession))
                .andExpect(status().isForbidden());

        // Candidate reaches /me while active.
        mockMvc.perform(get("/api/v1/auth/me").session(candidateSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CANDIDATE"));

        // Professor revokes the invite.
        mockMvc.perform(delete("/api/v1/ranking/studio/configs/" + configId + "/invites/" + inviteId)
                        .session(professorSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // REQ-AA-08: candidate's very next request answers 401 ProblemDetail and ends the session.
        mockMvc.perform(get("/api/v1/auth/me").session(candidateSession))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
        assertThat(candidateSession.isInvalid()).isTrue();
    }

    @Test
    void deletingAssessmentPurgesItsInvitesInSameTransactionAndKeepsOtherConfigsInvites() throws Exception {
        MockHttpSession professorSession = signInProfessor();
        UUID configToDelete = createConfig(professorSession);
        UUID configToKeep = createConfig(professorSession);

        mockMvc.perform(post("/api/v1/ranking/studio/configs/" + configToDelete + "/invites")
                        .session(professorSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateRefs\":[\"del_1\",\"del_2\"]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/ranking/studio/configs/" + configToKeep + "/invites")
                        .session(professorSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateRefs\":[\"keep_1\"]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/ranking/studio/configs/" + configToDelete)
                        .session(professorSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM invite WHERE config_id = ?", Long.class, configToDelete))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invite WHERE config_id = ?", Long.class, configToKeep))
                .isEqualTo(1L);
    }

    private MockHttpSession signInProfessor() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private UUID createConfig(MockHttpSession professorSession) throws Exception {
        String response = mockMvc.perform(post("/api/v1/ranking/studio/configs")
                        .session(professorSession)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_CONFIG_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("configId").asText());
    }
}
