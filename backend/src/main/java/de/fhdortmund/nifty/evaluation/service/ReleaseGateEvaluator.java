package de.fhdortmund.nifty.evaluation.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * REQ-EP-07, REQ-SM-01, REQ-SM-05, REQ-SM-06 / Decisions 2.10, 2.11, 2.24:
 * deterministic per-response flagging and threshold-gated auto-release evaluation.
 * Zero Java Streams (Coding Standard #3).
 */
@Component
public class ReleaseGateEvaluator {

    public record FlagEvaluation(ReleaseStatus reviewStatus, String flagReasons) {
    }

    public record AttemptReleaseDecision(ReleaseStatus releaseStatus, int flaggedCount, boolean autoReleased) {
    }

    public FlagEvaluation evaluateResponseFlags(
            boolean substantiveAnswer,
            boolean quoteVerified,
            boolean allQuotesVerified,
            Integer run1Level,
            Integer run2Level,
            BigDecimal calculatedConfidence,
            BigDecimal questionConfidenceThreshold,
            boolean pasteSuspected,
            boolean injectionSuspected,
            boolean parseFailed) {

        List<String> reasons = new ArrayList<>();

        if (pasteSuspected) {
            reasons.add("PASTE_SUSPECTED");
        }
        if (injectionSuspected) {
            reasons.add("INJECTION_SUSPECTED");
        }
        if (parseFailed) {
            reasons.add("JUDGE_PARSE_FAILED");
        }
        if (substantiveAnswer && (!quoteVerified || !allQuotesVerified)) {
            reasons.add("UNVERIFIED_QUOTE");
        }
        if (run1Level != null && run2Level != null && Math.abs(run1Level - run2Level) >= 1) {
            reasons.add("JUDGE_DISAGREEMENT");
        }

        // REQ-SM-01 / REQ-SM-05: an N/A score does not fail a question's confidence threshold.
        // Decision 2.10 fail closed: a scored response whose confidence or threshold is unknown cannot prove
        // that it meets the threshold, so it is flagged like a low-confidence response.
        boolean isScored = quoteVerified && (run1Level != null || run2Level != null);
        boolean confidenceMet = calculatedConfidence != null
                && questionConfidenceThreshold != null
                && calculatedConfidence.compareTo(questionConfidenceThreshold) >= 0;
        if (isScored && !confidenceMet) {
            reasons.add("LOW_CONFIDENCE");
        }

        if (reasons.isEmpty()) {
            return new FlagEvaluation(ReleaseStatus.PENDING, null);
        }
        return new FlagEvaluation(ReleaseStatus.FLAGGED, joinReasons(reasons));
    }

    public AttemptReleaseDecision evaluateAttemptRelease(
            List<ReleaseStatus> responseStatuses,
            BigDecimal coverage,
            BigDecimal minCoverage) {

        int flaggedCount = 0;
        for (int i = 0; i < responseStatuses.size(); i++) {
            if (responseStatuses.get(i) == ReleaseStatus.FLAGGED) {
                flaggedCount++;
            }
        }

        // Decision 2.10 fail closed: no evidence (empty list) or an unknown coverage or minimum never auto-releases.
        if (responseStatuses.isEmpty() || coverage == null || minCoverage == null) {
            return new AttemptReleaseDecision(ReleaseStatus.PENDING, flaggedCount, false);
        }
        boolean lowCoverage = coverage.compareTo(minCoverage) < 0;

        if (flaggedCount == 0 && !lowCoverage) {
            return new AttemptReleaseDecision(ReleaseStatus.AUTO_RELEASED, 0, true);
        }
        return new AttemptReleaseDecision(ReleaseStatus.FLAGGED, flaggedCount, false);
    }

    private static String joinReasons(List<String> reasons) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(reasons.get(i));
        }
        return sb.toString();
    }
}
