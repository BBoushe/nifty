package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * REQ-EP-03 / Decision 2.11: deterministic confidence calculation from measurable signals,
 * never LLM self-report.
 */
class ConfidenceCalculatorTest {

    private final ConfidenceCalculator calculator = new ConfidenceCalculator();

    @Test
    void fullAgreementVerifiedCoreAnswerWithUnpromptedTermsScoresHighest() {
        BigDecimal confidence = calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                2, 2, 3, 3, EntryOrigin.CORE, 150, 0, 2));

        assertThat(confidence).isEqualByComparingTo("1.0000");
    }

    @Test
    void judgeDisagreementReducesCalculatedConfidence() {
        BigDecimal agreed = calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                2, 2, 3, 3, EntryOrigin.CORE, 100, 0, 1));
        BigDecimal offByOne = calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                2, 2, 2, 3, EntryOrigin.CORE, 100, 0, 1));
        BigDecimal offByTwo = calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                2, 2, 1, 3, EntryOrigin.CORE, 100, 0, 1));

        assertThat(agreed).isGreaterThan(offByOne);
        assertThat(offByOne).isGreaterThan(offByTwo);
        assertThat(offByTwo).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Test
    void failedQuoteVerificationAndShortAnswerLowerConfidence() {
        BigDecimal verified = calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                2, 2, 2, 2, EntryOrigin.FOLLOW_UP, 90, 1, 1));
        BigDecimal unverified = calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                0, 2, 2, 2, EntryOrigin.FOLLOW_UP, 15, 1, 0));

        assertThat(unverified).isLessThan(verified);
        assertThat(unverified).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(verified).isLessThanOrEqualTo(BigDecimal.ONE);
    }

    @Test
    void separateSkillsOnSameAnswerGetIndependentConfidences() {
        BigDecimal springBootConf = calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                2, 2, 3, 3, EntryOrigin.CORE, 140, 0, 2));
        BigDecimal testingConf = calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                1, 2, 1, 3, EntryOrigin.CORE, 140, 1, 0));

        assertThat(springBootConf).isGreaterThan(testingConf);
    }

    @Test
    void exactBoundariesForEvidenceCountOriginAnswerLengthAndTermsKillMutants() {
        // Length boundaries: 7 (<8 -> 0.0) vs 8 (>=8 -> 0.4), 39 (<40 -> 0.4) vs 40 (>=40 -> 0.75), 119 (<120 -> 0.75) vs 120 (>=120 -> 1.0)
        assertThat(calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(2, 2, 3, 3, EntryOrigin.CORE, 7, 0, 1)))
                .isEqualByComparingTo("0.9000");
        assertThat(calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(2, 2, 3, 3, EntryOrigin.CORE, 8, 0, 1)))
                .isEqualByComparingTo("0.9400");
        assertThat(calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(2, 2, 3, 3, EntryOrigin.CORE, 39, 0, 1)))
                .isEqualByComparingTo("0.9400");
        assertThat(calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(2, 2, 3, 3, EntryOrigin.CORE, 40, 0, 1)))
                .isEqualByComparingTo("0.9750");
        assertThat(calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(2, 2, 3, 3, EntryOrigin.CORE, 119, 0, 1)))
                .isEqualByComparingTo("0.9750");
        assertThat(calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(2, 2, 3, 3, EntryOrigin.CORE, 120, 0, 1)))
                .isEqualByComparingTo("1.0000");

        // Evidence count boundaries: 0 -> 0.0, 1 -> 0.7, 2 -> 1.0
        assertThat(calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(1, 2, 3, 3, EntryOrigin.CORE, 120, 0, 1)))
                .isEqualByComparingTo("0.8300");
        assertThat(calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(0, 2, 3, 3, EntryOrigin.CORE, 120, 0, 1)))
                .isEqualByComparingTo("0.6000");

        // Origin boundaries: CORE (1.0), FOLLOW_UP (0.85), INVENTED (0.70)
        assertThat(calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(2, 2, 3, 3, EntryOrigin.FOLLOW_UP, 120, 0, 1)))
                .isEqualByComparingTo("0.9850");
        assertThat(calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(2, 2, 3, 3, EntryOrigin.INVENTED, 120, 0, 1)))
                .isEqualByComparingTo("0.9700");

        // Terms boundaries: unprompted (>0 -> 1.0), prompted only (>0 -> 0.60), neither (0.75)
        assertThat(calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(2, 2, 3, 3, EntryOrigin.CORE, 120, 2, 0)))
                .isEqualByComparingTo("0.9600");
        assertThat(calculator.calculate(new ConfidenceCalculator.ConfidenceSignals(2, 2, 3, 3, EntryOrigin.CORE, 120, 0, 0)))
                .isEqualByComparingTo("0.9750");
    }
}
