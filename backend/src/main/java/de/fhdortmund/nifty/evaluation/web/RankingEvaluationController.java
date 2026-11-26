package de.fhdortmund.nifty.evaluation.web;

import de.fhdortmund.nifty.evaluation.service.CohortGapQueryService;
import de.fhdortmund.nifty.evaluation.service.EvaluationDtos;
import de.fhdortmund.nifty.evaluation.service.EvaluationReviewService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REQ-SM-01..08 / Decisions 1.18, 1.19, 2.10, 2.13, 2.15, 2.24, 2.30:
 * PROFESSOR-only endpoints for the Skills Matrix, Evidence Drawer drilldown, individual approval,
 * audited overrides, explicit attempt release, SQL Cohort Gaps, and transcript export.
 */
@RestController
@RequestMapping("/api/v1/ranking")
@PreAuthorize("hasRole('PROFESSOR')")
class RankingEvaluationController {

    private final EvaluationReviewService reviewService;
    private final CohortGapQueryService cohortGapQueryService;

    RankingEvaluationController(
            EvaluationReviewService reviewService,
            CohortGapQueryService cohortGapQueryService) {
        this.reviewService = reviewService;
        this.cohortGapQueryService = cohortGapQueryService;
    }

    @GetMapping("/configs/{configId}/matrix")
    EvaluationDtos.MatrixResponse getMatrix(@PathVariable UUID configId) {
        return reviewService.getMatrix(configId);
    }

    @GetMapping("/attempts/{attemptId}/evaluations")
    EvaluationDtos.AttemptEvaluationDrilldownResponse getAttemptEvaluations(@PathVariable UUID attemptId) {
        return reviewService.getAttemptDrilldown(attemptId);
    }

    @PostMapping("/evaluations/{responseEvalId}/approve")
    EvaluationDtos.ApproveEvaluationResponse approveEvaluation(@PathVariable UUID responseEvalId) {
        return reviewService.approveEvaluation(responseEvalId);
    }

    @PostMapping("/evaluations/{responseEvalId}/override")
    EvaluationDtos.OverrideEvaluationResponse overrideEvaluation(
            @PathVariable UUID responseEvalId,
            @RequestBody EvaluationDtos.OverrideEvaluationRequest request) {
        return reviewService.overrideEvaluation(responseEvalId, request);
    }

    @PostMapping("/attempts/{attemptId}/release")
    EvaluationDtos.ReleaseAttemptResponse releaseAttempt(@PathVariable UUID attemptId) {
        return reviewService.releaseAttempt(attemptId);
    }

    @GetMapping("/configs/{configId}/cohort-gaps")
    EvaluationDtos.CohortGapsResponse getCohortGaps(@PathVariable UUID configId) {
        return cohortGapQueryService.getCohortGaps(configId);
    }

    @GetMapping("/transcripts/{attemptId}")
    EvaluationDtos.TranscriptExportResponse exportTranscript(@PathVariable UUID attemptId) {
        return reviewService.exportTranscript(attemptId);
    }
}
