package de.fhdortmund.nifty.evaluation.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.evaluation.service.CandidateResultsService;
import de.fhdortmund.nifty.evaluation.service.EvaluationDtos;
import de.fhdortmund.nifty.evaluation.service.ReleaseStatus;
import de.fhdortmund.nifty.shared.config.SecurityConfig;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.GlobalExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US-EV-05 / REQ-CR-01..03: Web-slice tests for CandidateResultsController.
 */
@WebMvcTest(CandidateResultsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class CandidateResultsControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CandidateResultsService candidateResultsService;

    @Test
    void candidateGetsPendingBeforeReleaseAnd403ForProfessorOrOtherConfig() throws Exception {
        UUID configId = UUID.randomUUID();
        UUID otherConfig = UUID.randomUUID();

        when(candidateResultsService.getResultsForCurrentCandidate(configId)).thenReturn(
                new EvaluationDtos.CandidateResultsResponse(
                        configId, null, ReleaseStatus.PENDING, false, null,
                        null, null, null, null, null, null, null, null, null, null));
        when(candidateResultsService.getResultsForCurrentCandidate(otherConfig)).thenThrow(
                new DomainException(HttpStatus.FORBIDDEN, "You may only view results for your own assessment."));

        mockMvc.perform(get("/api/v1/assessment/results/{id}", configId).with(user("cand").roles("CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(false))
                .andExpect(jsonPath("$.releaseStatus").value("pending"))
                .andExpect(jsonPath("$.fitScore").doesNotExist());

        mockMvc.perform(get("/api/v1/assessment/results/{id}", otherConfig).with(user("cand").roles("CANDIDATE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/assessment/results/{id}", configId).with(user("prof").roles("PROFESSOR")))
                .andExpect(status().isForbidden());
    }
}
