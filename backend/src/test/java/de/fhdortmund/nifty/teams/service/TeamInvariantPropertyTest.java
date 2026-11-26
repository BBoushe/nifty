package de.fhdortmund.nifty.teams.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.assessment.service.StudentSoftSkillProfile;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.auth.service.InviteService;
import de.fhdortmund.nifty.evaluation.service.CandidateHardSkillProfile;
import de.fhdortmund.nifty.evaluation.service.CandidateProfilesView;
import de.fhdortmund.nifty.evaluation.service.EvaluationQueryService;
import de.fhdortmund.nifty.evaluation.service.ReleaseStatus;
import de.fhdortmund.nifty.evaluation.service.ScoreStatus;
import de.fhdortmund.nifty.evaluation.service.SkillScoreView;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import de.fhdortmund.nifty.studio.service.view.TeamView;
import de.fhdortmund.nifty.teams.repository.TeamMemberRepository;
import de.fhdortmund.nifty.teams.repository.TeamProposalEntity;
import de.fhdortmund.nifty.teams.repository.TeamProposalRepository;
import de.fhdortmund.nifty.teams.service.TeamObjectiveCalculator.Params;
import de.fhdortmund.nifty.teams.service.TeamObjectiveCalculator.TeamDiagnostics;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Invariant (property) tests of the {@code teams} slice (REQ-TB-03, -05, -06, -09, -10, -11; AGENTS.md invariants 1
 * and 2). Plain JUnit loops over seeded random worlds ({@code new Random(20261126L)}): every failure reproduces.
 * Masked (N/A) cells always carry poison values (NaN, 999, -50, Infinity), so any read of a masked cell shows up.
 * The oracles here are written from the spec in a different style than production (lists, sorted-gap formula).
 */
class TeamInvariantPropertyTest {

    private static final long SEED = 20261126L;
    private static final int CASES = 300;
    private static final GroupingMode[] MODES = GroupingMode.values();
    private static final double[] NA_RATES = {0.0, 0.15, 0.5, 0.9, 1.0};
    private static final double[] POISON = {Double.NaN, 999.0, -50.0, Double.POSITIVE_INFINITY};
    private static final String[] TRAITS = TeamObjectiveCalculator.TRAITS;

    // ---------------------------------------------------------------------------------------------------------
    // calculator-level worlds
    // ---------------------------------------------------------------------------------------------------------

    private record Inst(int n, int g, int hard, double[][] values, boolean[][] has, Params params) {
        int dims() {
            return hard + TRAITS.length;
        }

        TeamObjectiveCalculator calc() {
            return new TeamObjectiveCalculator(values, has, hard, g, params);
        }

        Inst with(double[][] newValues, boolean[][] newHas) {
            return new Inst(newValues.length, g, hard, newValues, newHas, params);
        }

        Inst with(Params newParams) {
            return new Inst(n, g, hard, values, has, newParams);
        }
    }

    private static int size(Random rnd) {
        return 1 + rnd.nextInt(1 + rnd.nextInt(45));
    }

    private static int teams(Random rnd, int n) {
        int pick = rnd.nextInt(6);
        return pick == 0 ? 1 : pick == 1 ? n : 1 + rnd.nextInt(n);
    }

    private static double unit(Random rnd) {
        int pick = rnd.nextInt(6);
        return pick == 0 ? 0.0 : pick == 1 ? 1.0 : rnd.nextDouble();
    }

    private static double[] weights(Random rnd) {
        double[] w = new double[TRAITS.length];
        double sum = 0;
        for (int t = 0; t < w.length; t++) {
            w[t] = rnd.nextInt(4) == 0 ? 0 : rnd.nextDouble() + 0.05;
            sum += w[t];
        }
        if (sum == 0) {
            w[rnd.nextInt(w.length)] = 1;
            sum = 1;
        }
        for (int t = 0; t < w.length; t++) {
            w[t] /= sum;
        }
        return w;
    }

    private static Params params(Random rnd, boolean bfi, GroupingMode mode) {
        return new Params(mode, bfi, unit(rnd), unit(rnd), weights(rnd));
    }

    private static double poison(Random rnd) {
        return POISON[rnd.nextInt(POISON.length)];
    }

    /** Random world: 0..8 hard skills, five traits, random N/A rate, some fully N/A columns, poison behind the mask. */
    private static Inst inst(Random rnd, int n, int g) {
        int hard = rnd.nextInt(9);
        int dims = hard + TRAITS.length;
        double pNa = NA_RATES[rnd.nextInt(NA_RATES.length)];
        boolean steps = rnd.nextBoolean();
        double[][] values = new double[n][dims];
        boolean[][] has = new boolean[n][dims];
        for (int d = 0; d < dims; d++) {
            boolean blankColumn = rnd.nextInt(5) == 0;
            for (int i = 0; i < n; i++) {
                has[i][d] = !blankColumn && rnd.nextDouble() >= pNa;
                values[i][d] = has[i][d] ? steps ? rnd.nextInt(5) / 4.0 : rnd.nextDouble() : poison(rnd);
            }
        }
        return new Inst(n, g, hard, values, has, params(rnd, rnd.nextBoolean(), MODES[rnd.nextInt(MODES.length)]));
    }

    private static double[][] copyValues(double[][] values) {
        double[][] copy = new double[values.length][];
        for (int i = 0; i < values.length; i++) {
            copy[i] = values[i].clone();
        }
        return copy;
    }

    /** Same world with every masked cell set to {@code poison}. */
    private static Inst repoisoned(Inst in, double poison) {
        double[][] values = copyValues(in.values());
        for (int i = 0; i < in.n(); i++) {
            for (int d = 0; d < in.dims(); d++) {
                if (!in.has()[i][d]) {
                    values[i][d] = poison;
                }
            }
        }
        return in.with(values, in.has());
    }

    private static Inst withExtraAllNaCandidate(Inst in, Random rnd) {
        double[][] values = new double[in.n() + 1][];
        boolean[][] has = new boolean[in.n() + 1][];
        for (int i = 0; i < in.n(); i++) {
            values[i] = in.values()[i];
            has[i] = in.has()[i];
        }
        values[in.n()] = new double[in.dims()];
        has[in.n()] = new boolean[in.dims()];
        for (int d = 0; d < in.dims(); d++) {
            values[in.n()][d] = poison(rnd);
        }
        return in.with(values, has);
    }

    private static int[] looseAssignment(Random rnd, int n, int g) {
        int[] teamOf = new int[n];
        for (int i = 0; i < n; i++) {
            teamOf[i] = rnd.nextInt(g);
        }
        return teamOf;
    }

    private static int[] sizes(int[] teamOf, int g) {
        int[] sizes = new int[g];
        for (int team : teamOf) {
            sizes[team]++;
        }
        return sizes;
    }

    // ---------------------------------------------------------------------------------------------------------
    // independent oracle (spec wording: REQ-TB-06, -10, -11)
    // ---------------------------------------------------------------------------------------------------------

    private static List<Double> column(Inst in, int d, int[] teamOf, int team) {
        List<Double> scored = new ArrayList<>();
        for (int i = 0; i < in.n(); i++) {
            if ((teamOf == null || teamOf[i] == team) && in.has()[i][d]) {
                scored.add(in.values()[i][d]);
            }
        }
        return scored;
    }

    private static double average(List<Double> list) {
        double sum = 0;
        for (double v : list) {
            sum += v;
        }
        return sum / list.size();
    }

    /** Mean absolute gap over all pairs, via the sorted-order identity sum_k v_k * (2k - m + 1). */
    private static double meanPairGap(List<Double> list) {
        List<Double> sorted = new ArrayList<>(list);
        Collections.sort(sorted);
        int m = sorted.size();
        double total = 0;
        for (int k = 0; k < m; k++) {
            total += sorted.get(k) * (2 * k - m + 1);
        }
        return total / (m * (m - 1) / 2.0);
    }

    /** Returns {imbalance, diversity, cost}. */
    private static double[] oracle(Inst in, int[] teamOf) {
        Params p = in.params();
        boolean softOn = p.bfi10Enabled() && p.mode() != GroupingMode.HARD_SKILLS;
        double hw = p.combinedHardWeight();
        double[] share = !softOn ? new double[] {1.0, 0.0}
                : p.mode() == GroupingMode.SOFT_SKILLS ? new double[] {0.0, 1.0} : new double[] {hw, 1.0 - hw};
        double imbalance = 0;
        double diversity = 0;
        for (int group = 0; group < 2; group++) {
            if (share[group] == 0) {
                continue;
            }
            int from = group == 0 ? 0 : in.hard();
            int to = group == 0 ? in.hard() : in.dims();
            double imbNum = 0;
            double imbDen = 0;
            double divNum = 0;
            double divDen = 0;
            for (int d = from; d < to; d++) {
                double w = group == 0 ? 1.0 / in.hard() : p.traitWeights()[d - in.hard()];
                List<Double> cohort = column(in, d, null, -1);
                if (w <= 0 || cohort.isEmpty()) {
                    continue;
                }
                double cohortMean = average(cohort);
                List<Double> deviations = new ArrayList<>();
                List<Double> gaps = new ArrayList<>();
                for (int team = 0; team < in.g(); team++) {
                    List<Double> mine = column(in, d, teamOf, team);
                    if (mine.isEmpty()) {
                        continue;
                    }
                    deviations.add(Math.abs(average(mine) - cohortMean));
                    if (mine.size() >= 2) {
                        gaps.add(meanPairGap(mine));
                    }
                }
                imbNum += w * average(deviations);
                imbDen += w;
                if (!gaps.isEmpty()) {
                    divNum += w * average(gaps);
                    divDen += w;
                }
            }
            imbalance += share[group] * (imbDen == 0 ? 0 : imbNum / imbDen);
            diversity += share[group] * (divDen == 0 ? 0 : divNum / divDen);
        }
        return new double[] {imbalance, diversity, p.alpha() * imbalance - (1 - p.alpha()) * diversity};
    }

    private static void checkAgainstOracle(Inst in, int[] teamOf, TeamDiagnostics diag) {
        double[] expected = oracle(in, teamOf);
        assertThat(diag.imbalance()).isCloseTo(expected[0], within(1e-9));
        assertThat(diag.diversity()).isCloseTo(expected[1], within(1e-9));
        assertThat(diag.cost()).isCloseTo(expected[2], within(1e-9));
    }

    /** Independent per-team N/A count and mean (REQ-TB-11) against the calculator's diagnostics. */
    private static void checkNaCountsAndMeans(Inst in, int[] teamOf, TeamDiagnostics diag) {
        for (int g = 0; g < in.g(); g++) {
            int teamSize = 0;
            for (int i = 0; i < in.n(); i++) {
                if (teamOf[i] == g) {
                    teamSize++;
                }
            }
            assertThat(diag.teamSizes()[g]).isEqualTo(teamSize);
            for (int d = 0; d < in.dims(); d++) {
                List<Double> scored = column(in, d, teamOf, g);
                assertThat(diag.naCounts()[g][d]).isEqualTo(teamSize - scored.size());
                if (scored.isEmpty()) {
                    assertThat(diag.teamMeans()[g][d]).isNaN();
                } else {
                    assertThat(diag.teamMeans()[g][d]).isCloseTo(average(scored), within(1e-12));
                }
            }
        }
    }

    private static void assertFinite(double... values) {
        for (double v : values) {
            assertThat(Double.isFinite(v)).as("finite: %s", v).isTrue();
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // 1. placement and sizes
    // ---------------------------------------------------------------------------------------------------------

    private static void assertPlacedOnceWithBalancedSizes(int[] teamOf, int n, int g) {
        assertThat(teamOf).hasSize(n);
        int[] sizes = new int[g];
        for (int team : teamOf) {
            assertThat(team).isBetween(0, g - 1);
            sizes[team]++;
        }
        int floor = n / g;
        int ceil = (n + g - 1) / g;
        int ceilTeams = 0;
        for (int size : sizes) {
            assertThat(size).isIn(floor, ceil);
            if (size == ceil && ceil != floor) {
                ceilTeams++;
            }
        }
        assertThat(ceilTeams).isEqualTo(floor == ceil ? 0 : n % g);
    }

    @Test
    void everyCandidateIsPlacedExactlyOnceAndOptimizerSizesAreFloorOrCeil() {
        Random rnd = new Random(SEED);
        for (int c = 0; c < CASES + 2; c++) {
            int n = c >= CASES ? 200 : size(rnd);
            int g = c == CASES ? 200 : teams(rnd, n); // 200 candidates in 200 teams once, then a random G
            Inst in = inst(rnd, n, g);
            long seed = rnd.nextLong();
            int[] teamOf = SimulatedAnnealingTeamOptimizer.optimize(in.calc(), n, g, seed);
            assertPlacedOnceWithBalancedSizes(teamOf, n, g);
            assertPlacedOnceWithBalancedSizes(SimulatedAnnealingTeamOptimizer.initialAssignment(n, g, seed), n, g);
        }
        // explicit extremes: one candidate, G = 1, G = N
        for (int n : new int[] {1, 2, 7, 22}) {
            for (int g : new int[] {1, n}) {
                Inst in = inst(rnd, n, g);
                assertPlacedOnceWithBalancedSizes(SimulatedAnnealingTeamOptimizer.optimize(in.calc(), n, g, 42L), n, g);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // 2. determinism (optimizer level)
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void sameInputsAndSeedGiveTheSameAssignmentAndDiagnostics() {
        Random rnd = new Random(SEED + 2);
        for (int c = 0; c < CASES; c++) {
            int n = size(rnd);
            int g = teams(rnd, n);
            Inst in = inst(rnd, n, g);
            long seed = rnd.nextLong();
            int[] first = SimulatedAnnealingTeamOptimizer.optimize(in.calc(), n, g, seed);
            int[] second = SimulatedAnnealingTeamOptimizer.optimize(in.calc(), n, g, seed);
            assertThat(second).isEqualTo(first);
            TeamDiagnostics a = in.calc().diagnostics(first);
            TeamDiagnostics b = in.calc().diagnostics(second);
            assertThat(b.cost()).isEqualTo(a.cost());
            assertThat(b.imbalance()).isEqualTo(a.imbalance());
            assertThat(b.diversity()).isEqualTo(a.diversity());
        }
    }

    @Test
    void differentSeedsGiveDifferentStartingDeals() {
        // The seed must reach the shuffle: over many seeds the deal of 12 candidates cannot be one fixed deal.
        Set<List<Integer>> deals = new HashSet<>();
        for (long seed = 0; seed < 40; seed++) {
            deals.add(Arrays.stream(SimulatedAnnealingTeamOptimizer.initialAssignment(12, 3, seed)).boxed().toList());
        }
        assertThat(deals.size()).isGreaterThan(30);
    }

    // ---------------------------------------------------------------------------------------------------------
    // 3. N/A handling (calculator level)
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void maskedCellsAreNeverReadAndEveryNaMixStaysFinite() {
        Random rnd = new Random(SEED + 3);
        for (int c = 0; c < CASES; c++) {
            int n = size(rnd);
            int g = teams(rnd, n);
            Inst in = inst(rnd, n, g);
            if (c % 25 == 0) { // all N/A
                in = in.with(in.values(), new boolean[n][in.dims()]);
            }
            int[] balanced = SimulatedAnnealingTeamOptimizer.initialAssignment(n, g, rnd.nextLong());
            if (c % 3 == 0 && in.dims() > 0) { // a whole team N/A in one column
                int team = rnd.nextInt(g);
                int d = rnd.nextInt(in.dims());
                boolean[][] has = new boolean[n][];
                for (int i = 0; i < n; i++) {
                    has[i] = in.has()[i].clone();
                    if (balanced[i] == team) {
                        has[i][d] = false;
                    }
                }
                in = in.with(in.values(), has);
            }
            for (int[] teamOf : List.of(balanced, looseAssignment(rnd, n, g))) { // loose may leave teams empty
                TeamDiagnostics diag = in.calc().diagnostics(teamOf);
                assertFinite(diag.imbalance(), diag.diversity(), diag.cost());
                for (double[] row : diag.teamMeans()) {
                    for (double mean : row) {
                        assertThat(Double.isInfinite(mean)).isFalse();
                    }
                }
                checkNaCountsAndMeans(in, teamOf, diag);
                checkAgainstOracle(in, teamOf, diag);
                for (double other : new double[] {0.0, 1.0, 999.0}) { // masked cells must not matter at all
                    assertThat(repoisoned(in, other).calc().cost(teamOf)).isEqualTo(diag.cost());
                }
            }
            long seed = rnd.nextLong();
            assertThat(SimulatedAnnealingTeamOptimizer.optimize(repoisoned(in, 0.0).calc(), n, g, seed))
                    .isEqualTo(SimulatedAnnealingTeamOptimizer.optimize(in.calc(), n, g, seed));
        }
    }

    @Test
    void anAllNaCandidateAddedNeverThrowsAndIsPlacedInTheRange() {
        Random rnd = new Random(SEED + 4);
        for (int c = 0; c < CASES; c++) {
            int n = size(rnd);
            Inst in = withExtraAllNaCandidate(inst(rnd, n, 1), rnd);
            int g = teams(rnd, n + 1);
            in = new Inst(in.n(), g, in.hard(), in.values(), in.has(), in.params());
            int[] teamOf = SimulatedAnnealingTeamOptimizer.optimize(in.calc(), n + 1, g, rnd.nextLong());
            assertPlacedOnceWithBalancedSizes(teamOf, n + 1, g);
            TeamDiagnostics diag = in.calc().diagnostics(teamOf);
            assertFinite(diag.imbalance(), diag.diversity(), diag.cost());
            // the extra candidate is counted as N/A in every column of its own team, never as a score
            for (int d = 0; d < in.dims(); d++) {
                assertThat(diag.naCounts()[teamOf[n]][d]).isGreaterThanOrEqualTo(1);
            }
        }
    }

    @Test
    void naIsNotZeroSoReplacingItByZeroChangesTheResult() {
        // One hard skill, alpha 1 (pure imbalance), four candidates. Candidate 2 has N/A, the others 4.0 (=1.0).
        double[][] na = new double[4][6];
        boolean[][] hasNa = new boolean[4][6];
        for (int i = 0; i < 4; i++) {
            na[i][0] = 1.0;
            hasNa[i][0] = i != 2;
        }
        na[2][0] = Double.NaN;
        double[][] zero = copyValues(na);
        zero[2][0] = 0.0;
        boolean[][] hasZero = new boolean[4][6];
        for (boolean[] row : hasZero) {
            row[0] = true;
        }
        Params params = new Params(GroupingMode.HARD_SKILLS, false, 0.5, 1.0, new double[] {0.2, 0.2, 0.2, 0.2, 0.2});
        for (int[] teamOf : new int[][] {{0, 0, 1, 1}, {0, 1, 0, 1}, {0, 1, 1, 0}}) {
            double withNa = new TeamObjectiveCalculator(na, hasNa, 1, 2, params).cost(teamOf);
            double withZero = new TeamObjectiveCalculator(zero, hasZero, 1, 2, params).cost(teamOf);
            assertThat(withNa).isEqualTo(0.0); // every team mean stays 1.0 when N/A is left out
            assertThat(withZero).isGreaterThan(0.2); // cohort 0.75, team with the zero 0.5 -> deviation 0.25
        }
        int[] best = SimulatedAnnealingTeamOptimizer.optimize(new TeamObjectiveCalculator(zero, hasZero, 1, 2, params),
                4, 2, 1L);
        assertThat(best).hasSize(4);
    }

    // ---------------------------------------------------------------------------------------------------------
    // 4. optimizer cost, relabelling, weight scaling
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void optimizerCostNeverExceedsItsStartingDealAndFindsTheOptimumOnTinyWorlds() {
        Random rnd = new Random(SEED + 5);
        for (int c = 0; c < CASES; c++) {
            int n = size(rnd);
            int g = teams(rnd, n);
            Inst in = inst(rnd, n, g);
            long seed = rnd.nextLong();
            double start = in.calc().cost(SimulatedAnnealingTeamOptimizer.initialAssignment(n, g, seed));
            double end = in.calc().cost(SimulatedAnnealingTeamOptimizer.optimize(in.calc(), n, g, seed));
            assertThat(end).isLessThanOrEqualTo(start);
        }
        int improved = 0;
        int optimal = 0;
        for (int c = 0; c < CASES; c++) {
            int n = 2 + rnd.nextInt(6); // 2..7
            int g = 2 + rnd.nextInt(Math.min(n, 4) - 1); // 2..min(n,4)
            Inst in = inst(rnd, n, g);
            long seed = rnd.nextLong();
            double best = bruteForceBest(in);
            double start = in.calc().cost(SimulatedAnnealingTeamOptimizer.initialAssignment(n, g, seed));
            int[] result = SimulatedAnnealingTeamOptimizer.optimize(in.calc(), n, g, seed);
            double found = in.calc().cost(result);
            assertThat(found).as("never below the true optimum").isGreaterThanOrEqualTo(best - 1e-12);
            optimal += found <= best + 1e-12 ? 1 : 0;
            if (found < start - 1e-12) {
                improved++;
            }
        }
        // Simulated Annealing is a heuristic: it is not guaranteed to reach the optimum (e.g. n=5, g=4 can end in the
        // second-best deal), so this asserts a hit rate, not every case. It still kills "never accept a swap".
        assertThat(optimal).as("optimum hits of %d (improved %d)", CASES, improved).isGreaterThan(CASES * 90 / 100);
        assertThat(improved).as("the annealing must improve some starting deals").isGreaterThan(20);
    }

    private static double bruteForceBest(Inst in) {
        TeamObjectiveCalculator calc = in.calc();
        int n = in.n();
        int g = in.g();
        int floor = n / g;
        int ceil = (n + g - 1) / g;
        int[] teamOf = new int[n];
        double best = Double.POSITIVE_INFINITY;
        long total = 1;
        for (int i = 0; i < n; i++) {
            total *= g;
        }
        for (long code = 0; code < total; code++) {
            long rest = code;
            int[] sizes = new int[g];
            for (int i = 0; i < n; i++) {
                teamOf[i] = (int) (rest % g);
                rest /= g;
                sizes[teamOf[i]]++;
            }
            boolean balanced = true;
            for (int size : sizes) {
                balanced &= size >= floor && size <= ceil;
            }
            if (balanced) {
                best = Math.min(best, calc.cost(teamOf));
            }
        }
        return best;
    }

    /** Strict local minimum: every swap of two members of different teams makes the cost strictly worse. */
    private static boolean isStrictSwapLocalMinimum(TeamObjectiveCalculator calc, int[] teamOf, double cost) {
        for (int i = 0; i < teamOf.length; i++) {
            for (int j = i + 1; j < teamOf.length; j++) {
                if (teamOf[i] == teamOf[j]) {
                    continue;
                }
                int[] swapped = teamOf.clone();
                swapped[i] = teamOf[j];
                swapped[j] = teamOf[i];
                if (calc.cost(swapped) <= cost + 1e-12) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    void annealingEscapesLocalOptimaThatPureDescentCannotLeave() {
        // Starting deals that no single swap can improve, yet the true optimum is better: only an optimizer that
        // sometimes accepts a worse swap (Simulated Annealing, REQ-TB-09) can leave them.
        Random rnd = new Random(SEED + 15);
        int trapped = 0;
        int escaped = 0;
        for (int w = 0; w < 4000 && trapped < 40; w++) {
            int n = 6 + rnd.nextInt(3);
            int g = 2 + rnd.nextInt(2);
            Inst in = inst(rnd, n, g);
            long seed = rnd.nextLong();
            int[] start = SimulatedAnnealingTeamOptimizer.initialAssignment(n, g, seed);
            double startCost = in.calc().cost(start);
            if (!isStrictSwapLocalMinimum(in.calc(), start, startCost)) {
                continue;
            }
            double best = bruteForceBest(in);
            if (startCost <= best + 1e-9) {
                continue;
            }
            trapped++;
            double found = in.calc().cost(SimulatedAnnealingTeamOptimizer.optimize(in.calc(), n, g, seed));
            escaped += found <= best + 1e-12 ? 1 : 0;
        }
        assertThat(trapped).as("trapped starting deals found").isGreaterThanOrEqualTo(10);
        assertThat(escaped).as("escaped of %d trapped deals", trapped).isGreaterThanOrEqualTo(trapped * 8 / 10);
    }

    @Test
    void annealingBeatsTheStartingDealByAClearMarginOnSixtyCandidates() {
        // 60 candidates, 6 teams, 3 hard skills and 5 traits, pure imbalance (alpha 1): the annealing schedule, its
        // cooling and its iteration budget must bring the cost far below a random deal. A random walk, a hot
        // schedule or a tiny iteration budget ends clearly higher (measured: 0.20 of the start vs 0.28 to 0.57).
        double sumStart = 0;
        double sumEnd = 0;
        for (int w = 0; w < 10; w++) {
            Random rnd = new Random(1000 + w);
            double[][] values = new double[60][8];
            boolean[][] has = new boolean[60][8];
            for (int i = 0; i < 60; i++) {
                Arrays.fill(has[i], true);
                for (int d = 0; d < 8; d++) {
                    values[i][d] = rnd.nextInt(17) / 16.0;
                }
            }
            Params params = new Params(GroupingMode.COMBINED, true, 0.6, 1.0, new double[] {0.30, 0.25, 0.20, 0.15, 0.10});
            for (long seed = 0; seed < 3; seed++) {
                TeamObjectiveCalculator calc = new TeamObjectiveCalculator(values, has, 3, 6, params);
                sumStart += calc.cost(SimulatedAnnealingTeamOptimizer.initialAssignment(60, 6, seed));
                sumEnd += calc.cost(SimulatedAnnealingTeamOptimizer.optimize(calc, 60, 6, seed));
            }
        }
        assertThat(sumEnd).isLessThan(0.24 * sumStart);
    }

    @Test
    void customTraitWeightsNeedAPositiveSumAndAreNormalised() {
        assertThatThrownBy(() -> TeamObjectiveCalculator.traitWeights(SoftPreset.CUSTOM,
                Map.of("conscientiousness", 0.0, "agreeableness", 0.0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TeamObjectiveCalculator.traitWeights(SoftPreset.CUSTOM, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        double[] weights = TeamObjectiveCalculator.traitWeights(SoftPreset.CUSTOM, Map.of("openness", 2.0, "extraversion", 6.0));
        assertThat(weights).containsExactly(0.0, 0.0, 0.0, 0.75, 0.25);
    }

    @Test
    void relabellingTheTeamsLeavesTheCostUnchanged() {
        Random rnd = new Random(SEED + 6);
        for (int c = 0; c < CASES; c++) {
            int n = size(rnd);
            int g = teams(rnd, n);
            Inst in = inst(rnd, n, g);
            List<Integer> permutation = new ArrayList<>();
            for (int t = 0; t < g; t++) {
                permutation.add(t);
            }
            Collections.shuffle(permutation, rnd);
            for (int[] teamOf : List.of(SimulatedAnnealingTeamOptimizer.initialAssignment(n, g, rnd.nextLong()),
                    looseAssignment(rnd, n, g))) {
                int[] relabelled = new int[n];
                for (int i = 0; i < n; i++) {
                    relabelled[i] = permutation.get(teamOf[i]);
                }
                assertThat(in.calc().cost(relabelled)).isCloseTo(in.calc().cost(teamOf), within(1e-9));
            }
        }
    }

    @Test
    void scalingTheTraitWeightsByPowersOfTwoLeavesCostAndAssignmentUnchanged() {
        Random rnd = new Random(SEED + 7);
        for (int c = 0; c < CASES; c++) {
            int n = 4 + rnd.nextInt(30);
            int g = 2 + rnd.nextInt(Math.min(n, 8) - 1);
            Inst in = inst(rnd, n, g);
            GroupingMode mode = rnd.nextBoolean() ? GroupingMode.SOFT_SKILLS : GroupingMode.COMBINED;
            in = in.with(new Params(mode, true, in.params().combinedHardWeight(), in.params().alpha(),
                    in.params().traitWeights()));
            double factor = Math.scalb(1.0, rnd.nextInt(7) - 3);
            double[] scaled = in.params().traitWeights().clone();
            for (int t = 0; t < scaled.length; t++) {
                scaled[t] *= factor;
            }
            Inst big = in.with(new Params(mode, true, in.params().combinedHardWeight(), in.params().alpha(), scaled));
            long seed = rnd.nextLong();
            int[] teamOf = SimulatedAnnealingTeamOptimizer.initialAssignment(n, g, seed);
            assertThat(big.calc().cost(teamOf)).isEqualTo(in.calc().cost(teamOf));
            assertThat(SimulatedAnnealingTeamOptimizer.optimize(big.calc(), n, g, seed))
                    .isEqualTo(SimulatedAnnealingTeamOptimizer.optimize(in.calc(), n, g, seed));
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // 5. BFI-10
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void noTraitTermInHardSkillsOrWithBfiOffAndNoInversionOfTheNormalisation() {
        Random rnd = new Random(SEED + 8);
        for (int c = 0; c < CASES; c++) {
            int n = size(rnd);
            int g = teams(rnd, n);
            Inst in = inst(rnd, n, g);
            boolean hardMode = rnd.nextBoolean();
            in = in.with(new Params(hardMode ? GroupingMode.HARD_SKILLS : MODES[1 + rnd.nextInt(2)], hardMode && rnd.nextBoolean(),
                    in.params().combinedHardWeight(), in.params().alpha(), in.params().traitWeights()));
            // HARD_SKILLS with BFI-10 on or off, and SOFT/COMBINED with BFI-10 off: the trait columns must not matter.
            double[][] values = copyValues(in.values());
            boolean[][] has = new boolean[n][];
            for (int i = 0; i < n; i++) {
                has[i] = in.has()[i].clone();
                for (int d = in.hard(); d < in.dims(); d++) {
                    has[i][d] = rnd.nextBoolean();
                    values[i][d] = has[i][d] ? rnd.nextDouble() : poison(rnd);
                }
            }
            Inst shifted = in.with(values, has);
            for (int k = 0; k < 4; k++) {
                int[] teamOf = k % 2 == 0 ? SimulatedAnnealingTeamOptimizer.initialAssignment(n, g, rnd.nextLong())
                        : looseAssignment(rnd, n, g);
                assertThat(shifted.calc().cost(teamOf)).isEqualTo(in.calc().cost(teamOf));
            }
        }
    }

    @Test
    void traitTermIsPresentInSoftAndCombinedWithBfiOn() {
        Random rnd = new Random(SEED + 9);
        for (int c = 0; c < CASES; c++) {
            int k = 2 + 2 * rnd.nextInt(5); // team half size, even
            int n = 2 * k;
            int hard = rnd.nextInt(4);
            int dims = hard + TRAITS.length;
            int trait = rnd.nextInt(TRAITS.length);
            double[] w = weights(rnd);
            while (w[trait] == 0) {
                w = weights(rnd);
            }
            GroupingMode mode = rnd.nextBoolean() ? GroupingMode.SOFT_SKILLS : GroupingMode.COMBINED;
            double hardWeight = Math.min(0.9, rnd.nextDouble());
            double[][] values = new double[n][dims];
            boolean[][] has = new boolean[n][dims];
            for (int i = 0; i < n; i++) {
                Arrays.fill(has[i], true);
                Arrays.fill(values[i], 0.5);
                values[i][hard + trait] = i < k ? 0.0 : 1.0;
            }
            Params params = new Params(mode, true, hardWeight, 1.0, w); // alpha 1: pure imbalance
            TeamObjectiveCalculator calc = new TeamObjectiveCalculator(values, has, hard, 2, params);
            int[] segregated = new int[n];
            int[] mixed = new int[n];
            for (int i = 0; i < n; i++) {
                segregated[i] = i < k ? 0 : 1;
                mixed[i] = i % 2;
            }
            double softShare = mode == GroupingMode.SOFT_SKILLS ? 1.0 : 1.0 - hardWeight;
            assertThat(calc.cost(mixed)).isCloseTo(0.0, within(1e-12));
            assertThat(calc.cost(segregated)).isCloseTo(softShare * w[trait] * 0.5, within(1e-12));
            // with BFI-10 off or HARD_SKILLS the same world has no trait term at all
            assertThat(new TeamObjectiveCalculator(values, has, hard, 2,
                    new Params(mode, false, hardWeight, 1.0, w)).cost(segregated)).isEqualTo(0.0);
            assertThat(new TeamObjectiveCalculator(values, has, hard, 2,
                    new Params(GroupingMode.HARD_SKILLS, true, hardWeight, 1.0, w)).cost(segregated)).isEqualTo(0.0);
            // the optimizer, run on this world, spreads the low and high trait values
            int[] found = SimulatedAnnealingTeamOptimizer.optimize(calc, n, 2, rnd.nextLong());
            assertThat(calc.cost(found)).isCloseTo(0.0, within(1e-12));
        }
    }

    @Test
    void traitNormalisationMapsOneToZeroAndFiveToOneAndSkillZeroToFourToUnitWithoutInversion() {
        assertThat(TeamObjectiveCalculator.normalizeTrait(1.0)).isEqualTo(0.0);
        assertThat(TeamObjectiveCalculator.normalizeTrait(5.0)).isEqualTo(1.0);
        assertThat(TeamObjectiveCalculator.normalizeSkill(0.0)).isEqualTo(0.0);
        assertThat(TeamObjectiveCalculator.normalizeSkill(4.0)).isEqualTo(1.0);
        Random rnd = new Random(SEED + 10);
        for (int c = 0; c < CASES; c++) {
            double a = 1 + 4 * rnd.nextDouble();
            double b = 1 + 4 * rnd.nextDouble();
            assertThat(Double.compare(TeamObjectiveCalculator.normalizeTrait(a), TeamObjectiveCalculator.normalizeTrait(b)))
                    .isEqualTo(Double.compare(a, b));
            assertThat(TeamObjectiveCalculator.normalizeTrait(a)).isCloseTo((a - 1) / 4, within(1e-15)).isBetween(0.0, 1.0);
            assertThat(TeamObjectiveCalculator.normalizeSkill(a - 1)).isCloseTo((a - 1) / 4, within(1e-15)).isBetween(0.0, 1.0);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // 6. bounds (calculator level)
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void teamMeansStayWithinZeroAndOneAndDiversityIsNeverNegative() {
        Random rnd = new Random(SEED + 11);
        for (int c = 0; c < CASES; c++) {
            int n = size(rnd);
            int g = teams(rnd, n);
            Inst in = inst(rnd, n, g);
            for (int[] teamOf : List.of(SimulatedAnnealingTeamOptimizer.optimize(in.calc(), n, g, rnd.nextLong()),
                    looseAssignment(rnd, n, g))) {
                TeamDiagnostics diag = in.calc().diagnostics(teamOf);
                assertThat(diag.imbalance()).isBetween(0.0, 1.0);
                assertThat(diag.diversity()).isBetween(0.0, 1.0);
                assertThat(diag.cost()).isBetween(-1.0, 1.0);
                for (double[] row : diag.teamMeans()) {
                    for (double mean : row) {
                        if (!Double.isNaN(mean)) {
                            assertThat(mean).isBetween(0.0, 1.0);
                        }
                    }
                }
                assertThat(Arrays.stream(diag.teamSizes()).sum()).isEqualTo(n);
                assertThat(diag.teamSizes()).isEqualTo(sizes(teamOf, g));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // service-level worlds (Mockito stubs for the cross-slice contracts and the repositories)
    // ---------------------------------------------------------------------------------------------------------

    private static final class Pop {
        UUID configId;
        TeamView view;
        UUID[] skillIds;
        boolean[] listed;
        Double[][] skillTruth;
        Double[][] traitTruth;
        List<CandidateHardSkillProfile> released = new ArrayList<>();
        List<UUID> pending = new ArrayList<>();
        List<StudentSoftSkillProfile> soft = new ArrayList<>();
        Map<UUID, Integer> index = new HashMap<>();
    }

    private static UUID uuid(Random rnd) {
        return new UUID(rnd.nextLong(), rnd.nextLong());
    }

    private static Pop pop(Random rnd, int n, int skills, boolean bfi, GroupingMode mode) {
        Pop p = new Pop();
        p.configId = uuid(rnd);
        p.skillIds = new UUID[skills];
        for (int k = 0; k < skills; k++) {
            p.skillIds[k] = uuid(rnd);
        }
        p.listed = new boolean[skills];
        p.skillTruth = new Double[n][skills];
        p.traitTruth = new Double[n][TRAITS.length];
        p.view = new TeamView(p.configId, 1 + rnd.nextInt(6), mode, SoftPreset.values()[rnd.nextInt(2)],
                BigDecimal.valueOf(Math.round(unit(rnd) * 10_000) / 10_000.0),
                BigDecimal.valueOf(Math.round(unit(rnd) * 10_000) / 10_000.0), Map.of(), bfi);
        double pNa = NA_RATES[rnd.nextInt(NA_RATES.length)];
        for (int i = 0; i < n; i++) {
            UUID invite = uuid(rnd);
            UUID attempt = rnd.nextInt(20) == 0 ? null : uuid(rnd); // no attempt id: its traits can never match
            p.index.put(invite, i);
            boolean allNa = rnd.nextInt(12) == 0;
            List<SkillScoreView> cells = new ArrayList<>();
            for (int k = 0; k < skills; k++) {
                if (!allNa && rnd.nextDouble() >= pNa) {
                    double score = rnd.nextInt(17) / 4.0;
                    cells.add(new SkillScoreView(p.skillIds[k], "S" + k, ScoreStatus.SCORED, BigDecimal.valueOf(score)));
                    p.skillTruth[i][k] = score;
                    p.listed[k] = true;
                    continue;
                }
                switch (rnd.nextInt(4)) {
                    case 0 -> { // the skill is missing from this candidate's list
                    }
                    case 1 -> {
                        cells.add(new SkillScoreView(p.skillIds[k], "S" + k, ScoreStatus.NOT_ASSESSED, null));
                        p.listed[k] = true;
                    }
                    case 2 -> { // N/A status with a poison score: the status wins
                        cells.add(new SkillScoreView(p.skillIds[k], "S" + k, ScoreStatus.NOT_ASSESSED,
                                BigDecimal.valueOf(999)));
                        p.listed[k] = true;
                    }
                    default -> {
                        cells.add(new SkillScoreView(p.skillIds[k], "S" + k, ScoreStatus.SCORED, null));
                        p.listed[k] = true;
                    }
                }
            }
            Collections.shuffle(cells, rnd);
            p.released.add(new CandidateHardSkillProfile(invite, attempt, ReleaseStatus.APPROVED,
                    new BigDecimal("2.00"), new BigDecimal("1.0000"), cells));
            if (bfi) {
                addSoft(rnd, p, i, invite, attempt);
            }
        }
        for (int j = 0; j < rnd.nextInt(3); j++) { // waiting for review: never placed, never scored
            UUID waiting = uuid(rnd);
            p.pending.add(waiting);
            if (bfi) {
                p.soft.add(new StudentSoftSkillProfile(waiting, uuid(rnd), poisonTraits()));
            }
        }
        return p;
    }

    private static Map<String, Double> poisonTraits() {
        Map<String, Double> poison = new HashMap<>();
        for (String trait : TRAITS) {
            poison.put(trait, 999.0);
        }
        return poison;
    }

    private static void addSoft(Random rnd, Pop p, int i, UUID invite, UUID attempt) {
        int pick = rnd.nextInt(10);
        if (attempt == null) { // released side has no attempt id: any soft profile is another attempt
            p.soft.add(new StudentSoftSkillProfile(invite, uuid(rnd), poisonTraits()));
        } else if (pick < 7) {
            double pNa = NA_RATES[rnd.nextInt(NA_RATES.length)];
            Map<String, Double> traits = new HashMap<>();
            for (int t = 0; t < TRAITS.length; t++) {
                if (rnd.nextDouble() >= pNa) {
                    double mean = 1 + rnd.nextInt(17) / 4.0;
                    traits.put(TRAITS[t], mean);
                    p.traitTruth[i][t] = mean;
                } else if (rnd.nextBoolean()) {
                    traits.put(TRAITS[t], null);
                }
            }
            p.soft.add(new StudentSoftSkillProfile(invite, attempt, traits));
        } else if (pick < 9) { // traits of another attempt count as N/A
            p.soft.add(new StudentSoftSkillProfile(invite, uuid(rnd), poisonTraits()));
        }
    }

    private static GenerateTeamsRequest request(Random rnd, int n) {
        Integer numTeams = rnd.nextInt(3) == 0 ? null : rnd.nextInt(6) == 0 ? n : 1 + rnd.nextInt(n);
        return new GenerateTeamsRequest(numTeams, null, null, null, rnd.nextLong(-GenerateTeamsRequest.MAX_SEED,
                GenerateTeamsRequest.MAX_SEED + 1), null);
    }

    private final StudioQueryService studio = mock(StudioQueryService.class);
    private final EvaluationQueryService evaluation = mock(EvaluationQueryService.class);
    private final AssessmentQueryService assessment = mock(AssessmentQueryService.class);
    private final AuthContextService authContext = mock(AuthContextService.class);
    private final TeamProposalRepository proposals = mock(TeamProposalRepository.class);
    private final TeamBuilderService service = new TeamBuilderService(studio, evaluation, assessment,
            mock(AuditService.class), authContext, mock(InviteService.class), proposals,
            mock(TeamMemberRepository.class));

    /** The service keeps the mocks above for the whole test; only the stubs change per run. */
    private TeamProposalResponse run(Pop pop, List<CandidateHardSkillProfile> released, List<UUID> pending,
            List<StudentSoftSkillProfile> soft, GenerateTeamsRequest request) {
        UUID professor = new UUID(1, 2);
        when(authContext.currentProfessorId()).thenReturn(professor);
        when(studio.ownedBy(pop.configId, professor)).thenReturn(true);
        when(studio.teamView(pop.configId)).thenReturn(pop.view);
        when(evaluation.getCandidateProfilesForConfig(pop.configId)).thenReturn(new CandidateProfilesView(released, pending));
        when(assessment.getSoftSkillProfilesForConfig(pop.configId)).thenReturn(soft);
        when(proposals.save(any(TeamProposalEntity.class))).thenAnswer(call -> call.getArgument(0));
        clearInvocations(assessment);
        TeamProposalResponse response = service.generate(pop.configId, request);
        if (!pop.view.bfi10Enabled()) { // BFI-10 off: the soft profiles are never read
            verify(assessment, never()).getSoftSkillProfilesForConfig(any());
        }
        return response;
    }

    private static Map<UUID, Integer> assignment(TeamProposalResponse response) {
        Map<UUID, Integer> placed = new HashMap<>();
        for (TeamProposalResponse.TeamCard card : response.teams()) {
            for (TeamProposalResponse.TeamMember member : card.members()) {
                assertThat(placed.put(member.inviteId(), card.teamNo())).as("placed twice").isNull();
            }
        }
        return placed;
    }

    private static int expectedTeams(Pop pop, GenerateTeamsRequest request, int n) {
        return request.numTeams() != null ? request.numTeams()
                : Math.max(1, (int) Math.ceil((double) n / pop.view.teamSize()));
    }

    private static void checkPlacement(Pop pop, GenerateTeamsRequest request, TeamProposalResponse response) {
        int n = pop.released.size();
        int g = expectedTeams(pop, request, n);
        assertThat(response.numTeams()).isEqualTo(g);
        assertThat(response.teams()).hasSize(g);
        Map<UUID, Integer> placed = assignment(response);
        assertThat(placed.keySet()).isEqualTo(pop.index.keySet());
        for (UUID waiting : pop.pending) {
            assertThat(placed).doesNotContainKey(waiting);
        }
        for (int t = 0; t < g; t++) {
            TeamProposalResponse.TeamCard card = response.teams().get(t);
            assertThat(card.teamNo()).isEqualTo(t + 1);
            assertThat(card.size()).isEqualTo(card.members().size()).isIn(n / g, (n + g - 1) / g);
            for (int m = 1; m < card.members().size(); m++) { // stable member order, whatever the hash order is
                assertThat(card.members().get(m - 1).inviteId().compareTo(card.members().get(m).inviteId())).isNegative();
            }
        }
    }

    private static List<CandidateHardSkillProfile> shuffled(Random rnd, List<CandidateHardSkillProfile> released) {
        List<CandidateHardSkillProfile> copy = new ArrayList<>();
        for (CandidateHardSkillProfile profile : released) {
            List<SkillScoreView> cells = new ArrayList<>(profile.skillScores());
            Collections.shuffle(cells, rnd);
            copy.add(new CandidateHardSkillProfile(profile.inviteId(), profile.attemptId(), profile.releaseStatus(),
                    profile.fitScore(), profile.coverage(), cells));
        }
        Collections.shuffle(copy, rnd);
        return copy;
    }

    private static int podSize(Random rnd) {
        return 1 + rnd.nextInt(1 + rnd.nextInt(30));
    }

    // ---------------------------------------------------------------------------------------------------------
    // 2 (service level), 1, 3, 6
    // ---------------------------------------------------------------------------------------------------------

    @Test
    void permutingReleasedAndSkillOrderKeepsTheAssignmentAndRerunsAreIdentical() {
        Random rnd = new Random(SEED + 12);
        for (int c = 0; c < CASES; c++) {
            int n = podSize(rnd);
            boolean bfi = rnd.nextBoolean();
            Pop pop = pop(rnd, n, rnd.nextInt(9), bfi, bfi ? MODES[rnd.nextInt(3)] : GroupingMode.HARD_SKILLS);
            GenerateTeamsRequest request = request(rnd, n);
            TeamProposalResponse first = run(pop, pop.released, pop.pending, pop.soft, request);
            List<StudentSoftSkillProfile> soft = new ArrayList<>(pop.soft);
            Collections.shuffle(soft, rnd);
            List<UUID> pending = new ArrayList<>(pop.pending);
            Collections.shuffle(pending, rnd);
            TeamProposalResponse permuted = run(pop, shuffled(rnd, pop.released), pending, soft, request);
            TeamProposalResponse again = run(pop, pop.released, pop.pending, pop.soft, request);

            checkPlacement(pop, request, first);
            assertThat(assignment(permuted)).isEqualTo(assignment(first));
            assertThat(assignment(again)).isEqualTo(assignment(first));
            assertThat(permuted.diagnostics()).isEqualTo(first.diagnostics());
            assertThat(again.diagnostics()).isEqualTo(first.diagnostics());
            for (int t = 0; t < first.teams().size(); t++) {
                assertThat(new ArrayList<>(permuted.teams().get(t).skillAverages().keySet()))
                        .isEqualTo(new ArrayList<>(first.teams().get(t).skillAverages().keySet()));
            }
        }
    }

    @Test
    void serviceDiagnosticsMatchIndependentNaCountsMeansAndBounds() {
        Random rnd = new Random(SEED + 13);
        for (int c = 0; c < CASES; c++) {
            int n = podSize(rnd);
            boolean bfi = rnd.nextInt(3) != 0;
            Pop pop = pop(rnd, n, rnd.nextInt(9), bfi, bfi ? MODES[rnd.nextInt(3)] : GroupingMode.HARD_SKILLS);
            GenerateTeamsRequest request = request(rnd, n);
            TeamProposalResponse response = run(pop, pop.released, pop.pending, pop.soft, request);
            checkPlacement(pop, request, response);
            checkAgainstTruth(pop, response);
        }
    }

    private static void checkAgainstTruth(Pop pop, TeamProposalResponse response) {
        List<Integer> listed = new ArrayList<>();
        for (int k = 0; k < pop.skillIds.length; k++) {
            if (pop.listed[k]) {
                listed.add(k);
            }
        }
        listed.sort((a, b) -> pop.skillIds[a].compareTo(pop.skillIds[b]));
        List<String> expectedNames = new ArrayList<>();
        int expectedNaTotal = 0;
        for (int k : listed) {
            expectedNames.add("S" + k);
            for (Double[] row : pop.skillTruth) {
                expectedNaTotal += row[k] == null ? 1 : 0;
            }
        }
        assertThat(response.diagnostics().naCount()).isEqualTo(expectedNaTotal);
        assertFinite(response.diagnostics().imbalance(), response.diagnostics().diversity(), response.diagnostics().cost());
        assertThat(response.diagnostics().imbalance()).isBetween(0.0, 1.0);
        assertThat(response.diagnostics().diversity()).isBetween(0.0, 1.0);
        for (TeamProposalResponse.TeamCard card : response.teams()) {
            assertThat(new ArrayList<>(card.skillAverages().keySet())).isEqualTo(expectedNames);
            assertThat(new ArrayList<>(card.skillNaCounts().keySet())).isEqualTo(expectedNames);
            for (int k : listed) {
                int na = 0;
                double sum = 0;
                for (TeamProposalResponse.TeamMember member : card.members()) {
                    Double truth = pop.skillTruth[pop.index.get(member.inviteId())][k];
                    assertThat(member.skillScores().get("S" + k)).isEqualTo(truth);
                    if (truth == null) {
                        na++;
                    } else {
                        sum += truth;
                    }
                }
                assertThat(card.skillNaCounts().get("S" + k)).isEqualTo(na);
                Double average = card.skillAverages().get("S" + k);
                if (na == card.size()) {
                    assertThat(average).as("whole team N/A is N/A, not 0").isNull();
                } else {
                    assertThat(average).isCloseTo(sum / (card.size() - na), within(1e-4)).isBetween(0.0, 4.0);
                }
            }
            if (!pop.view.bfi10Enabled()) {
                assertThat(card.traitAverages()).isEmpty();
                continue;
            }
            for (int t = 0; t < TRAITS.length; t++) {
                int na = 0;
                double sum = 0;
                for (TeamProposalResponse.TeamMember member : card.members()) {
                    Double truth = pop.traitTruth[pop.index.get(member.inviteId())][t];
                    assertThat(member.traits().get(TRAITS[t])).isEqualTo(truth); // no second inversion (Decision 4.3)
                    if (truth == null) {
                        na++;
                    } else {
                        sum += truth;
                    }
                }
                Double average = card.traitAverages().get(TRAITS[t]);
                if (na == card.size()) {
                    assertThat(average).isNull();
                } else {
                    assertThat(average).isCloseTo(sum / (card.size() - na), within(1e-4)).isBetween(1.0, 5.0);
                }
            }
        }
    }

    @Test
    void generatedTeamsEqualADirectOptimizerRunWithTheRequestedParameters() {
        // Reference pipeline built in the test from the truth tables: sorted invites, sorted skill columns, /4 and
        // (y-1)/4 scaling, the requested alpha, combinedHardWeight, mode, seed and trait weights.
        Random rnd = new Random(SEED + 16);
        for (int c = 0; c < CASES; c++) {
            int n = podSize(rnd);
            boolean bfi = rnd.nextInt(3) != 0;
            Pop pop = pop(rnd, n, rnd.nextInt(9), bfi, bfi ? MODES[rnd.nextInt(3)] : GroupingMode.HARD_SKILLS);
            GroupingMode mode = bfi && rnd.nextInt(3) == 0 ? MODES[rnd.nextInt(3)] : pop.view.groupingMode();
            Double alpha = rnd.nextInt(3) == 0 ? Double.valueOf(unit(rnd)) : null;
            Double hardWeight = rnd.nextInt(3) == 0 ? Double.valueOf(unit(rnd)) : null;
            Map<String, Double> custom = null;
            double[] weights = pop.view.softPreset() == SoftPreset.ENGINEERING_DEFAULT
                    ? new double[] {0.30, 0.25, 0.20, 0.15, 0.10} : new double[] {0.2, 0.2, 0.2, 0.2, 0.2};
            if (rnd.nextInt(3) == 0) { // custom weights: zero entries, missing entries and unnormalised values
                custom = new HashMap<>();
                double sum = 0;
                for (String trait : TRAITS) {
                    int pick = rnd.nextInt(3);
                    double value = pick == 0 ? 0.0 : 0.5 + 4 * rnd.nextDouble();
                    if (pick != 2) {
                        custom.put(trait, value);
                        sum += value;
                    }
                }
                if (sum == 0) {
                    custom.put(TRAITS[0], 1.0);
                    sum = 1.0;
                }
                for (int t = 0; t < TRAITS.length; t++) {
                    weights[t] = custom.getOrDefault(TRAITS[t], 0.0) / sum;
                }
            }
            GenerateTeamsRequest base = request(rnd, n);
            GenerateTeamsRequest request = new GenerateTeamsRequest(base.numTeams(), mode, alpha, hardWeight,
                    base.seed(), custom);
            TeamProposalResponse response = run(pop, pop.released, pop.pending, pop.soft, request);

            List<UUID> invites = new ArrayList<>(pop.index.keySet());
            Collections.sort(invites);
            Params params = new Params(mode, bfi, hardWeight != null ? hardWeight : pop.view.combinedHardWeight().doubleValue(),
                    alpha != null ? alpha : pop.view.alpha().doubleValue(), weights);
            int[] reference = referenceTeams(pop, invites, params, expectedTeams(pop, request, n), base.seed());
            Map<UUID, Integer> placed = assignment(response);
            for (int i = 0; i < n; i++) {
                assertThat(placed.get(invites.get(i))).as("candidate %d of %d", i, n).isEqualTo(reference[i] + 1);
            }
            assertThat(response.groupingMode()).isEqualTo(mode);
            assertThat(response.seed()).isEqualTo(base.seed());
        }
    }

    private static int[] referenceTeams(Pop pop, List<UUID> invites, Params params, int g, long seed) {
        List<Integer> listed = new ArrayList<>();
        for (int k = 0; k < pop.skillIds.length; k++) {
            if (pop.listed[k]) {
                listed.add(k);
            }
        }
        listed.sort((a, b) -> pop.skillIds[a].compareTo(pop.skillIds[b]));
        int n = invites.size();
        int hard = listed.size();
        double[][] values = new double[n][hard + TRAITS.length];
        boolean[][] has = new boolean[n][hard + TRAITS.length];
        for (int i = 0; i < n; i++) {
            int idx = pop.index.get(invites.get(i));
            for (int d = 0; d < hard; d++) {
                Double score = pop.skillTruth[idx][listed.get(d)];
                has[i][d] = score != null;
                values[i][d] = score != null ? score / 4.0 : 999.0;
            }
            for (int t = 0; t < TRAITS.length; t++) {
                Double mean = pop.traitTruth[idx][t];
                has[i][hard + t] = mean != null;
                values[i][hard + t] = mean != null ? (mean - 1.0) / 4.0 : -50.0;
            }
        }
        TeamObjectiveCalculator calc = new TeamObjectiveCalculator(values, has, hard, g, params);
        return SimulatedAnnealingTeamOptimizer.optimize(calc, n, g, seed);
    }

    @Test
    void serviceTreatsNaAsNotZeroAndPlacesAnAllNaCandidate() {
        UUID skill = new UUID(7, 7);
        UUID config = new UUID(8, 8);
        Pop pop = new Pop();
        pop.configId = config;
        pop.view = new TeamView(config, 2, GroupingMode.HARD_SKILLS, SoftPreset.EQUAL_WEIGHTS, new BigDecimal("0.5"),
                new BigDecimal("1.0"), Map.of(), false); // alpha 1: pure imbalance
        GenerateTeamsRequest request = new GenerateTeamsRequest(2, null, null, null, 5L, null);
        List<CandidateHardSkillProfile> asNa = new ArrayList<>();
        List<CandidateHardSkillProfile> asZero = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            UUID invite = new UUID(100, i);
            SkillScoreView four = new SkillScoreView(skill, "git", ScoreStatus.SCORED, BigDecimal.valueOf(4));
            SkillScoreView na = new SkillScoreView(skill, "git", ScoreStatus.NOT_ASSESSED, null);
            SkillScoreView zero = new SkillScoreView(skill, "git", ScoreStatus.SCORED, BigDecimal.ZERO);
            asNa.add(profile(invite, i == 2 ? na : four));
            asZero.add(profile(invite, i == 2 ? zero : four));
        }
        TeamProposalResponse withNa = run(pop, asNa, List.of(), List.of(), request);
        TeamProposalResponse withZero = run(pop, asZero, List.of(), List.of(), request);
        assertThat(withNa.diagnostics().imbalance()).isEqualTo(0.0);
        assertThat(withNa.diagnostics().naCount()).isEqualTo(1);
        assertThat(withZero.diagnostics().imbalance()).isGreaterThan(0.2);
        assertThat(withZero.diagnostics().naCount()).isZero();
    }

    private static CandidateHardSkillProfile profile(UUID invite, SkillScoreView cell) {
        return new CandidateHardSkillProfile(invite, new UUID(5, 5), ReleaseStatus.APPROVED, new BigDecimal("2.00"),
                new BigDecimal("1.0000"), List.of(cell));
    }

    @Test
    void hardSkillsModeIgnoresTraitsEvenWithBfiOnAndTraitScoresAreNotInverted() {
        Random rnd = new Random(SEED + 14);
        for (int c = 0; c < CASES; c++) {
            int n = podSize(rnd);
            Pop pop = pop(rnd, n, 1 + rnd.nextInt(8), true, GroupingMode.HARD_SKILLS);
            GenerateTeamsRequest request = request(rnd, n);
            TeamProposalResponse first = run(pop, pop.released, pop.pending, pop.soft, request);
            // same hard scores, completely different traits (same invites, same attempts): same teams
            List<StudentSoftSkillProfile> changed = new ArrayList<>();
            for (CandidateHardSkillProfile released : pop.released) {
                Map<String, Double> traits = new HashMap<>();
                for (String trait : TRAITS) {
                    traits.put(trait, 1 + 4 * rnd.nextDouble());
                }
                changed.add(new StudentSoftSkillProfile(released.inviteId(), released.attemptId(), traits));
            }
            TeamProposalResponse second = run(pop, pop.released, pop.pending, changed, request);
            assertThat(assignment(second)).isEqualTo(assignment(first));
            assertThat(second.diagnostics()).isEqualTo(first.diagnostics());
        }
    }

    @Test
    void traitsOneAndFiveSurviveTheRoundTripOnTheProfessorScale() {
        UUID config = new UUID(9, 9);
        Pop pop = new Pop();
        pop.configId = config;
        pop.view = new TeamView(config, 2, GroupingMode.COMBINED, SoftPreset.EQUAL_WEIGHTS, new BigDecimal("0.5"),
                new BigDecimal("0.5"), Map.of(), true);
        UUID low = new UUID(1, 1);
        UUID high = new UUID(1, 2);
        UUID attemptLow = new UUID(2, 1);
        UUID attemptHigh = new UUID(2, 2);
        List<CandidateHardSkillProfile> released = List.of(profile(low, attemptLow), profile(high, attemptHigh));
        Map<String, Double> ones = new HashMap<>();
        Map<String, Double> fives = new HashMap<>();
        for (String trait : TRAITS) {
            ones.put(trait, 1.0);
            fives.put(trait, 5.0);
        }
        List<StudentSoftSkillProfile> soft = List.of(new StudentSoftSkillProfile(low, attemptLow, ones),
                new StudentSoftSkillProfile(high, attemptHigh, fives));
        // one team of both: average 3.0; two teams: 1.0 and 5.0, never inverted (Decision 4.3)
        TeamProposalResponse together = run(pop, released, List.of(), soft, new GenerateTeamsRequest(1, null, null, null, 1L, null));
        assertThat(together.teams().get(0).traitAverages().values()).containsOnly(3.0);
        TeamProposalResponse apart = run(pop, released, List.of(), soft, new GenerateTeamsRequest(2, null, null, null, 1L, null));
        for (TeamProposalResponse.TeamCard card : apart.teams()) {
            double expected = card.members().get(0).inviteId().equals(low) ? 1.0 : 5.0;
            assertThat(card.traitAverages().values()).containsOnly(expected);
            assertThat(card.members().get(0).traits().values()).containsOnly(expected);
        }
    }

    private static CandidateHardSkillProfile profile(UUID invite, UUID attempt) {
        return new CandidateHardSkillProfile(invite, attempt, ReleaseStatus.APPROVED, new BigDecimal("2.00"),
                new BigDecimal("1.0000"), List.of(new SkillScoreView(new UUID(3, 3), "git", ScoreStatus.SCORED,
                BigDecimal.valueOf(2))));
    }
}
