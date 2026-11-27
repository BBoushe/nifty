package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * REQ-EP-03 / Decision 2.11: deterministic confidence calculation per (entry, skill) from
 * measurable signals only (never LLM self-report). Zero Java Streams (Coding Standard #3).
 */
@Component
public class ConfidenceCalculator {

    private static final double WEIGHT_JUDGE_AGREEMENT = 0.30;
    private static final double WEIGHT_QUOTE_RATIO = 0.25;
    private static final double WEIGHT_EVIDENCE_COUNT = 0.15;
    private static final double WEIGHT_ORIGIN = 0.10;
    private static final double WEIGHT_ANSWER_LENGTH = 0.10;
    private static final double WEIGHT_TERMS = 0.10;

    public record ConfidenceSignals(
            int verifiedEvidenceCount,
            int totalQuoteAttempts,
            Integer run1Level,
            Integer run2Level,
            EntryOrigin questionOrigin,
            int answerLength,
            int promptedTermsCount,
            int unpromptedTermsCount
    ) {
    }

    public BigDecimal calculate(ConfidenceSignals signals) {
        double judgeAgreement = judgeAgreementFactor(signals.run1Level(), signals.run2Level());
        double quoteRatio = signals.totalQuoteAttempts() <= 0
                ? 0.0
                : Math.min(1.0, Math.max(0.0, (double) signals.verifiedEvidenceCount() / signals.totalQuoteAttempts()));
        double evidenceFactor = evidenceCountFactor(signals.verifiedEvidenceCount());
        double originFactor = originFactor(signals.questionOrigin());
        double lengthFactor = answerLengthFactor(signals.answerLength());
        double termsFactor = termsFactor(signals.promptedTermsCount(), signals.unpromptedTermsCount());

        double raw = WEIGHT_JUDGE_AGREEMENT * judgeAgreement
                + WEIGHT_QUOTE_RATIO * quoteRatio
                + WEIGHT_EVIDENCE_COUNT * evidenceFactor
                + WEIGHT_ORIGIN * originFactor
                + WEIGHT_ANSWER_LENGTH * lengthFactor
                + WEIGHT_TERMS * termsFactor;

        double clamped = Math.min(1.0, Math.max(0.0, raw));
        return BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_UP);
    }

    private static double judgeAgreementFactor(Integer run1, Integer run2) {
        if (run1 == null || run2 == null) {
            return 0.0;
        }
        int diff = Math.abs(run1 - run2);
        if (diff == 0) {
            return 1.0;
        }
        if (diff == 1) {
            return 0.5;
        }
        return 0.0;
    }

    private static double evidenceCountFactor(int count) {
        if (count <= 0) {
            return 0.0;
        }
        if (count == 1) {
            return 0.7;
        }
        return 1.0;
    }

    private static double originFactor(EntryOrigin origin) {
        if (origin == null || origin == EntryOrigin.CORE) {
            return 1.0;
        }
        if (origin == EntryOrigin.FOLLOW_UP) {
            return 0.85;
        }
        return 0.70;
    }

    private static double answerLengthFactor(int answerLength) {
        if (answerLength < VerbatimQuoteVerifier.MIN_QUOTE_LENGTH) {
            return 0.0;
        }
        if (answerLength < 40) {
            return 0.4;
        }
        if (answerLength < 120) {
            return 0.75;
        }
        return 1.0;
    }

    private static double termsFactor(int promptedCount, int unpromptedCount) {
        if (unpromptedCount > 0) {
            return 1.0;
        }
        if (promptedCount > 0) {
            return 0.60;
        }
        return 0.75;
    }
}
