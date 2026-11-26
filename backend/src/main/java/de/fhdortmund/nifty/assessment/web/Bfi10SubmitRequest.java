package de.fhdortmund.nifty.assessment.web;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * REQ-AC-14: 10 answers 1..5 in item order. A missing list fails here; the service names each missing or invalid
 * item, because only it knows the item numbers the candidate sees.
 */
record Bfi10SubmitRequest(@NotNull List<Integer> items) {
}
