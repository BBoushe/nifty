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
}
