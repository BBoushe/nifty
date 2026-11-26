package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * REQ-EP-02, REQ-FE-01, REQ-FE-03, REQ-FE-06 / Decisions 2.2, 2.4, 2.12, 2.20, 2.22:
 * question-weighted Formula B with 1.0 follow-up inheritance, 0.5 invented multiplier,
 * iterative D2 0.50 cap, explicit N/A (never 0), unweighted fit, and question-weighted coverage.
 */
class SkillFormulaCalculatorTest {

    private final SkillFormulaCalculator calculator = new SkillFormulaCalculator();

    @Test
    void capsTwoCoreQuestionsAtFiftyPercentEach() {
        UUID gitSkill = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();

        SkillFormulaCalculator.FormulaResult result = calculator.calculate(
                List.of(gitSkill),
                List.of(
                        new SkillFormulaCalculator.QuestionWeight(q1, new BigDecimal("3.00")),
                        new SkillFormulaCalculator.QuestionWeight(q2, new BigDecimal("1.00"))),
                List.of(
                        scored(q1, gitSkill, EntryOrigin.CORE, "3.00", 4),
                        scored(q2, gitSkill, EntryOrigin.CORE, "1.00", 2)),
                Set.of(q1, q2),
                new BigDecimal("0.5000"));

        SkillFormulaCalculator.CalculatedSkillScore git = result.skillScores().getFirst();
        assertThat(git.status()).isEqualTo(ScoreStatus.SCORED);
        assertThat(git.score()).isEqualByComparingTo("3.00");
        assertThat(result.fitScore()).isEqualByComparingTo("3.00");
        assertThat(result.coverage()).isEqualByComparingTo("1.0000");
    }

    @Test
    void followUpInheritsFullCoreQuestionWeight() {
        UUID sqlSkill = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();

        SkillFormulaCalculator.FormulaResult result = calculator.calculate(
                List.of(sqlSkill),
                List.of(new SkillFormulaCalculator.QuestionWeight(q2, new BigDecimal("2.00"))),
                List.of(
                        scored(q2, sqlSkill, EntryOrigin.CORE, "2.00", 3),
                        scored(q2, sqlSkill, EntryOrigin.FOLLOW_UP, "2.00", 1)),
                Set.of(q2),
                new BigDecimal("0.5000"));

        assertThat(result.skillScores().getFirst().score()).isEqualByComparingTo("2.00");
    }

    @Test
    void redistributesExcessShareAcrossThreeEntries() {
        UUID dockerSkill = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID q3 = UUID.randomUUID();

        SkillFormulaCalculator.FormulaResult result = calculator.calculate(
                List.of(dockerSkill),
                List.of(
                        new SkillFormulaCalculator.QuestionWeight(q1, new BigDecimal("3.00")),
                        new SkillFormulaCalculator.QuestionWeight(q2, new BigDecimal("1.00")),
                        new SkillFormulaCalculator.QuestionWeight(q3, new BigDecimal("1.00"))),
                List.of(
                        scored(q1, dockerSkill, EntryOrigin.CORE, "3.00", 4),
                        scored(q2, dockerSkill, EntryOrigin.CORE, "1.00", 2),
                        scored(q3, dockerSkill, EntryOrigin.CORE, "1.00", 0)),
                Set.of(q1, q2, q3),
                new BigDecimal("0.5000"));

        assertThat(result.skillScores().getFirst().score()).isEqualByComparingTo("2.50");
    }

    @Test
    void untestedSkillIsExplicitNaAndExcludedFromFitAndLowCoverageCalculatedSeparately() {
        UUID springSkill = UUID.randomUUID();
        UUID untestedSkill = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID q3 = UUID.randomUUID();
        UUID q4 = UUID.randomUUID();

        // REQ-FE-03 scenario: weights 1, 1, 1, 2; q4 unanswered -> coverage = 3/5 = 0.6000
        SkillFormulaCalculator.FormulaResult result = calculator.calculate(
                List.of(springSkill, untestedSkill),
                List.of(
                        new SkillFormulaCalculator.QuestionWeight(q1, new BigDecimal("1.00")),
                        new SkillFormulaCalculator.QuestionWeight(q2, new BigDecimal("1.00")),
                        new SkillFormulaCalculator.QuestionWeight(q3, new BigDecimal("1.00")),
                        new SkillFormulaCalculator.QuestionWeight(q4, new BigDecimal("2.00"))),
                List.of(
                        scored(q1, springSkill, EntryOrigin.CORE, "1.00", 4),
                        scored(q2, springSkill, EntryOrigin.CORE, "1.00", 2),
                        scored(q3, springSkill, EntryOrigin.CORE, "1.00", 3)),
                Set.of(q1, q2, q3),
                new BigDecimal("0.5000"));

        assertThat(result.skillScores()).hasSize(2);
        SkillFormulaCalculator.CalculatedSkillScore scored = result.skillScores().get(0);
        SkillFormulaCalculator.CalculatedSkillScore na = result.skillScores().get(1);

        assertThat(scored.status()).isEqualTo(ScoreStatus.SCORED);
        assertThat(scored.score()).isEqualByComparingTo("3.00");

        assertThat(na.status()).isEqualTo(ScoreStatus.NOT_ASSESSED);
        assertThat(na.score()).isNull();
        assertThat(na.verifiedEvidenceCount()).isZero();

        // Unweighted mean over SCORED skills only -> 3.00 (NOT (3+0)/2 = 1.50!)
        assertThat(result.fitScore()).isEqualByComparingTo("3.00");
        assertThat(result.coverage()).isEqualByComparingTo("0.6000");
    }

    @Test
    void inventedQuestionUsesHalfCoreWeightAndAllNaYieldsNullFit() {
        UUID skillA = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();

        SkillFormulaCalculator.FormulaResult withInvented = calculator.calculate(
                List.of(skillA),
                List.of(new SkillFormulaCalculator.QuestionWeight(q1, new BigDecimal("2.00"))),
                List.of(
                        scored(q1, skillA, EntryOrigin.CORE, "2.00", 4),
                        scored(q1, skillA, EntryOrigin.INVENTED, "2.00", 2)),
                Set.of(q1),
                new BigDecimal("0.5000"));
        // Raw weights: 2.00 and 1.00 -> shares 0.667/0.333 -> capped at 0.50/0.50 -> 3.00
        assertThat(withInvented.skillScores().getFirst().score()).isEqualByComparingTo("3.00");

        // Unverified quote is excluded from scoring -> N/A and fit == null
        SkillFormulaCalculator.FormulaResult allNa = calculator.calculate(
                List.of(skillA),
                List.of(new SkillFormulaCalculator.QuestionWeight(q1, new BigDecimal("2.00"))),
                List.of(new SkillFormulaCalculator.ScoredEntryInput(
                        UUID.randomUUID(), q1, skillA, EntryOrigin.CORE,
                        new BigDecimal("2.00"), 3, new BigDecimal("0.4000"), false, false, "flagged")),
                Set.of(q1),
                new BigDecimal("0.5000"));
        assertThat(allNa.skillScores().getFirst().status()).isEqualTo(ScoreStatus.NOT_ASSESSED);
        assertThat(allNa.skillScores().getFirst().score()).isNull();
        assertThat(allNa.fitScore()).isNull();
        assertThat(allNa.coverage()).isEqualByComparingTo("0.0000");
    }

    @Test
    void inventedHalfMultiplierIsObservableWhenUncappedAndMetadataAndReviewStatusesPropagate() {
        UUID skillA = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();

        // 3 entries with core w=2 (lvl 4), core w=2 (lvl 4), invented w=2 -> eff w=1 (lvl 0)
        // Shares: 2/5=0.40, 2/5=0.40, 1/5=0.20 (none exceed 0.50 cap) -> score = 0.4*4 + 0.4*4 + 0.2*0 = 3.20
        SkillFormulaCalculator.FormulaResult res = calculator.calculate(
                List.of(skillA),
                List.of(
                        new SkillFormulaCalculator.QuestionWeight(q1, new BigDecimal("2.00")),
                        new SkillFormulaCalculator.QuestionWeight(q2, new BigDecimal("2.00"))),
                List.of(
                        new SkillFormulaCalculator.ScoredEntryInput(UUID.randomUUID(), q1, skillA, EntryOrigin.CORE,
                                new BigDecimal("2.00"), 4, new BigDecimal("0.8000"), true, false, "approved"),
                        new SkillFormulaCalculator.ScoredEntryInput(UUID.randomUUID(), q2, skillA, EntryOrigin.CORE,
                                new BigDecimal("2.00"), 4, new BigDecimal("1.0000"), true, false, "overridden"),
                        new SkillFormulaCalculator.ScoredEntryInput(UUID.randomUUID(), q2, skillA, EntryOrigin.INVENTED,
                                new BigDecimal("2.00"), 0, new BigDecimal("0.6000"), true, false, "auto_released")),
                Set.of(q1, q2),
                new BigDecimal("0.5000"));

        SkillFormulaCalculator.CalculatedSkillScore sc = res.skillScores().getFirst();
        assertThat(sc.score()).isEqualByComparingTo("3.20");
        assertThat(sc.avgConfidence()).isEqualByComparingTo("0.8000");
        assertThat(sc.verifiedEvidenceCount()).isEqualTo(3);
        assertThat(sc.reviewStatus()).isEqualTo("overridden");

        // Uniform guard when n * maxShare < 1.0 (e.g. 2 entries with cap 0.40 -> uniform 0.50, 0.50)
        double[] uniform = SkillFormulaCalculator.computeCappedShares(new double[] {3.0, 1.0}, 0.40);
        assertThat(uniform[0]).isEqualTo(0.5);
        assertThat(uniform[1]).isEqualTo(0.5);

        // Priority: flagged > pending > overridden > approved > auto_released
        SkillFormulaCalculator.FormulaResult approvedOnly = calculator.calculate(
                List.of(skillA),
                List.of(new SkillFormulaCalculator.QuestionWeight(q1, new BigDecimal("1.00"))),
                List.of(new SkillFormulaCalculator.ScoredEntryInput(UUID.randomUUID(), q1, skillA, EntryOrigin.CORE,
                        new BigDecimal("1.00"), 3, new BigDecimal("0.9000"), true, false, "approved")),
                Set.of(q1),
                new BigDecimal("0.5000"));
        assertThat(approvedOnly.skillScores().getFirst().reviewStatus()).isEqualTo("approved");

        SkillFormulaCalculator.FormulaResult autoOnly = calculator.calculate(
                List.of(skillA),
                List.of(new SkillFormulaCalculator.QuestionWeight(q1, new BigDecimal("1.00"))),
                List.of(new SkillFormulaCalculator.ScoredEntryInput(UUID.randomUUID(), q1, skillA, EntryOrigin.CORE,
                        new BigDecimal("1.00"), 3, new BigDecimal("0.9000"), true, false, "auto_released")),
                Set.of(q1),
                new BigDecimal("0.5000"));
        assertThat(autoOnly.skillScores().getFirst().reviewStatus()).isEqualTo("auto_released");
    }

    @Test
    void zeroWeightEntriesCarryNoShareAndAllZeroFallsBackToPlainMean() {
        UUID skill = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID q3 = UUID.randomUUID();
        List<SkillFormulaCalculator.QuestionWeight> questions = List.of(
                new SkillFormulaCalculator.QuestionWeight(q1, BigDecimal.ZERO),
                new SkillFormulaCalculator.QuestionWeight(q2, BigDecimal.ZERO),
                new SkillFormulaCalculator.QuestionWeight(q3, new BigDecimal("1.00")));

        // Decision 2.22: one positive weight is the whole score (not halved by the 0.50 cap).
        SkillFormulaCalculator.FormulaResult onePositive = calculator.calculate(
                List.of(skill), questions,
                List.of(scored(q1, skill, EntryOrigin.CORE, "0.00", 0), scored(q3, skill, EntryOrigin.CORE, "1.00", 4)),
                Set.of(q1, q3), new BigDecimal("0.5000"));
        assertThat(onePositive.skillScores().getFirst().score()).isEqualByComparingTo("4.00");

        // No positive weight at all: plain mean of the eligible levels, never 0 and never NaN.
        SkillFormulaCalculator.FormulaResult allZero = calculator.calculate(
                List.of(skill), questions,
                List.of(scored(q1, skill, EntryOrigin.CORE, "0.00", 2), scored(q2, skill, EntryOrigin.CORE, "0.00", 4)),
                Set.of(q1, q2), new BigDecimal("0.5000"));
        assertThat(allZero.skillScores().getFirst().score()).isEqualByComparingTo("3.00");
    }

    @Test
    void inventedEntryNeverCountsTowardCoverage() {
        UUID skill = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();

        SkillFormulaCalculator.FormulaResult result = calculator.calculate(
                List.of(skill),
                List.of(new SkillFormulaCalculator.QuestionWeight(q1, new BigDecimal("1.00")),
                        new SkillFormulaCalculator.QuestionWeight(q2, new BigDecimal("1.00"))),
                List.of(scored(q1, skill, EntryOrigin.CORE, "1.00", 3), scored(q2, skill, EntryOrigin.INVENTED, "1.00", 3)),
                Set.of(q1, q2), new BigDecimal("0.5000"));

        assertThat(result.coverage()).isEqualByComparingTo("0.5000");
    }

    private static SkillFormulaCalculator.ScoredEntryInput scored(
            UUID coreQuestionId, UUID skillId, EntryOrigin origin, String weight, int level) {
        return new SkillFormulaCalculator.ScoredEntryInput(
                UUID.randomUUID(),
                coreQuestionId,
                skillId,
                origin,
                new BigDecimal(weight),
                level,
                new BigDecimal("0.9000"),
                true,
                false,
                "pending");
    }
}
