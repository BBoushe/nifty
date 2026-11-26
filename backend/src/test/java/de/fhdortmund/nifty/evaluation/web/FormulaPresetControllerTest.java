package de.fhdortmund.nifty.evaluation.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.evaluation.service.EvaluationDtos;
import de.fhdortmund.nifty.evaluation.service.FormulaPresetService;
import de.fhdortmund.nifty.shared.config.SecurityConfig;
import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.shared.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US-EV-04b / REQ-FE-02, REQ-FE-05: web-slice tests for formula presets, apply, and preview.
 */
@WebMvcTest(FormulaPresetController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class FormulaPresetControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private FormulaPresetService formulaPresetService;

    @Test
    void listsPresetsAppliesAndPreviewsForProfessorAndForbidsCandidate() throws Exception {
        UUID configId = UUID.randomUUID();
        when(formulaPresetService.listPresets()).thenReturn(List.of(
                new EvaluationDtos.FormulaPresetInfo("WEIGHTED_MEAN", "Question-Weighted Mean", "Formula B", new BigDecimal("0.5000"))));
        when(formulaPresetService.applyPreset(eq(configId), any())).thenReturn(
                new EvaluationDtos.FormulaVersionResponse(
                        UUID.randomUUID(), configId, 2, "WEIGHTED_MEAN", new BigDecimal("0.5000"),
                        Map.of(), Instant.now(), 3));
        when(formulaPresetService.previewPreset(eq(configId), any())).thenReturn(
                new EvaluationDtos.FormulaPreviewResponse(configId, "WEIGHTED_MEAN", new BigDecimal("0.5000"), List.of()));

        mockMvc.perform(get("/api/v1/ranking/formula-presets").with(user("prof").roles("PROFESSOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].preset").value("WEIGHTED_MEAN"));

        mockMvc.perform(get("/api/v1/ranking/formula-presets").with(user("cand").roles("CANDIDATE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ranking/configs/{id}/formula", configId)
                        .with(user("prof").roles("PROFESSOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"WEIGHTED_MEAN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2));

        mockMvc.perform(post("/api/v1/ranking/configs/{id}/formula/preview", configId)
                        .with(user("prof").roles("PROFESSOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"WEIGHTED_MEAN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preset").value("WEIGHTED_MEAN"));
    }

    @Test
    void expressionFieldReturns400ProblemDetail() throws Exception {
        UUID configId = UUID.randomUUID();
        when(formulaPresetService.applyPreset(eq(configId), any())).thenThrow(
                new FieldValidationException(List.of(
                        new FieldErrorDetail("expression", "free-form expressions are not allowed in MVP", "FREE_FORM_FORBIDDEN"))));

        mockMvc.perform(post("/api/v1/ranking/configs/{id}/formula", configId)
                        .with(user("prof").roles("PROFESSOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"WEIGHTED_MEAN\",\"expression\":\"fit * 2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("expression"));
    }
}
