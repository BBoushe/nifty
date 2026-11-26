package de.fhdortmund.nifty.evaluation.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.evaluation.service.CohortGapQueryService;
import de.fhdortmund.nifty.evaluation.service.EvaluationDtos;
import de.fhdortmund.nifty.evaluation.service.EvaluationReviewService;
import de.fhdortmund.nifty.evaluation.service.ReleaseStatus;
import de.fhdortmund.nifty.shared.config.SecurityConfig;
import de.fhdortmund.nifty.shared.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
 * US-EV-05: Web-slice tests for RankingEvaluationController verifying PROFESSOR access and
 * CANDIDATE 403 rejection under real SecurityConfig.
 */
@WebMvcTest(RankingEvaluationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class RankingEvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private EvaluationReviewService reviewService;
    @MockitoBean
    private CohortGapQueryService cohortGapQueryService;

    @Test
    void professorCanAccessAllRankingEvaluationEndpoints() throws Exception {
        UUID configId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID responseEvalId = UUID.randomUUID();

        when(reviewService.getMatrix(configId)).thenReturn(new EvaluationDtos.MatrixResponse(
                configId, UUID.randomUUID(), 1, "WEIGHTED_MEAN", new BigDecimal("0.7000"), List.of(),
                List.of(new EvaluationDtos.MatrixCandidateRow(
                        UUID.randomUUID(), "anna.schmidt", attemptId, 1, "RUNNING", null, BigDecimal.ZERO,
                        ReleaseStatus.PENDING, 0, null, List.of()))));
        when(reviewService.getAttemptDrilldown(attemptId)).thenReturn(new EvaluationDtos.AttemptEvaluationDrilldownResponse(
                attemptId, configId, UUID.randomUUID(), "anna.schmidt", 1, "COMPLETED", new BigDecimal("3.00"), BigDecimal.ONE,
                new BigDecimal("0.7000"), ReleaseStatus.AUTO_RELEASED, 0, Instant.now(), List.of(), List.of()));
        when(reviewService.approveEvaluation(responseEvalId)).thenReturn(
                new EvaluationDtos.ApproveEvaluationResponse(
                        responseEvalId, attemptId, null, ReleaseStatus.APPROVED, Instant.now(), 0));
        when(reviewService.overrideEvaluation(eq(responseEvalId), any())).thenReturn(
                new EvaluationDtos.OverrideEvaluationResponse(
                        responseEvalId, attemptId, 2, 3, ReleaseStatus.OVERRIDDEN, "Verified", Instant.now(),
                        new BigDecimal("3.00"), BigDecimal.ONE, 0));
        when(reviewService.releaseAttempt(attemptId)).thenReturn(new EvaluationDtos.ReleaseAttemptResponse(
                attemptId, ReleaseStatus.APPROVED, Instant.now(), new BigDecimal("3.00"), BigDecimal.ONE));
        when(cohortGapQueryService.getCohortGaps(configId)).thenReturn(new EvaluationDtos.CohortGapsResponse(
                configId, 4, List.of()));
        when(reviewService.exportTranscript(attemptId)).thenReturn(new EvaluationDtos.TranscriptExportResponse(
                "assessment-transcript/v1", attemptId, UUID.randomUUID(), configId, "v1", 1,
                Instant.now(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/ranking/configs/{id}/matrix", configId).with(user("p").roles("PROFESSOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulaPreset").value("WEIGHTED_MEAN"))
                .andExpect(jsonPath("$.rows[0].candidateRef").value("anna.schmidt"));

        mockMvc.perform(get("/api/v1/ranking/attempts/{id}/evaluations", attemptId).with(user("p").roles("PROFESSOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseStatus").value("auto_released"))
                .andExpect(jsonPath("$.evaluationStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.candidateRef").value("anna.schmidt"));

        mockMvc.perform(post("/api/v1/ranking/evaluations/{id}/approve", responseEvalId)
                        .with(user("p").roles("PROFESSOR"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("approved"));

        mockMvc.perform(post("/api/v1/ranking/evaluations/{id}/override", responseEvalId)
                        .with(user("p").roles("PROFESSOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newLevel\":3,\"reason\":\"Verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("overridden"));

        mockMvc.perform(post("/api/v1/ranking/attempts/{id}/release", attemptId)
                        .with(user("p").roles("PROFESSOR"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseStatus").value("approved"));

        mockMvc.perform(get("/api/v1/ranking/configs/{id}/cohort-gaps", configId).with(user("p").roles("PROFESSOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedCandidateCount").value(4));

        mockMvc.perform(get("/api/v1/ranking/transcripts/{id}", attemptId).with(user("p").roles("PROFESSOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("assessment-transcript/v1"));
    }

    @Test
    void candidateIsForbiddenFromProfessorRankingEndpoints() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/ranking/configs/{id}/matrix", id).with(user("c").roles("CANDIDATE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/ranking/transcripts/{id}", id).with(user("c").roles("CANDIDATE")))
                .andExpect(status().isForbidden());
    }
}
