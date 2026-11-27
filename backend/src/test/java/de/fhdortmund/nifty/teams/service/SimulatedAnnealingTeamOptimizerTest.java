package de.fhdortmund.nifty.teams.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.teams.service.TeamObjectiveCalculator.Params;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** US-TB-03, REQ-TB-05, REQ-TB-06, REQ-TB-09, Decision 1.20: seeded 2-opt Simulated Annealing. */
class SimulatedAnnealingTeamOptimizerTest {

    private static final double[] WEIGHTS = {0.30, 0.25, 0.20, 0.15, 0.10};

    /** Two hard skills and five traits, random in 0..1; extraversion (trait column 3 of the row) set by caller. */
    private static double[][] randomValues(int n, long seed) {
        Random random = new Random(seed);
        double[][] values = new double[n][7];
        for (double[] row : values) {
            for (int d = 0; d < row.length; d++) {
                row[d] = random.nextDouble();
            }
        }
        return values;
    }

    private static boolean[][] allHave(int n) {
        boolean[][] has = new boolean[n][7];
        for (boolean[] row : has) {
            Arrays.fill(row, true);
        }
        return has;
    }

    private static TeamObjectiveCalculator calc(double[][] values, boolean[][] has, int teams, GroupingMode mode,
            double alpha) {
        return new TeamObjectiveCalculator(values, has, 2, teams, new Params(mode, true, 0.6, alpha, WEIGHTS));
    }

    private static int[] sizes(int[] teamOf, int teams) {
        int[] sizes = new int[teams];
        for (int team : teamOf) {
            sizes[team]++;
        }
        Arrays.sort(sizes);
        return sizes;
    }

    @Test
    void twentyTwoStudentsInFiveTeamsGetSizesFiveFiveFourFourFourAndEveryoneIsPlacedOnce() {
        double[][] values = randomValues(22, 1);
        TeamObjectiveCalculator calc = calc(values, allHave(22), 5, GroupingMode.COMBINED, 0.6);

        int[] teamOf = SimulatedAnnealingTeamOptimizer.optimize(calc, 22, 5, 42L);

        assertThat(teamOf).hasSize(22).doesNotContain(-1, 5);
        assertThat(sizes(teamOf, 5)).containsExactly(4, 4, 4, 5, 5);
    }

    @Test
    void sameSeedGivesIdenticalAssignmentsAndDifferentSeedMayDiffer() {
        double[][] values = randomValues(22, 2);
        int[] first = SimulatedAnnealingTeamOptimizer.optimize(
                calc(values, allHave(22), 5, GroupingMode.COMBINED, 0.6), 22, 5, 42L);
        int[] second = SimulatedAnnealingTeamOptimizer.optimize(
                calc(values, allHave(22), 5, GroupingMode.COMBINED, 0.6), 22, 5, 42L);
        assertThat(second).containsExactly(first);
    }

    @Test
    void resultNeverCostsMoreThanTheRandomStartAndImprovesIt() {
        double[][] values = randomValues(30, 3);
        TeamObjectiveCalculator calc = calc(values, allHave(30), 6, GroupingMode.HARD_SKILLS, 0.5);
        double baseline = calc.cost(SimulatedAnnealingTeamOptimizer.initialAssignment(30, 6, 7L));

        int[] teamOf = SimulatedAnnealingTeamOptimizer.optimize(calc, 30, 6, 7L);

        assertThat(calc.cost(teamOf)).isLessThan(baseline);
    }

    @Test
    void lowAndHighSkillCandidatesAreMixedAcrossTeamsWhenOnlyBalanceMatters() {
        double[][] values = new double[8][7];
        for (int i = 0; i < 8; i++) {
            Arrays.fill(values[i], 0.5);
            values[i][0] = i < 4 ? 0.0 : 1.0;
        }
        TeamObjectiveCalculator calc = calc(values, allHave(8), 2, GroupingMode.HARD_SKILLS, 1.0);

        int[] teamOf = SimulatedAnnealingTeamOptimizer.optimize(calc, 8, 2, 1L);

        assertThat(calc.diagnostics(teamOf).imbalance()).isLessThan(1e-9);
    }

    @Test
    void introvertsAreSpreadAcrossTeamsInCombinedMode() {
        double[][] values = randomValues(20, 4);
        for (int i = 0; i < 20; i++) {
            values[i][5] = i < 4 ? 0.1 : 0.7; // extraversion is trait column 3 -> row column 2 + 3
            for (int d = 0; d < 7; d++) {
                if (d != 5) {
                    values[i][d] = 0.5;
                }
            }
        }
        TeamObjectiveCalculator calc = calc(values, allHave(20), 4, GroupingMode.COMBINED, 1.0);

        int[] teamOf = SimulatedAnnealingTeamOptimizer.optimize(calc, 20, 4, 5L);

        int[] introverts = new int[4];
        for (int i = 0; i < 4; i++) {
            introverts[teamOf[i]]++;
        }
        assertThat(Arrays.stream(introverts).max().orElseThrow()).isEqualTo(1);
    }

    @Test
    void rejectsMoreTeamsThanCandidatesAndInvalidCalculatorParameters() {
        double[][] values = randomValues(4, 5);
        TeamObjectiveCalculator calc = calc(values, allHave(4), 2, GroupingMode.HARD_SKILLS, 0.5);
        assertThatThrownBy(() -> SimulatedAnnealingTeamOptimizer.optimize(calc, 4, 5, 1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("numTeams");
        assertThatThrownBy(() -> SimulatedAnnealingTeamOptimizer.optimize(calc, 4, 0, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calc(values, allHave(4), 2, GroupingMode.HARD_SKILLS, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TeamObjectiveCalculator(values, allHave(4), 2, 2,
                new Params(GroupingMode.COMBINED, true, 0.5, 0.5, new double[] {-0.1, 0.5, 0.3, 0.2, 0.1})))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optimizerAndCalculatorSourcesUseNoJavaStreams() throws IOException {
        for (String name : new String[] {"SimulatedAnnealingTeamOptimizer", "TeamObjectiveCalculator"}) {
            String source = Files.readString(
                    Path.of("src/main/java/de/fhdortmund/nifty/teams/service/" + name + ".java"));
            assertThat(source).doesNotContain(".stream(").doesNotContain("java.util.stream");
        }
    }
}
