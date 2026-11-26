package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * REQ-EP-02, REQ-FE-01, REQ-FE-03, REQ-FE-06 / Decisions 2.2, 2.4, 2.12, 2.20, 2.22:
 * Question-weighted Formula B calculator with 1.0 follow-up inheritance, 0.5 invented multiplier,
 * iterative D2 0.50 cap, explicit N/A (never 0), unweighted fit, and question-weighted coverage.
 * Zero Java Streams (Coding Standard #3).
 */
@Component
public class SkillFormulaCalculator {

    static final BigDecimal DEFAULT_MAX_ENTRY_SHARE = new BigDecimal("0.5000");
    private static final double EPSILON = 1e-12;

    public record QuestionWeight(UUID coreQuestionId, BigDecimal weight) {
    }

    public record ScoredEntryInput(
            UUID entryId,
            UUID coreQuestionId,
            UUID skillId,
            EntryOrigin origin,
            BigDecimal coreQuestionWeight,
            Integer effectiveLevel,
            BigDecimal confidence,
            boolean quoteVerified,
            boolean overridden,
            String reviewStatus
    ) {
        boolean isEligibleForScoring() {
            return effectiveLevel != null && (quoteVerified || overridden);
        }
    }

    public record CalculatedSkillScore(
            UUID skillId,
            ScoreStatus status,
            BigDecimal score,
            BigDecimal avgConfidence,
            int verifiedEvidenceCount,
            String reviewStatus
    ) {
    }

    public record FormulaResult(
            List<CalculatedSkillScore> skillScores,
            BigDecimal fitScore,
            BigDecimal coverage
    ) {
    }

    public FormulaResult calculate(
            List<UUID> configuredSkillIds,
            List<QuestionWeight> coreQuestions,
            List<ScoredEntryInput> evaluations,
            Set<UUID> answeredCoreQuestionIds,
            BigDecimal maxEntryShare) {

        double cap = maxEntryShare == null
                ? DEFAULT_MAX_ENTRY_SHARE.doubleValue()
                : maxEntryShare.doubleValue();

        Map<UUID, List<ScoredEntryInput>> eligibleBySkill = new HashMap<>();
        Map<UUID, List<ScoredEntryInput>> allBySkill = new HashMap<>();
        Set<UUID> scoredCoreQuestions = new HashSet<>();

        for (int i = 0; i < evaluations.size(); i++) {
            ScoredEntryInput item = evaluations.get(i);
            allBySkill.computeIfAbsent(item.skillId(), ignored -> new ArrayList<>()).add(item);
            if (item.isEligibleForScoring()) {
                eligibleBySkill.computeIfAbsent(item.skillId(), ignored -> new ArrayList<>()).add(item);
                // Decision 2.22: an invented-question entry never counts toward coverage.
                if (item.coreQuestionId() != null && item.origin() != EntryOrigin.INVENTED) {
                    scoredCoreQuestions.add(item.coreQuestionId());
                }
            }
        }

        List<CalculatedSkillScore> skillScores = new ArrayList<>(configuredSkillIds.size());
        double fitSum = 0.0;
        int scoredSkillCount = 0;

        for (int i = 0; i < configuredSkillIds.size(); i++) {
            UUID skillId = configuredSkillIds.get(i);
            List<ScoredEntryInput> eligible = eligibleBySkill.get(skillId);
            List<ScoredEntryInput> allForSkill = allBySkill.get(skillId);
            String skillReviewStatus = resolveSkillReviewStatus(allForSkill);

            if (eligible == null || eligible.isEmpty()) {
                // REQ-EP-02: missing verified evidence is explicitly N/A (score = null), never 0.
                skillScores.add(new CalculatedSkillScore(
                        skillId, ScoreStatus.NOT_ASSESSED, null, null, 0, skillReviewStatus));
                continue;
            }

            double weightedScore = computeCappedWeightedMean(eligible, cap);
            BigDecimal roundedScore = BigDecimal.valueOf(weightedScore).setScale(2, RoundingMode.HALF_UP);
            BigDecimal avgConfidence = computeAverageConfidence(eligible);
            int verifiedCount = countVerifiedEvidence(eligible);

            skillScores.add(new CalculatedSkillScore(
                    skillId, ScoreStatus.SCORED, roundedScore, avgConfidence, verifiedCount, skillReviewStatus));
            fitSum += roundedScore.doubleValue();
            scoredSkillCount++;
        }

        // Decision 2.22: fit is the unweighted mean across SCORED skills only; null when none are scored.
        BigDecimal fitScore = scoredSkillCount == 0
                ? null
                : BigDecimal.valueOf(fitSum / scoredSkillCount).setScale(2, RoundingMode.HALF_UP);

        // Decision 2.12 / REQ-FE-03: weight of answered, scored core questions / weight of all core questions.
        BigDecimal coverage = computeCoverage(coreQuestions, answeredCoreQuestionIds, scoredCoreQuestions);

        return new FormulaResult(List.copyOf(skillScores), fitScore, coverage);
    }

    /**
     * Decision 2.22: studio enforces weights &gt; 0, but the calculator never trusts that. A zero-weight entry
     * carries no share. One positive entry is the whole score. When no weight is positive, the plain mean of the
     * eligible levels applies. This keeps every SCORED value inside the eligible min..max level range.
     */
    private static double computeCappedWeightedMean(List<ScoredEntryInput> entries, double maxShare) {
        int n = entries.size();
        double[] positiveWeights = new double[n];
        int[] positiveLevels = new int[n];
        int positive = 0;
        double levelSum = 0.0;
        for (int i = 0; i < n; i++) {
            ScoredEntryInput entry = entries.get(i);
            levelSum += entry.effectiveLevel();
            double weight = effectiveWeight(entry);
            if (weight > EPSILON) {
                positiveWeights[positive] = weight;
                positiveLevels[positive] = entry.effectiveLevel();
                positive++;
            }
        }
        if (positive == 0) {
            return levelSum / n;
        }
        if (positive == 1) {
            return positiveLevels[0];
        }

        double[] shares = computeCappedShares(Arrays.copyOf(positiveWeights, positive), maxShare);
        double weightedSum = 0.0;
        for (int i = 0; i < positive; i++) {
            weightedSum += shares[i] * positiveLevels[i];
        }
        return weightedSum;
    }

    /**
     * Decision 2.22 / REQ-FE-06: follow-ups inherit 1.0 * coreQuestion.weight; invented uses 0.5x.
     */
    private static double effectiveWeight(ScoredEntryInput entry) {
        double base = entry.coreQuestionWeight().doubleValue();
        if (entry.origin() == EntryOrigin.INVENTED) {
            return base * 0.5;
        }
        return base;
    }

    /**
     * D2 iterative water-filling cap: when n >= 2, cap any share exceeding maxShare and redistribute
     * excess proportionally across uncapped entries until no share exceeds maxShare.
     */
    static double[] computeCappedShares(double[] rawWeights, double maxShare) {
        int n = rawWeights.length;
        double[] shares = new double[n];
        boolean[] capped = new boolean[n];
        double remainingShare = 1.0;

        // Guard: if n * maxShare < 1.0, uniform shares are the minimum possible maximum share.
        if (n * maxShare < 1.0 - EPSILON) {
            double uniform = 1.0 / n;
            for (int i = 0; i < n; i++) {
                shares[i] = uniform;
            }
            return shares;
        }

        while (true) {
            double uncappedWeightSum = 0.0;
            for (int i = 0; i < n; i++) {
                if (!capped[i]) {
                    uncappedWeightSum += rawWeights[i];
                }
            }
            if (uncappedWeightSum <= EPSILON) {
                break;
            }

            boolean newlyCapped = false;
            for (int i = 0; i < n; i++) {
                if (!capped[i]) {
                    double candidateShare = remainingShare * (rawWeights[i] / uncappedWeightSum);
                    if (candidateShare > maxShare + EPSILON) {
                        capped[i] = true;
                        shares[i] = maxShare;
                        remainingShare -= maxShare;
                        newlyCapped = true;
                    }
                }
            }

            if (!newlyCapped) {
                for (int i = 0; i < n; i++) {
                    if (!capped[i]) {
                        shares[i] = remainingShare * (rawWeights[i] / uncappedWeightSum);
                    }
                }
                break;
            }
        }
        return shares;
    }

    private static BigDecimal computeAverageConfidence(List<ScoredEntryInput> entries) {
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < entries.size(); i++) {
            BigDecimal conf = entries.get(i).confidence();
            if (conf != null) {
                sum += conf.doubleValue();
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum / count).setScale(4, RoundingMode.HALF_UP);
    }

    private static int countVerifiedEvidence(List<ScoredEntryInput> entries) {
        int count = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).quoteVerified()) {
                count++;
            }
        }
        return count;
    }

    private static BigDecimal computeCoverage(
            List<QuestionWeight> coreQuestions,
            Set<UUID> answeredCoreQuestionIds,
            Set<UUID> scoredCoreQuestions) {
        if (coreQuestions == null || coreQuestions.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        double totalWeight = 0.0;
        double coveredWeight = 0.0;
        for (int i = 0; i < coreQuestions.size(); i++) {
            QuestionWeight q = coreQuestions.get(i);
            double w = q.weight().doubleValue();
            totalWeight += w;
            boolean answered = answeredCoreQuestionIds != null && answeredCoreQuestionIds.contains(q.coreQuestionId());
            if (answered && scoredCoreQuestions.contains(q.coreQuestionId())) {
                coveredWeight += w;
            }
        }
        if (totalWeight <= EPSILON) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        double ratio = Math.min(1.0, Math.max(0.0, coveredWeight / totalWeight));
        return BigDecimal.valueOf(ratio).setScale(4, RoundingMode.HALF_UP);
    }

    private static String resolveSkillReviewStatus(List<ScoredEntryInput> entries) {
        if (entries == null || entries.isEmpty()) {
            return ReleaseStatus.PENDING.literal();
        }
        boolean anyFlagged = false;
        boolean anyPending = false;
        boolean anyOverridden = false;
        boolean anyApproved = false;
        boolean anyAutoReleased = false;
        for (int i = 0; i < entries.size(); i++) {
            String status = entries.get(i).reviewStatus();
            if (ReleaseStatus.FLAGGED.literal().equals(status)) {
                anyFlagged = true;
            } else if (ReleaseStatus.PENDING.literal().equals(status)) {
                anyPending = true;
            } else if (ReleaseStatus.OVERRIDDEN.literal().equals(status)) {
                anyOverridden = true;
            } else if (ReleaseStatus.APPROVED.literal().equals(status)) {
                anyApproved = true;
            } else if (ReleaseStatus.AUTO_RELEASED.literal().equals(status)) {
                anyAutoReleased = true;
            }
        }
        if (anyFlagged) {
            return ReleaseStatus.FLAGGED.literal();
        }
        if (anyPending) {
            return ReleaseStatus.PENDING.literal();
        }
        if (anyOverridden) {
            return ReleaseStatus.OVERRIDDEN.literal();
        }
        if (anyApproved) {
            return ReleaseStatus.APPROVED.literal();
        }
        if (anyAutoReleased) {
            return ReleaseStatus.AUTO_RELEASED.literal();
        }
        // Fail closed on unknown literals rather than promoting to auto_released (Decision 2.10).
        return ReleaseStatus.PENDING.literal();
    }
}
