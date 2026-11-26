package de.fhdortmund.nifty.studio.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.shared.config.SecurityConfig;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.shared.exception.GlobalExceptionHandler;
import de.fhdortmund.nifty.studio.service.AssessmentDeletionService;
import de.fhdortmund.nifty.studio.service.AssessmentDetailResponse;
import de.fhdortmund.nifty.studio.service.AssessmentReadService;
import de.fhdortmund.nifty.studio.service.AssessmentSummaryResponse;
import de.fhdortmund.nifty.studio.service.ConfigCreationService;
import de.fhdortmund.nifty.studio.service.CreatedAssessment;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * REQ-IS-01, REQ-IS-06, REQ-IS-07, REQ-IS-08, REQ-IS-10, REQ-AA-01:
 * Web-slice tests for StudioConfigController under real SecurityConfig path guards.
 */
@WebMvcTest(StudioConfigController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class StudioConfigControllerTest {

    private static final String BASE = "/api/v1/ranking/studio/configs";
    private static final String MINIMAL_JSON = "{\"settings\":{\"title\":\"SE\"},\"skills\":[],\"questions\":[]}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfigCreationService creationService;
    @MockitoBean
    private AssessmentReadService readService;
    @MockitoBean
    private AssessmentDeletionService deletionService;

    @Test
    void postReturns201OnCreateAnd200OnIdempotentReplay() throws Exception {
        UUID key = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        AssessmentDetailResponse detail = sampleDetail(configId);

        when(creationService.create(eq(key), any())).thenReturn(new CreatedAssessment(configId, true));
        when(readService.getForCurrentProfessor(configId)).thenReturn(detail);

        mockMvc.perform(post(BASE)
                        .with(user("prof").roles("PROFESSOR"))
                        .with(csrf())
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MINIMAL_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/" + configId))
                .andExpect(jsonPath("$.configId").value(configId.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.version").value(1));

        when(creationService.create(eq(key), any())).thenReturn(new CreatedAssessment(configId, false));

        mockMvc.perform(post(BASE)
                        .with(user("prof").roles("PROFESSOR"))
                        .with(csrf())
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MINIMAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId").value(configId.toString()));
    }

    @Test
    void postAcceptsAndIgnoresTheRemovedInventedQuestionsField() throws Exception {
        UUID key = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(creationService.create(eq(key), any())).thenReturn(new CreatedAssessment(configId, true));
        when(readService.getForCurrentProfessor(configId)).thenReturn(sampleDetail(configId));

        mockMvc.perform(post(BASE)
                        .with(user("prof").roles("PROFESSOR"))
                        .with(csrf())
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settings\":{\"title\":\"SE\",\"inventedQuestions\":true},"
                                + "\"skills\":[],\"questions\":[]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void postWithInvalidOrMissingIdempotencyKeyOrFieldErrorsReturns400ProblemDetail() throws Exception {
        mockMvc.perform(post(BASE)
                        .with(user("prof").roles("PROFESSOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MINIMAL_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));

        mockMvc.perform(post(BASE)
                        .with(user("prof").roles("PROFESSOR"))
                        .with(csrf())
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MINIMAL_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));

        mockMvc.perform(post(BASE)
                        .with(user("prof").roles("PROFESSOR"))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        UUID key = UUID.randomUUID();
        when(creationService.create(eq(key), any())).thenThrow(new FieldValidationException(List.of(
                new FieldErrorDetail("questions[0].maxTimeSec", "must be greater than minTimeSec", "TIME_RANGE"))));

        mockMvc.perform(post(BASE)
                        .with(user("prof").roles("PROFESSOR"))
                        .with(csrf())
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MINIMAL_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].code").value("TIME_RANGE"));
    }

    @Test
    void candidateRoleGets403OnAllStudioEndpoints() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get(BASE).with(user("cand").roles("CANDIDATE"))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE).with(user("cand").roles("CANDIDATE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(MINIMAL_JSON)).andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/" + id).with(user("cand").roles("CANDIDATE")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getListAndDetailReturnScopedResultsAnd404ForOtherProfessor() throws Exception {
        UUID id = UUID.randomUUID();
        when(readService.listForCurrentProfessor()).thenReturn(List.of(new AssessmentSummaryResponse(
                id, "SE Block Week", GroupingMode.HARD_SKILLS, false, 4, 2, 3, "PUBLISHED", 1, Instant.now())));
        when(readService.getForCurrentProfessor(id)).thenReturn(sampleDetail(id));

        mockMvc.perform(get(BASE).with(user("prof").roles("PROFESSOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].configId").value(id.toString()))
                .andExpect(jsonPath("$[0].title").value("SE Block Week"));

        mockMvc.perform(get(BASE + "/" + id).with(user("prof").roles("PROFESSOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0].rubricLevels.length()").value(5));

        UUID otherId = UUID.randomUUID();
        when(readService.getForCurrentProfessor(otherId))
                .thenThrow(new DomainException(HttpStatus.NOT_FOUND, "Assessment configuration not found."));

        mockMvc.perform(get(BASE + "/" + otherId).with(user("prof").roles("PROFESSOR")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void putAndPatchReturn405AndDeleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put(BASE + "/" + id).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(MINIMAL_JSON))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(patch(BASE + "/" + id).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(MINIMAL_JSON))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete(BASE + "/" + id).with(user("prof").roles("PROFESSOR")).with(csrf()))
                .andExpect(status().isNoContent());

        verify(deletionService).deleteForCurrentProfessor(id);
    }

    private static AssessmentDetailResponse sampleDetail(UUID id) {
        return new AssessmentDetailResponse(
                id,
                "SE Block Week",
                "Evaluate backend craft",
                new BigDecimal("0.6000"),
                1,
                false,
                4,
                GroupingMode.HARD_SKILLS,
                SoftPreset.ENGINEERING_DEFAULT,
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                Map.of(),
                "PUBLISHED",
                1,
                Instant.now(),
                List.of(new AssessmentDetailResponse.SkillDetail(
                        UUID.randomUUID(), "Java", List.of("0", "1", "2", "3", "4"))),
                List.of());
    }
}
