package de.fhdortmund.nifty.context.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.context.service.ContextCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The real {@code ContextCreationServiceImpl} behind the preview endpoint. AuditService is still a placeholder. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContextPreviewIntegrationTest extends PostgresContainerBase {

    private static final String URL = "/api/v1/ranking/context/preview/interviewer";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext context;
    @MockitoBean
    private AuditService audit;

    private static String body(String vision) {
        return "{\"vision\":\"" + vision + "\",\"targetSkillNames\":[\"Git conflicts\"],"
                + "\"questionText\":\"How do you merge?\",\"followUpLimit\":1}";
    }

    @Test
    void exactlyOneRealBeanServesTheContract() {
        assertThat(context.getBeansOfType(ContextCreationService.class)).hasSize(1);
    }

    @Test
    void professorPreviewRunsTheRealBuilder() throws Exception {
        mockMvc.perform(post(URL).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Check Git skills")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateVersion").value("interviewer-v1"))
                .andExpect(jsonPath("$.systemPrompt").value(containsString("How do you merge?")))
                .andExpect(jsonPath("$.promptSha256").value(matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.rubricLeakCheckPassed").value(true));
    }

    @Test
    void visionThatNamesARubricFailsTheLeakCheck() throws Exception {
        mockMvc.perform(post(URL).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Use the rubric with weights")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rubricLeakCheckPassed").value(false));
    }

    @Test
    void candidateIsForbidden() throws Exception {
        mockMvc.perform(post(URL).with(user("cand").roles("CANDIDATE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("v")))
                .andExpect(status().isForbidden());
    }
}
