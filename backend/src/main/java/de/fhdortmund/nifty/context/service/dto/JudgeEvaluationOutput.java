package de.fhdortmund.nifty.context.service.dto;

/** Parse target of the evaluator judge JSON (REQ-EP-08, Decision 2.25). evaluation validates level 0..4. */
public record JudgeEvaluationOutput(int level, String verbatimQuote, String rationale, String suggestion) {
}
