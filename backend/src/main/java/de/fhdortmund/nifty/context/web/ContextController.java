package de.fhdortmund.nifty.context.web;

import de.fhdortmund.nifty.context.service.ContextCreationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Professor prompt preview (Decision 1.18). SecurityConfig limits {@code /api/v1/ranking/**} to PROFESSOR. */
@RestController
@RequestMapping("/api/v1/ranking/context")
class ContextController {

    private final ContextCreationService contextCreation;

    ContextController(ContextCreationService contextCreation) {
        this.contextCreation = contextCreation;
    }

    @PostMapping("/preview/interviewer")
    ContextPreviewResponse previewInterviewer(@Valid @RequestBody InterviewerPreviewRequest request) {
        return ContextPreviewResponse.from(contextCreation.buildInterviewerContext(request.toInput()));
    }
}
