package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * REQ-EP-07, REQ-SM-01, REQ-SM-05, REQ-SM-06 / Decisions 2.10, 2.11, 2.24:
 * per-question threshold flagging, paste/injection flagging, judge disagreement,
 * N/A exemption from confidence threshold, and threshold-gated auto_released.
 */
class ReleaseGateEvaluatorTest {

    private final ReleaseGateEvaluator evaluator = new ReleaseGateEvaluator();

    @Test
    void flagsPasteSuspectedAndInjectionSuspected() {
        ReleaseGateEvaluator.FlagEvaluation paste = evaluator.evaluateResponseFlags(
                true, true, true, 3, 3, new BigDecimal("0.9500"), new BigDecimal("0.6000"),
                true, false, false);
        assertThat(paste.reviewStatus()).isEqualTo(ReleaseStatus.FLAGGED);
        assertThat(paste.flagReasons()).contains("PASTE_SUSPECTED");

        ReleaseGateEvaluator.FlagEvaluation injection = evaluator.evaluateResponseFlags(
                true, true, true, 3, 3, new BigDecimal("0.9500"), new BigDecimal("0.6000"),
                false, true, false);
        assertThat(injection.reviewStatus()).isEqualTo(ReleaseStatus.FLAGGED);
        assertThat(injection.flagReasons()).contains("INJECTION_SUSPECTED");
    }

    @Test
    void flagsJudgeDisagreementLowConfidenceAndUnverifiedSubstantiveQuote() {
        ReleaseGateEvaluator.FlagEvaluation disagreement = evaluator.evaluateResponseFlags(
                true, true, true, 2, 4, new BigDecimal("0.9000"), new BigDecimal("0.6000"),
                false, false, false);
        assertThat(disagreement.reviewStatus()).isEqualTo(ReleaseStatus.FLAGGED);
        assertThat(disagreement.flagReasons()).contains("JUDGE_DISAGREEMENT");

        ReleaseGateEvaluator.FlagEvaluation lowConfidence = evaluator.evaluateResponseFlags(
                true, true, true, 2, 2, new BigDecimal("0.4200"), new BigDecimal("0.6000"),
                false, false, false);
        assertThat(lowConfidence.reviewStatus()).isEqualTo(ReleaseStatus.FLAGGED);
        assertThat(lowConfidence.flagReasons()).contains("LOW_CONFIDENCE");

        ReleaseGateEvaluator.FlagEvaluation unverifiedQuote = evaluator.evaluateResponseFlags(
                true, false, false, 2, 2, new BigDecimal("0.8000"), new BigDecimal("0.6000"),
                false, false, false);
        assertThat(unverifiedQuote.reviewStatus()).isEqualTo(ReleaseStatus.FLAGGED);
        assertThat(unverifiedQuote.flagReasons()).contains("UNVERIFIED_QUOTE");
    }

    @Test
    void naScoreDoesNotFailQuestionConfidenceThreshold() {
        // Non-substantive (< 8 chars) / N/A response has low confidence, but N/A does not fail threshold (REQ-SM-01, REQ-SM-05)
        ReleaseGateEvaluator.FlagEvaluation naEval = evaluator.evaluateResponseFlags(
                false, false, false, null, null, new BigDecimal("0.1000"), new BigDecimal("0.8000"),
                false, false, false);
        assertThat(naEval.reviewStatus()).isEqualTo(ReleaseStatus.PENDING);
        assertThat(naEval.flagReasons()).isNull();
    }

    @Test
    void unknownConfidenceOrThresholdOnAScoredResponseFailsClosed() {
        ReleaseGateEvaluator.FlagEvaluation noConfidence = evaluator.evaluateResponseFlags(
                true, true, true, 3, 3, null, new BigDecimal("0.6000"), false, false, false);
        assertThat(noConfidence.reviewStatus()).isEqualTo(ReleaseStatus.FLAGGED);
        assertThat(noConfidence.flagReasons()).contains("LOW_CONFIDENCE");

        ReleaseGateEvaluator.FlagEvaluation noThreshold = evaluator.evaluateResponseFlags(
                true, true, true, 3, 3, new BigDecimal("0.9000"), null, false, false, false);
        assertThat(noThreshold.reviewStatus()).isEqualTo(ReleaseStatus.FLAGGED);

        // An N/A response (no verified level) still never fails a threshold, even when both are unknown.
        ReleaseGateEvaluator.FlagEvaluation na = evaluator.evaluateResponseFlags(
                false, false, false, null, null, null, null, false, false, false);
        assertThat(na.reviewStatus()).isEqualTo(ReleaseStatus.PENDING);
    }

    @Test
    void emptyResponseListOrUnknownCoverageNeverAutoReleases() {
        ReleaseGateEvaluator.AttemptReleaseDecision empty = evaluator.evaluateAttemptRelease(
                List.of(), new BigDecimal("1.0000"), new BigDecimal("0.7000"));
        assertThat(empty.autoReleased()).isFalse();
        assertThat(empty.releaseStatus()).isEqualTo(ReleaseStatus.PENDING);

        assertThat(evaluator.evaluateAttemptRelease(
                List.of(ReleaseStatus.PENDING), null, new BigDecimal("0.7000")).autoReleased()).isFalse();
        assertThat(evaluator.evaluateAttemptRelease(
                List.of(ReleaseStatus.PENDING), new BigDecimal("0.9000"), null).autoReleased()).isFalse();
    }

    @Test
    void autoReleasesOnlyWhenZeroFlagsAndCoverageMeetsMinimum() {
        ReleaseGateEvaluator.AttemptReleaseDecision released = evaluator.evaluateAttemptRelease(
                List.of(ReleaseStatus.PENDING, ReleaseStatus.PENDING),
                new BigDecimal("0.9000"),
                new BigDecimal("0.7000"));
        assertThat(released.releaseStatus()).isEqualTo(ReleaseStatus.AUTO_RELEASED);
        assertThat(released.flaggedCount()).isZero();
        assertThat(released.autoReleased()).isTrue();

        ReleaseGateEvaluator.AttemptReleaseDecision blockedByFlag = evaluator.evaluateAttemptRelease(
                List.of(ReleaseStatus.PENDING, ReleaseStatus.FLAGGED),
                new BigDecimal("0.9000"),
                new BigDecimal("0.7000"));
        assertThat(blockedByFlag.releaseStatus()).isEqualTo(ReleaseStatus.FLAGGED);
        assertThat(blockedByFlag.flaggedCount()).isEqualTo(1);
        assertThat(blockedByFlag.autoReleased()).isFalse();

        ReleaseGateEvaluator.AttemptReleaseDecision blockedByCoverage = evaluator.evaluateAttemptRelease(
                List.of(ReleaseStatus.PENDING),
                new BigDecimal("0.6000"),
                new BigDecimal("0.7000"));
        assertThat(blockedByCoverage.releaseStatus()).isEqualTo(ReleaseStatus.FLAGGED);
        assertThat(blockedByCoverage.flaggedCount()).isZero();
        assertThat(blockedByCoverage.autoReleased()).isFalse();
    }
}
