package de.fhdortmund.nifty.teams.web;

import de.fhdortmund.nifty.teams.service.GenerateTeamsRequest;
import de.fhdortmund.nifty.teams.service.RecalculateTeamsRequest;
import de.fhdortmund.nifty.teams.service.TeamBuilderService;
import de.fhdortmund.nifty.teams.service.TeamProposalResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REQ-TB-03, REQ-TB-04, REQ-TB-07, REQ-TB-08, REQ-TB-09 (Decisions 1.20, 2.23): PROFESSOR-only Team Builder endpoints.
 * Trait scores appear in the responses, so no CANDIDATE may reach them.
 */
@RestController
@RequestMapping("/api/v1/ranking")
@PreAuthorize("hasRole('PROFESSOR')")
class TeamBuilderController {

    private final TeamBuilderService service;

    TeamBuilderController(TeamBuilderService service) {
        this.service = service;
    }

    @PostMapping("/configs/{configId}/teams/generate")
    ResponseEntity<TeamProposalResponse> generate(
            @PathVariable UUID configId, @Valid @RequestBody(required = false) GenerateTeamsRequest request) {
        TeamProposalResponse proposal =
                service.generate(configId, request == null ? GenerateTeamsRequest.defaults() : request);
        return ResponseEntity.created(URI.create("/api/v1/ranking/configs/" + configId + "/teams")).body(proposal);
    }

    @PostMapping("/teams/{proposalId}/recalculate")
    TeamProposalResponse recalculate(
            @PathVariable UUID proposalId, @Valid @RequestBody RecalculateTeamsRequest request) {
        return service.recalculate(proposalId, request);
    }

    @PostMapping("/teams/{proposalId}/publish")
    TeamProposalResponse publish(@PathVariable UUID proposalId) {
        return service.publish(proposalId);
    }

    @GetMapping("/configs/{configId}/teams")
    List<TeamProposalResponse> list(@PathVariable UUID configId) {
        return service.list(configId);
    }
}
