package de.fhdortmund.nifty.evaluation.web;

import de.fhdortmund.nifty.evaluation.service.CandidateResultsService;
import de.fhdortmund.nifty.evaluation.service.EvaluationDtos;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REQ-CR-01..06 / Decisions 2.10, 2.15, 2.16, 4.3:
 * CANDIDATE-only post-release transparency endpoint scoped strictly to the signed-in invite.
 */
@RestController
@RequestMapping("/api/v1/assessment")
@PreAuthorize("hasRole('CANDIDATE')")
class CandidateResultsController {

    private final CandidateResultsService candidateResultsService;

    CandidateResultsController(CandidateResultsService candidateResultsService) {
        this.candidateResultsService = candidateResultsService;
    }

    @GetMapping("/results/{configId}")
    EvaluationDtos.CandidateResultsResponse getResults(@PathVariable UUID configId) {
        return candidateResultsService.getResultsForCurrentCandidate(configId);
    }
}
