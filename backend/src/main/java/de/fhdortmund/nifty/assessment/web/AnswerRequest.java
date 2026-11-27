package de.fhdortmund.nifty.assessment.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * REQ-AC-20 / Decision 2.26: at most 4000 characters, equal to the prompt ceiling of context (PromptSanitizer.MAX_CHARS).
 * Empty text is allowed: the browser auto-submits whatever is typed at zero.
 */
record AnswerRequest(@NotNull @Size(max = 4000) String content) {
}
