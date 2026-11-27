package de.fhdortmund.nifty.studio.web;

import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.AssessmentDeletionService;
import de.fhdortmund.nifty.studio.service.AssessmentDetailResponse;
import de.fhdortmund.nifty.studio.service.AssessmentReadService;
import de.fhdortmund.nifty.studio.service.AssessmentSummaryResponse;
import de.fhdortmund.nifty.studio.service.ConfigCreationService;
import de.fhdortmund.nifty.studio.service.CreateAssessmentRequest;
import de.fhdortmund.nifty.studio.service.CreatedAssessment;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REQ-IS-01, REQ-IS-07, REQ-IS-08, REQ-IS-10 (Decisions 1.19, 2.21, 4.4):
 * PROFESSOR-only one-shot assessment endpoints. No PUT or PATCH exists (405 Method Not Allowed).
 */
@RestController
@RequestMapping("/api/v1/ranking/studio/configs")
@PreAuthorize("hasRole('PROFESSOR')")
class StudioConfigController {

    private static final String BASE_PATH = "/api/v1/ranking/studio/configs";

    private final ConfigCreationService creationService;
    private final AssessmentReadService readService;
    private final AssessmentDeletionService deletionService;

    StudioConfigController(
            ConfigCreationService creationService,
            AssessmentReadService readService,
            AssessmentDeletionService deletionService) {
        this.creationService = creationService;
        this.readService = readService;
        this.deletionService = deletionService;
    }

    @PostMapping
    ResponseEntity<AssessmentDetailResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String rawIdempotencyKey,
            @RequestBody CreateAssessmentRequest request) {
        UUID idempotencyKey = parseIdempotencyKey(rawIdempotencyKey);
        CreatedAssessment result = creationService.create(idempotencyKey, request);
        AssessmentDetailResponse detail = readService.getForCurrentProfessor(result.configId());
        if (result.created()) {
            return ResponseEntity.created(URI.create(BASE_PATH + "/" + result.configId())).body(detail);
        }
        return ResponseEntity.ok(detail);
    }

    @GetMapping
    List<AssessmentSummaryResponse> list() {
        return readService.listForCurrentProfessor();
    }

    @GetMapping("/{configId}")
    AssessmentDetailResponse get(@PathVariable UUID configId) {
        return readService.getForCurrentProfessor(configId);
    }

    @DeleteMapping("/{configId}")
    ResponseEntity<Void> delete(@PathVariable UUID configId) {
        deletionService.deleteForCurrentProfessor(configId);
        return ResponseEntity.noContent().build();
    }

    private static UUID parseIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new FieldValidationException(List.of(
                    new FieldErrorDetail("Idempotency-Key", "must be a valid UUID header", "REQUIRED")));
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException(List.of(
                    new FieldErrorDetail("Idempotency-Key", "must be a valid UUID header", "OUT_OF_RANGE")));
        }
    }
}
