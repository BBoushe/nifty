package de.fhdortmund.nifty.teams.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import de.fhdortmund.nifty.teams.service.TeamObjectiveCalculator.Params;
import de.fhdortmund.nifty.teams.service.TeamObjectiveCalculator.TeamDiagnostics;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** US-TB-02, REQ-TB-06, REQ-TB-10, REQ-TB-11, Decisions 1.20, 2.23, 4.3: pure-JVM objective maths. */
class TeamObjectiveCalculatorTest {

    private static final double EPS = 1e-9;
    private static final double[] DEFAULT_WEIGHTS = {0.30, 0.25, 0.20, 0.15, 0.10};

    /** One hard skill (column 0) plus five trait columns: trait 0 follows {@code trait0}, the rest sit at 0.5. */
    private static double[][] matrix(double[] hard, double[] trait0) {
        double[][] values = new double[hard.length][6];
        for (int i = 0; i < hard.length; i++) {
            values[i][0] = hard[i];
            values[i][1] = trait0[i];
            for (int t = 2; t < 6; t++) {
                values[i][t] = 0.5;
            }
        }
        return values;
    }

    private static boolean[][] allHave(int n) {
        boolean[][] has = new boolean[n][6];
        for (boolean[] row : has) {
            java.util.Arrays.fill(row, true);
        }
        return has;
    }

    private static TeamObjectiveCalculator calc(double[][] values, boolean[][] has, GroupingMode mode,
            boolean bfi10, double hardWeight, double alpha) {
        return new TeamObjectiveCalculator(values, has, 1, 2,
                new Params(mode, bfi10, hardWeight, alpha, DEFAULT_WEIGHTS));
    }

    private static final double[] PATTERN = {0, 1, 0, 1};
    private static final double[] FLAT = {0.5, 0.5, 0.5, 0.5};

    @Test
    void normalisesHardSkillsFromZeroToFourAndTraitsFromOneToFiveWithoutInverting() {
        assertThat(TeamObjectiveCalculator.normalizeSkill(0)).isEqualTo(0.0);
        assertThat(TeamObjectiveCalculator.normalizeSkill(2)).isEqualTo(0.5);
        assertThat(TeamObjectiveCalculator.normalizeSkill(4)).isEqualTo(1.0);
        assertThat(TeamObjectiveCalculator.normalizeTrait(1)).isEqualTo(0.0);
        assertThat(TeamObjectiveCalculator.normalizeTrait(3)).isEqualTo(0.5);
        assertThat(TeamObjectiveCalculator.normalizeTrait(5)).isEqualTo(1.0);
        // Decision 4.3: a reverse-scored neuroticism mean of 4.0 stays 0.75; it is never reversed again.
        assertThat(TeamObjectiveCalculator.normalizeTrait(4.0)).isEqualTo(0.75);
    }

    @Test
    void presetsGiveEngineeringDefaultEqualAndNormalisedCustomTraitWeights() {
        assertThat(TeamObjectiveCalculator.traitWeights(SoftPreset.ENGINEERING_DEFAULT, Map.of()))
                .containsExactly(0.30, 0.25, 0.20, 0.15, 0.10);
        assertThat(TeamObjectiveCalculator.traitWeights(SoftPreset.EQUAL_WEIGHTS, Map.of()))
                .containsExactly(0.2, 0.2, 0.2, 0.2, 0.2);
        double[] custom = TeamObjectiveCalculator.traitWeights(SoftPreset.CUSTOM,
                Map.of("conscientiousness", 3.0, "agreeableness", 1.0, "neuroticism", 0.0,
                        "extraversion", 0.0, "openness", 0.0));
        assertThat(custom).containsExactly(0.75, 0.25, 0.0, 0.0, 0.0);
    }

    @Test
    void hardSkillsBalancedTeamsScoreLowerImbalanceAndHigherDiversity() {
        double[][] values = matrix(PATTERN, FLAT);
        TeamObjectiveCalculator calc = calc(values, allHave(4), GroupingMode.HARD_SKILLS, true, 0.5, 0.5);

        TeamDiagnostics balanced = calc.diagnostics(new int[] {0, 0, 1, 1});
        assertThat(balanced.imbalance()).isCloseTo(0.0, within(EPS));
        assertThat(balanced.diversity()).isCloseTo(1.0, within(EPS));
        assertThat(balanced.cost()).isCloseTo(-0.5, within(EPS));

        TeamDiagnostics skewed = calc.diagnostics(new int[] {0, 1, 0, 1});
        assertThat(skewed.imbalance()).isCloseTo(0.5, within(EPS));
        assertThat(skewed.diversity()).isCloseTo(0.0, within(EPS));
        assertThat(calc.cost(new int[] {0, 1, 0, 1})).isCloseTo(0.25, within(EPS));
    }

    @Test
    void hardSkillsModeIgnoresTraitsEvenWhenBfi10IsOn() {
        double[][] values = matrix(FLAT, PATTERN);
        TeamObjectiveCalculator calc = calc(values, allHave(4), GroupingMode.HARD_SKILLS, true, 0.5, 1.0);
        assertThat(calc.cost(new int[] {0, 1, 0, 1})).isCloseTo(0.0, within(EPS));
    }

    @Test
    void softSkillsModeWeightsTraitImbalanceAndDiversityByPreset() {
        double[][] values = matrix(FLAT, PATTERN);
        // Only trait 0 (weight 0.30 of 1.00) is unbalanced: imbalance 0.5 * 0.30.
        assertThat(calc(values, allHave(4), GroupingMode.SOFT_SKILLS, true, 0.5, 1.0)
                .cost(new int[] {0, 1, 0, 1})).isCloseTo(0.15, within(EPS));
        // Diversity of trait 0 is 1.0 * 0.30 in the mixed teams; cost = -(1 - 0) * 0.30 with alpha 0.
        assertThat(calc(values, allHave(4), GroupingMode.SOFT_SKILLS, true, 0.5, 0.0)
                .cost(new int[] {0, 0, 1, 1})).isCloseTo(-0.30, within(EPS));
    }

    @Test
    void combinedModeMixesHardAndSoftTermsWithCombinedHardWeight() {
        double[][] values = matrix(PATTERN, PATTERN);
        // hard imbalance 0.5, soft imbalance 0.5 * 0.30 = 0.15; alpha = 1 keeps only imbalance.
        assertThat(calc(values, allHave(4), GroupingMode.COMBINED, true, 0.6, 1.0)
                .cost(new int[] {0, 1, 0, 1})).isCloseTo(0.6 * 0.5 + 0.4 * 0.15, within(EPS));
    }

    @Test
    void traitTermIsDroppedWhenBfi10IsOffEvenInCombinedMode() {
        double[][] values = matrix(PATTERN, PATTERN);
        assertThat(calc(values, allHave(4), GroupingMode.COMBINED, false, 0.6, 1.0)
                .cost(new int[] {0, 1, 0, 1})).isCloseTo(0.5, within(EPS));
    }

    @Test
    void naCandidateIsLeftOutOfThatSkillOnlyAndNeverTreatedAsZero() {
        double[][] values = matrix(new double[] {0.0, 1.0, 1.0, 1.0}, FLAT);
        boolean[][] has = allHave(4);
        has[0][0] = false;
        TeamObjectiveCalculator calc = calc(values, has, GroupingMode.HARD_SKILLS, false, 0.5, 1.0);

        TeamDiagnostics diagnostics = calc.diagnostics(new int[] {0, 0, 1, 1});

        assertThat(diagnostics.imbalance()).isCloseTo(0.0, within(EPS)); // 0 would give team 0 mean 0.5
        assertThat(diagnostics.teamMeans()[0][0]).isEqualTo(1.0);
        assertThat(diagnostics.naCounts()[0][0]).isEqualTo(1);
        assertThat(diagnostics.naCounts()[1][0]).isZero();
        assertThat(diagnostics.teamSizes()).containsExactly(2, 2);
    }

    @Test
    void teamWithoutAnyScoredMemberIsSkippedAndCostStaysFinite() {
        double[][] values = matrix(new double[] {0.0, 0.0, 1.0, 1.0}, FLAT);
        boolean[][] has = allHave(4);
        has[0][0] = false;
        has[1][0] = false;
        TeamObjectiveCalculator calc = calc(values, has, GroupingMode.HARD_SKILLS, false, 0.5, 0.5);

        TeamDiagnostics diagnostics = calc.diagnostics(new int[] {0, 0, 1, 1});

        assertThat(diagnostics.teamMeans()[0][0]).isNaN();
        assertThat(diagnostics.naCounts()[0][0]).isEqualTo(2);
        assertThat(diagnostics.imbalance()).isCloseTo(0.0, within(EPS));
        assertThat(Double.isFinite(diagnostics.cost())).isTrue();
    }

    @Test
    void cohortWithNoScoreForAnyHardSkillStillEvaluates() {
        boolean[][] has = allHave(4);
        for (boolean[] row : has) {
            row[0] = false;
        }
        TeamObjectiveCalculator calc = calc(matrix(FLAT, FLAT), has, GroupingMode.HARD_SKILLS, false, 0.5, 0.5);
        assertThat(calc.cost(new int[] {0, 0, 1, 1})).isEqualTo(0.0);
    }

    @Test
    void rejectsOutOfRangeAlphaNegativeWeightsAndBadTeamCount() {
        double[][] values = matrix(FLAT, FLAT);
        boolean[][] has = allHave(4);
        assertThatThrownBy(() -> new TeamObjectiveCalculator(values, has, 1, 2,
                new Params(GroupingMode.HARD_SKILLS, false, 0.5, 1.2, DEFAULT_WEIGHTS)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TeamObjectiveCalculator(values, has, 1, 2,
                new Params(GroupingMode.HARD_SKILLS, false, 0.5, 0.5, new double[] {-1, 1, 0, 0, 0})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TeamObjectiveCalculator(values, has, 1, 0,
                new Params(GroupingMode.HARD_SKILLS, false, 0.5, 0.5, DEFAULT_WEIGHTS)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
