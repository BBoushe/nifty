package de.fhdortmund.nifty.assessment.web;

import de.fhdortmund.nifty.assessment.service.AssessmentSessionService;
import de.fhdortmund.nifty.assessment.service.SessionStateResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-AC-05, 06, 14, 20 (Decisions 1.11, 1.19, 2.26): candidate interview endpoints. No transcript export here;
 * the professor export lives in {@code evaluation}.
 */
@RestController
@RequestMapping("/api/v1/assessment/sessions")
@PreAuthorize("hasRole('CANDIDATE')")
class AssessmentSessionController {

    private final AssessmentSessionService sessions;
    private final JsonMapper json;

    AssessmentSessionController(AssessmentSessionService sessions, JsonMapper json) {
        this.sessions = sessions;
        this.json = json;
    }

    /** SSE with question 1, or JSON for a SOFT_SKILLS-only start or a resumed attempt. */
    @PostMapping
    ResponseEntity<StreamingResponseBody> start() {
        return SessionReplies.toResponse(sessions.start(), json);
    }

    @PostMapping("/{attemptId}/answers")
    ResponseEntity<StreamingResponseBody> answer(@PathVariable UUID attemptId, @Valid @RequestBody AnswerRequest request) {
        return SessionReplies.toResponse(sessions.answer(attemptId, request.content()), json);
    }

    @GetMapping("/{attemptId}")
    SessionStateResponse state(@PathVariable UUID attemptId) {
        return sessions.state(attemptId);
    }

    @PostMapping("/{attemptId}/bfi10")
    ResponseEntity<Void> submitBfi10(@PathVariable UUID attemptId, @RequestBody Bfi10SubmitRequest request) {
        sessions.submitBfi10(attemptId, request.items());
        return ResponseEntity.noContent().build();
    }
}
