package de.fhdortmund.nifty.teams.service;

import java.util.Random;

/**
 * Seeded Simulated Annealing with 2-opt swaps (Decision 1.20, REQ-TB-09). A swap exchanges the teams of two
 * candidates, so the team sizes stay {@code floor(N/G)} or {@code ceil(N/G)} from the start. Same input and
 * seed give the same teams. Plain loops and primitive arrays only: no Java Streams, no LLM.
 */
public final class SimulatedAnnealingTeamOptimizer {

    private static final int MIN_ITERATIONS = 2_000;
    private static final int MAX_ITERATIONS = 60_000;
    private static final int ITERATIONS_PER_CANDIDATE = 300;
    // Costs lie within about -1..1 and one swap moves them by roughly 0.01..0.1, so 0.05 accepts early uphill swaps.
    private static final double START_TEMPERATURE = 0.05;
    private static final double END_TEMPERATURE = 1e-4;

    private SimulatedAnnealingTeamOptimizer() {
    }

    /** Returns {@code teamOf[i]} in {@code 0..numTeams-1}; the best assignment seen, never worse than the start. */
    public static int[] optimize(TeamObjectiveCalculator calculator, int candidates, int numTeams, long seed) {
        int[] current = initialAssignment(candidates, numTeams, seed);
        if (numTeams == 1 || candidates < 2) {
            return current;
        }
        Random random = new Random(seed ^ 0x9E3779B97F4A7C15L);
        int[] best = current.clone();
        double currentCost = calculator.cost(current);
        double bestCost = currentCost;
        int iterations = Math.max(MIN_ITERATIONS, Math.min(MAX_ITERATIONS, ITERATIONS_PER_CANDIDATE * candidates));
        double cooling = Math.pow(END_TEMPERATURE / START_TEMPERATURE, 1.0 / iterations);
        double temperature = START_TEMPERATURE;
        for (int step = 0; step < iterations; step++, temperature *= cooling) {
            int i = random.nextInt(candidates);
            int j = random.nextInt(candidates);
            if (current[i] == current[j]) {
                continue;
            }
            swap(current, i, j);
            double candidateCost = calculator.cost(current);
            double delta = candidateCost - currentCost;
            if (delta <= 0 || random.nextDouble() < Math.exp(-delta / temperature)) {
                currentCost = candidateCost;
                if (currentCost < bestCost) {
                    bestCost = currentCost;
                    System.arraycopy(current, 0, best, 0, candidates);
                }
            } else {
                swap(current, i, j);
            }
        }
        return best;
    }

    /** Seeded shuffle dealt round-robin, so team sizes differ by at most one. */
    static int[] initialAssignment(int candidates, int numTeams, long seed) {
        if (numTeams < 1 || numTeams > candidates) {
            throw new IllegalArgumentException("numTeams must be within 1..candidateCount (" + candidates + ")");
        }
        int[] shuffled = new int[candidates];
        for (int i = 0; i < candidates; i++) {
            shuffled[i] = i;
        }
        Random random = new Random(seed);
        for (int i = candidates - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swapped = shuffled[i];
            shuffled[i] = shuffled[j];
            shuffled[j] = swapped;
        }
        int[] teamOf = new int[candidates];
        for (int slot = 0; slot < candidates; slot++) {
            teamOf[shuffled[slot]] = slot % numTeams;
        }
        return teamOf;
    }

    private static void swap(int[] teamOf, int i, int j) {
        int team = teamOf[i];
        teamOf[i] = teamOf[j];
        teamOf[j] = team;
    }
}
