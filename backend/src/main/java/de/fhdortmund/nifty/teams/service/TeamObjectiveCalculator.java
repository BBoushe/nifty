package de.fhdortmund.nifty.teams.service;

import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import java.util.Arrays;
import java.util.Map;

/**
 * Pure-Java team objective {@code alpha * inter_team_imbalance - (1 - alpha) * intra_team_diversity}
 * (Decisions 1.20, 2.23, 4.3). Candidates are rows of a {@code double[][]} in {@code 0..1} with a parallel
 * {@code boolean[][]} "has a score" mask: columns {@code [0, hardCount)} are hard skills, the rest are the five
 * BFI-10 traits. A missing score ("N/A") is left out of that column only; it is never read as 0 or 1.
 *
 * <p>ZERO Java Streams and zero allocation in {@link #cost(int[])}: the optimizer calls it in its swap loop
 * (coding-standards rule 3). One instance holds scratch arrays, so it is not thread-safe: build one per run.
 */
public final class TeamObjectiveCalculator {

    /** Order of the trait weights of {@code ENGINEERING_DEFAULT} (matches studio's CUSTOM keys). */
    public static final String[] TRAITS =
            {"conscientiousness", "agreeableness", "neuroticism", "extraversion", "openness"};
    private static final double[] ENGINEERING_DEFAULT = {0.30, 0.25, 0.20, 0.15, 0.10};

    /** Run parameters; {@code traitWeights} must sum to 1 over {@link #TRAITS}. */
    public record Params(GroupingMode mode, boolean bfi10Enabled, double combinedHardWeight, double alpha,
            double[] traitWeights) {
    }

    /** Per-team results; {@code teamMeans[g][d]} is {@code NaN} when team g has no scored member for column d. */
    public record TeamDiagnostics(double imbalance, double diversity, double cost, double[][] teamMeans,
            int[][] naCounts, int[] teamSizes) {
    }

    private final double[][] values;
    private final boolean[][] has;
    private final int candidates;
    private final int dims;
    private final int hardCount;
    private final int teams;
    private final double alpha;
    private final double hardShare;
    private final double softShare;
    private final double[] dimWeight;
    private final double[] cohortMean;
    private final boolean[] active;

    private final int[] start;
    private final int[] order;
    private final int[] fill;
    private final double[] buffer;
    private double imbalance;
    private double diversity;

    public TeamObjectiveCalculator(double[][] values, boolean[][] has, int hardCount, int numTeams, Params params) {
        double hardWeight = params.combinedHardWeight();
        requireUnit(params.alpha(), "alpha");
        requireUnit(hardWeight, "combinedHardWeight");
        if (numTeams < 1) {
            throw new IllegalArgumentException("numTeams must be at least 1");
        }
        this.values = values;
        this.has = has;
        this.candidates = values.length;
        this.hardCount = hardCount;
        this.dims = candidates == 0 ? hardCount : values[0].length;
        this.teams = numTeams;
        this.alpha = params.alpha();
        boolean softOn = params.bfi10Enabled() && params.mode() != GroupingMode.HARD_SKILLS
                && dims - hardCount == params.traitWeights().length;
        // REQ-TB-06: the trait term exists only with BFI-10 on and mode SOFT_SKILLS or COMBINED.
        this.softShare = !softOn ? 0.0 : params.mode() == GroupingMode.SOFT_SKILLS ? 1.0 : 1.0 - hardWeight;
        this.hardShare = !softOn ? 1.0 : params.mode() == GroupingMode.SOFT_SKILLS ? 0.0 : hardWeight;
        this.dimWeight = new double[dims];
        for (int d = 0; d < hardCount; d++) {
            dimWeight[d] = 1.0 / hardCount;
        }
        for (int t = 0; t < params.traitWeights().length; t++) {
            if (params.traitWeights()[t] < 0) {
                throw new IllegalArgumentException("trait weights must not be negative");
            }
            if (hardCount + t < dims) {
                dimWeight[hardCount + t] = params.traitWeights()[t];
            }
        }
        this.cohortMean = new double[dims];
        this.active = new boolean[dims];
        for (int d = 0; d < dims; d++) {
            double sum = 0;
            int count = 0;
            for (int i = 0; i < candidates; i++) {
                if (has[i][d]) {
                    sum += values[i][d];
                    count++;
                }
            }
            cohortMean[d] = count == 0 ? 0 : sum / count;
            double share = d < hardCount ? hardShare : softShare;
            active[d] = count > 0 && dimWeight[d] > 0 && share > 0;
        }
        this.start = new int[teams + 1];
        this.order = new int[candidates];
        this.fill = new int[teams];
        this.buffer = new double[candidates];
    }

    /** Hard skill {@code 0..4} to {@code 0..1}. */
    public static double normalizeSkill(double score) {
        return score / 4.0;
    }

    /** BFI-10 trait mean {@code 1..5} to {@code 0..1}. The mean arrives already reverse-scored (Decision 4.3). */
    public static double normalizeTrait(double mean) {
        return (mean - 1.0) / 4.0;
    }

    /** Five trait weights in {@link #TRAITS} order, summing to 1. {@code custom} is read only for CUSTOM. */
    public static double[] traitWeights(SoftPreset preset, Map<String, ? extends Number> custom) {
        if (preset == SoftPreset.ENGINEERING_DEFAULT) {
            return ENGINEERING_DEFAULT.clone();
        }
        double[] weights = new double[TRAITS.length];
        double sum = 0;
        for (int t = 0; t < TRAITS.length; t++) {
            Number given = preset == SoftPreset.CUSTOM ? custom.get(TRAITS[t]) : Double.valueOf(1.0);
            weights[t] = given == null ? 0.0 : given.doubleValue();
            sum += weights[t];
        }
        if (sum <= 0) {
            throw new IllegalArgumentException("trait weights must have a positive sum");
        }
        for (int t = 0; t < weights.length; t++) {
            weights[t] /= sum;
        }
        return weights;
    }

    /** Objective value of an assignment ({@code teamOf[i]} in {@code 0..teams-1}); lower is better. */
    public double cost(int[] teamOf) {
        evaluate(teamOf);
        return alpha * imbalance - (1.0 - alpha) * diversity;
    }

    public TeamDiagnostics diagnostics(int[] teamOf) {
        double cost = cost(teamOf);
        double[][] means = new double[teams][dims];
        int[][] naCounts = new int[teams][dims];
        int[] sizes = new int[teams];
        for (int g = 0; g < teams; g++) {
            sizes[g] = start[g + 1] - start[g];
            for (int d = 0; d < dims; d++) {
                double sum = 0;
                int scored = 0;
                for (int a = start[g]; a < start[g + 1]; a++) {
                    int i = order[a];
                    if (has[i][d]) {
                        sum += values[i][d];
                        scored++;
                    }
                }
                means[g][d] = scored == 0 ? Double.NaN : sum / scored;
                naCounts[g][d] = sizes[g] - scored;
            }
        }
        return new TeamDiagnostics(imbalance, diversity, cost, means, naCounts, sizes);
    }

    private void evaluate(int[] teamOf) {
        bucket(teamOf);
        double hardImb = 0;
        double hardDiv = 0;
        double hardImbW = 0;
        double hardDivW = 0;
        double softImb = 0;
        double softDiv = 0;
        double softImbW = 0;
        double softDivW = 0;
        for (int d = 0; d < dims; d++) {
            if (!active[d]) {
                continue;
            }
            int scoredTeams = 0;
            double deviation = 0;
            int pairTeams = 0;
            double spread = 0;
            for (int g = 0; g < teams; g++) {
                int scored = 0;
                double sum = 0;
                for (int a = start[g]; a < start[g + 1]; a++) {
                    int i = order[a];
                    if (has[i][d]) {
                        buffer[scored++] = values[i][d];
                        sum += values[i][d];
                    }
                }
                if (scored == 0) {
                    continue; // REQ-TB-11: no scored member, so this team skips the column.
                }
                scoredTeams++;
                deviation += Math.abs(sum / scored - cohortMean[d]);
                if (scored >= 2) {
                    pairTeams++;
                    spread += meanPairGap(scored);
                }
            }
            double w = dimWeight[d];
            if (d < hardCount) {
                hardImb += w * deviation / scoredTeams;
                hardImbW += w;
                if (pairTeams > 0) {
                    hardDiv += w * spread / pairTeams;
                    hardDivW += w;
                }
            } else {
                softImb += w * deviation / scoredTeams;
                softImbW += w;
                if (pairTeams > 0) {
                    softDiv += w * spread / pairTeams;
                    softDivW += w;
                }
            }
        }
        imbalance = hardShare * ratio(hardImb, hardImbW) + softShare * ratio(softImb, softImbW);
        diversity = hardShare * ratio(hardDiv, hardDivW) + softShare * ratio(softDiv, softDivW);
    }

    /** Mean absolute difference over all pairs of the first {@code count} entries of {@link #buffer}. */
    private double meanPairGap(int count) {
        double sum = 0;
        for (int a = 0; a < count; a++) {
            for (int b = a + 1; b < count; b++) {
                sum += Math.abs(buffer[a] - buffer[b]);
            }
        }
        return sum / (count * (count - 1) / 2);
    }

    /** Counting sort of the candidates by team into {@link #order}; {@code start[g]..start[g+1]} is team g. */
    private void bucket(int[] teamOf) {
        Arrays.fill(start, 0);
        for (int i = 0; i < candidates; i++) {
            start[teamOf[i] + 1]++;
        }
        for (int g = 0; g < teams; g++) {
            start[g + 1] += start[g];
            fill[g] = start[g];
        }
        for (int i = 0; i < candidates; i++) {
            order[fill[teamOf[i]]++] = i;
        }
    }

    private static double ratio(double sum, double weight) {
        return weight == 0 ? 0 : sum / weight;
    }

    private static void requireUnit(double value, String name) {
        if (!(value >= 0.0 && value <= 1.0)) {
            throw new IllegalArgumentException(name + " must be within 0..1");
        }
    }
}
