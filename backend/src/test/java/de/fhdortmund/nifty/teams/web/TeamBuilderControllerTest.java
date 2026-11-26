package de.fhdortmund.nifty.teams.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.fhdortmund.nifty.shared.config.SecurityConfig;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.shared.exception.GlobalExceptionHandler;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.teams.service.GenerateTeamsRequest;
import de.fhdortmund.nifty.teams.service.RecalculateTeamsRequest;
import de.fhdortmund.nifty.teams.service.TeamBuilderService;
import de.fhdortmund.nifty.teams.service.TeamProposalResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
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

/** US-TB-05, REQ-TB-03, REQ-TB-04, REQ-TB-07, REQ-TB-08: PROFESSOR-only web slice under the real SecurityConfig. */
@WebMvcTest(TeamBuilderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class TeamBuilderControllerTest {

    private final UUID configId = UUID.randomUUID();
    private final UUID proposalId = UUID.randomUUID();
    private final String generateUrl = "/api/v1/ranking/configs/" + configId + "/teams/generate";
    private final String listUrl = "/api/v1/ranking/configs/" + configId + "/teams";
    private final String recalculateUrl = "/api/v1/ranking/teams/" + proposalId + "/recalculate";
    private final String publishUrl = "/api/v1/ranking/teams/" + proposalId + "/publish";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamBuilderService service;

    private TeamProposalResponse sample(String status) {
        Map<String, Double> skills = new LinkedHashMap<>();
        skills.put("git", 3.5);
        skills.put("docker", null);
        TeamProposalResponse.TeamMember member = new TeamProposalResponse.TeamMember(
                UUID.randomUUID(), "cand_001", skills, Map.of("extraversion", 2.5));
        TeamProposalResponse.TeamCard card = new TeamProposalResponse.TeamCard(1, 1, List.of(member), skills,
                Map.of("git", 0, "docker", 1), Map.of("extraversion", 2.5));
        return new TeamProposalResponse(proposalId, configId, status, 1, GroupingMode.COMBINED, 0.5, 0.6, 42L,
                Instant.parse("2026-11-26T10:00:00Z"), null,
                new TeamProposalResponse.Diagnostics(0.1, 0.2, -0.05, 1), List.of(card), List.of());
    }

    @Test
    void generateReturns201WithProposalAndAcceptsAnEmptyBodyAsDefaults() throws Exception {
        when(service.generate(eq(configId), any())).thenReturn(sample("DRAFT"));

        mockMvc.perform(post(generateUrl).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numTeams\":2,\"groupingMode\":\"COMBINED\",\"alpha\":0.4,\"seed\":42}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", listUrl))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.teams[0].members[0].traits.extraversion").value(2.5))
                .andExpect(jsonPath("$.teams[0].skillNaCounts.docker").value(1))
                .andExpect(jsonPath("$.teams[0].members[0].skillScores.docker").isEmpty());

        mockMvc.perform(post(generateUrl).with(user("prof").roles("PROFESSOR")).with(csrf()))
                .andExpect(status().isCreated());
        verify(service).generate(configId, GenerateTeamsRequest.defaults());
    }

    @Test
    void recalculateReturns200() throws Exception {
        when(service.recalculate(eq(proposalId), any(RecalculateTeamsRequest.class))).thenReturn(sample("DRAFT"));

        mockMvc.perform(post(recalculateUrl).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignments\":[{\"inviteId\":\"" + UUID.randomUUID() + "\",\"teamNo\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostics.imbalance").value(0.1));
    }

    @Test
    void publishReturns200AndListReturnsProposals() throws Exception {
        when(service.publish(proposalId)).thenReturn(sample("PUBLISHED"));
        when(service.list(configId)).thenReturn(List.of(sample("DRAFT")));

        mockMvc.perform(post(publishUrl).with(user("prof").roles("PROFESSOR")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mockMvc.perform(get(listUrl).with(user("prof").roles("PROFESSOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teams[0].members[0].traits.extraversion").value(2.5));
    }

    @Test
    void invalidRequestsReturn400ProblemDetailAndNeverReachTheService() throws Exception {
        String[] bodies = {"{\"numTeams\":0}", "{\"alpha\":1.5}", "{\"combinedHardWeight\":-0.1}",
                "{\"groupingMode\":\"NOPE\"}", "{not-json"};
        for (String body : bodies) {
            mockMvc.perform(post(generateUrl).with(user("prof").roles("PROFESSOR")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mockMvc.perform(post(generateUrl).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"numTeams\":0}"))
                .andExpect(jsonPath("$.errors[0].field").value("numTeams"));
        for (String body : new String[] {"{\"assignments\":[]}", "{\"assignments\":[{\"teamNo\":0}]}", "{}"}) {
            mockMvc.perform(post(recalculateUrl).with(user("prof").roles("PROFESSOR")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mockMvc.perform(post(recalculateUrl).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assignments\":[{\"teamNo\":0}]}"))
                .andExpect(jsonPath("$.errors.length()").value(2));
        verifyNoInteractions(service);
    }

    @Test
    void serviceValidationAndNotFoundAreMappedToProblemDetails() throws Exception {
        when(service.generate(eq(configId), any())).thenThrow(new FieldValidationException(List.of(
                new FieldErrorDetail("numTeams", "must be within 1 and the number of released candidates (12)",
                        "OUT_OF_RANGE"))));
        when(service.publish(proposalId)).thenThrow(new DomainException(HttpStatus.NOT_FOUND, "Team proposal not found."));

        mockMvc.perform(post(generateUrl).with(user("prof").roles("PROFESSOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"numTeams\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("numTeams"));
        mockMvc.perform(post(publishUrl).with(user("prof").roles("PROFESSOR")).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void candidateGets403AndAnonymousGets401OnEveryEndpoint() throws Exception {
        mockMvc.perform(post(generateUrl).with(user("cand").roles("CANDIDATE")).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post(recalculateUrl).with(user("cand").roles("CANDIDATE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post(publishUrl).with(user("cand").roles("CANDIDATE")).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(get(listUrl).with(user("cand").roles("CANDIDATE"))).andExpect(status().isForbidden());
        mockMvc.perform(get(listUrl)).andExpect(status().isUnauthorized());
        verify(service, never()).list(any());
    }
}
