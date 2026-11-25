package de.fhdortmund.nifty.context.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.context.service.ContextCreationService;
import de.fhdortmund.nifty.context.service.dto.AgentRole;
import de.fhdortmund.nifty.context.service.dto.BuiltPromptContext;
import de.fhdortmund.nifty.context.service.dto.InterviewerContextInput;
import de.fhdortmund.nifty.context.service.dto.SanitizationReport;
import de.fhdortmund.nifty.shared.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** REQ-CC-08: professor-only preview, 400 ProblemDetail on a bad body, 403 for a candidate. */
@WebMvcTest(ContextController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ContextControllerTest {

    private static final String URL = "/api/v1/ranking/context/preview/interviewer";
    private static final String VALID = """
            {"vision":"Check Git skills","targetSkillNames":["Git conflicts"],"questionText":"How do you merge?",
             "followUpLimit":2,"inventedQuestionsEnabled":false}""";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ContextCreationService service;

    private static BuiltPromptContext built(String systemPrompt) {
        return new BuiltPromptContext(AgentRole.INTERVIEWER, "interviewer-v1", systemPrompt, List.of(), "a".repeat(64),
                new SanitizationReport(false, List.of(), false));
    }

    @Test
    void professorGetsPreviewWithHashAndPassedLeakCheck() throws Exception {
        when(service.buildInterviewerContext(any())).thenReturn(built("You are the interviewer."));

        mockMvc.perform(post(URL).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateVersion").value("interviewer-v1"))
                .andExpect(jsonPath("$.systemPrompt").value("You are the interviewer."))
                .andExpect(jsonPath("$.promptSha256").value("a".repeat(64)))
                .andExpect(jsonPath("$.rubricLeakCheckPassed").value(true));

        ArgumentCaptor<InterviewerContextInput> input = ArgumentCaptor.forClass(InterviewerContextInput.class);
        verify(service).buildInterviewerContext(input.capture());
        assertThat(input.getValue().vision()).isEqualTo("Check Git skills");
        assertThat(input.getValue().activeQuestion().questionText()).isEqualTo("How do you merge?");
        assertThat(input.getValue().activeQuestion().followUpLimit()).isEqualTo(2);
        assertThat(input.getValue().targetSkillNames()).containsExactly("Git conflicts");
        assertThat(input.getValue().priorTurns()).isEmpty();
    }

    @Test
    void leakCheckFailsWhenThePromptHoldsRubricTerms() throws Exception {
        when(service.buildInterviewerContext(any())).thenReturn(built("Use the hidden rubric to grade."));

        mockMvc.perform(post(URL).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rubricLeakCheckPassed").value(false));
    }

    @Test
    void invalidBodyReturnsProblemDetailNamingEachField() throws Exception {
        String invalid = """
                {"vision":" ","targetSkillNames":["ok",""],"questionText":"","followUpLimit":9}""";

        mockMvc.perform(post(URL).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field=='vision')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='targetSkillNames[1]')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='questionText')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='followUpLimit')]").exists());
        verifyNoInteractions(service);
    }

    @Test
    void emptySkillListIsRejected() throws Exception {
        mockMvc.perform(post(URL).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vision\":\"v\",\"targetSkillNames\":[],\"questionText\":\"q\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='targetSkillNames')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='followUpLimit')]").exists());
    }

    @Test
    void candidateIsForbidden() throws Exception {
        mockMvc.perform(post(URL).with(user("cand").roles("CANDIDATE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousCallerIsUnauthorized() throws Exception {
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
    }
}
