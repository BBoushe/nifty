package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import de.fhdortmund.nifty.context.service.ContextCreationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import tools.jackson.databind.json.JsonMapper;

/**
 * Property-style invariance tests (plain loops, fixed seed, zero extra dependencies) for all
 * deterministic evaluation invariants: REQ-EP-01..04, REQ-EP-07, REQ-EP-08, REQ-FE-01, REQ-FE-03,
 * REQ-FE-06, REQ-SM-01, REQ-SM-05, REQ-CR-05 (Decisions 2.10, 2.11, 2.12, 2.16, 2.22, 2.24, 2.25).
 * Every oracle is written independently from the specs rather than copied from production code.
 */
class EvaluationInvariantPropertyTest {

    private static final long SEED = 20261126L;
    private static final int ROUNDS = 1200;

    private final VerbatimQuoteVerifier quoteVerifier = new VerbatimQuoteVerifier();
    private final ConfidenceCalculator confidenceCalculator = new ConfidenceCalculator();
    private final SkillFormulaCalculator formulaCalculator = new SkillFormulaCalculator();
    private final ReleaseGateEvaluator releaseGateEvaluator = new ReleaseGateEvaluator();
    private final DeterministicAdviceBuilder adviceBuilder = new DeterministicAdviceBuilder();

    @Test
    void verbatimQuoteVerifierInvariantsHoldUnderRandomWhitespaceAndMutations() {
        Random rnd = new Random(SEED);
        String[] tokens = {"@Transactional", "index", "PostgreSQL", "rollback", "isolation", "deadlock",
                "JSONB", "UUID", "Flyway", "checksum", "cache", "retry", "circuit", "breaker", "Spring"};
        String[] ws = {" ", "  ", "\n", "\t", "\r\n", " \n\t "};

        for (int r = 0; r < ROUNDS; r++) {
            int count = 3 + rnd.nextInt(12);
            List<String> words = new ArrayList<>(count);
            StringBuilder answer = new StringBuilder();
            for (int i = 0; i < count; i++) {
                String w = tokens[rnd.nextInt(tokens.length)];
                words.add(w);
                if (i > 0) {
                    answer.append(ws[rnd.nextInt(ws.length)]);
                }
                answer.append(w);
            }
            int start = rnd.nextInt(count);
            int end = start + 1 + rnd.nextInt(count - start);
            StringBuilder quote = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) {
                    quote.append(ws[rnd.nextInt(ws.length)]);
                }
                quote.append(words.get(i));
            }
            String answerStr = answer.toString();
            String quoteStr = quote.toString();
            String collapsed = quoteStr.trim().replaceAll("\\s+", " ");

            boolean verified = quoteVerifier.isVerified(answerStr, quoteStr);
            if (collapsed.length() >= 8) {
                assertThat(verified)
                        .as("round %d: verbatim substring '%s' must verify", r, collapsed)
                        .isTrue();
            } else {
                assertThat(verified)
                        .as("round %d: short quote '%s' (<8 chars) must be rejected", r, collapsed)
                        .isFalse();
            }

            // Hallucinated token never present in answer MUST be rejected
            assertThat(quoteVerifier.isVerified(answerStr, collapsed + " ZZZ_HALLUCINATED_999"))
                    .as("round %d: hallucinated quote must fail", r)
                    .isFalse();
        }
    }

    @Test
    void verbatimQuoteVerifierFinishesQuicklyOnAdversarialLargeInputs() {
        String large = ("a \t\n b ".repeat(15_000));
        long startNs = System.nanoTime();
        boolean verified = quoteVerifier.isVerified(large, "a b a b a b a b");
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        assertThat(verified).isTrue();
        assertThat(elapsedMs).isLessThan(200L);
    }

    @Test
    void confidenceCalculatorBoundsDeterminismAndMonotonicity() {
        Random rnd = new Random(SEED + 1);
        EntryOrigin[] origins = EntryOrigin.values();

        for (int r = 0; r < ROUNDS; r++) {
            int totalAttempts = rnd.nextInt(5);
            int verifiedCount = totalAttempts == 0 ? 0 : rnd.nextInt(totalAttempts + 1);
            Integer run1 = rnd.nextInt(7) == 0 ? null : rnd.nextInt(5);
            Integer run2 = rnd.nextInt(7) == 0 ? null : rnd.nextInt(5);
            EntryOrigin origin = rnd.nextInt(8) == 0 ? null : origins[rnd.nextInt(origins.length)];
            int answerLen = rnd.nextInt(250);
            int prompted = rnd.nextInt(5);
            int unprompted = rnd.nextInt(5);

            ConfidenceCalculator.ConfidenceSignals signals = new ConfidenceCalculator.ConfidenceSignals(
                    verifiedCount, totalAttempts, run1, run2, origin, answerLen, prompted, unprompted);
            BigDecimal c1 = confidenceCalculator.calculate(signals);
            BigDecimal c2 = confidenceCalculator.calculate(signals);

            assertThat(c1).isEqualByComparingTo(c2);
            assertThat(c1.scale()).isEqualTo(4);
            assertThat(c1).isBetween(BigDecimal.ZERO, BigDecimal.ONE);

            // Monotonicity: full agreement >= disagreement (all else equal)
            if (run1 != null && run2 != null && !run1.equals(run2)) {
                BigDecimal agreed = confidenceCalculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                        verifiedCount, totalAttempts, run1, run1, origin, answerLen, prompted, unprompted));
                assertThat(agreed).isGreaterThanOrEqualTo(c1);
            }

            // Monotonicity: CORE >= FOLLOW_UP >= INVENTED
            BigDecimal core = confidenceCalculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                    verifiedCount, totalAttempts, run1, run2, EntryOrigin.CORE, answerLen, prompted, unprompted));
            BigDecimal followUp = confidenceCalculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                    verifiedCount, totalAttempts, run1, run2, EntryOrigin.FOLLOW_UP, answerLen, prompted, unprompted));
            BigDecimal invented = confidenceCalculator.calculate(new ConfidenceCalculator.ConfidenceSignals(
                    verifiedCount, totalAttempts, run1, run2, EntryOrigin.INVENTED, answerLen, prompted, unprompted));
            assertThat(core).isGreaterThanOrEqualTo(followUp);
            assertThat(followUp).isGreaterThanOrEqualTo(invented);
        }
    }

    @Test
    void formulaBInvariantsCapSharesNaExclusionFitCoverageAndPermutationInvariance() {
        Random rnd = new Random(SEED + 2);
        EntryOrigin[] origins = EntryOrigin.values();
        BigDecimal cap = new BigDecimal("0.5000");

        for (int r = 0; r < ROUNDS; r++) {
            int skillCount = 1 + rnd.nextInt(6);
            int qCount = 1 + rnd.nextInt(6);

            List<UUID> skills = new ArrayList<>(skillCount);
            for (int i = 0; i < skillCount; i++) {
                skills.add(UUID.randomUUID());
            }

            List<SkillFormulaCalculator.QuestionWeight> questions = new ArrayList<>(qCount);
            Set<UUID> answeredQs = new HashSet<>();
            double totalQWeight = 0.0;
            for (int i = 0; i < qCount; i++) {
                UUID qid = UUID.randomUUID();
                BigDecimal w = BigDecimal.valueOf(25 + rnd.nextInt(475), 2); // 0.25..5.00
                questions.add(new SkillFormulaCalculator.QuestionWeight(qid, w));
                totalQWeight += w.doubleValue();
                if (rnd.nextBoolean()) {
                    answeredQs.add(qid);
                }
            }

            int evalCount = rnd.nextInt(15);
            List<SkillFormulaCalculator.ScoredEntryInput> evals = new ArrayList<>(evalCount);
            for (int i = 0; i < evalCount; i++) {
                SkillFormulaCalculator.QuestionWeight q = questions.get(rnd.nextInt(qCount));
                UUID s = skills.get(rnd.nextInt(skillCount));
                EntryOrigin origin = origins[rnd.nextInt(origins.length)];
                Integer level = rnd.nextInt(6) == 0 ? null : rnd.nextInt(5);
                boolean verified = rnd.nextBoolean();
                boolean overridden = rnd.nextInt(5) == 0;
                evals.add(new SkillFormulaCalculator.ScoredEntryInput(
                        UUID.randomUUID(), q.coreQuestionId(), s, origin, q.weight(),
                        level, BigDecimal.valueOf(rnd.nextInt(10001), 4),
                        verified, overridden, overridden ? "overridden" : "pending"));
            }

            SkillFormulaCalculator.FormulaResult res = formulaCalculator.calculate(
                    skills, questions, evals, answeredQs, cap);

            // Permutation invariance: shuffling evaluations must produce identical results
            List<SkillFormulaCalculator.ScoredEntryInput> shuffled = new ArrayList<>(evals);
            Collections.shuffle(shuffled, rnd);
            SkillFormulaCalculator.FormulaResult shuffledRes = formulaCalculator.calculate(
                    skills, questions, shuffled, answeredQs, cap);
            assertThat(shuffledRes.fitScore()).isEqualTo(res.fitScore());
            assertThat(shuffledRes.coverage()).isEqualTo(res.coverage());
            for (int i = 0; i < skills.size(); i++) {
                assertThat(shuffledRes.skillScores().get(i).status()).isEqualTo(res.skillScores().get(i).status());
                assertThat(shuffledRes.skillScores().get(i).score()).isEqualTo(res.skillScores().get(i).score());
            }

            // Independent spec oracles for N/A, score bounds, fit, and coverage
            double scoredSum = 0.0;
            int scoredCount = 0;
            Set<UUID> scoredQs = new HashSet<>();
            for (SkillFormulaCalculator.ScoredEntryInput e : evals) {
                // Decision 2.22: an invented-question entry never counts toward coverage.
                if (e.effectiveLevel() != null && (e.quoteVerified() || e.overridden())
                        && e.origin() != EntryOrigin.INVENTED) {
                    scoredQs.add(e.coreQuestionId());
                }
            }

            for (SkillFormulaCalculator.CalculatedSkillScore sc : res.skillScores()) {
                int minLvl = 5;
                int maxLvl = -1;
                int eligibleCount = 0;
                for (SkillFormulaCalculator.ScoredEntryInput e : evals) {
                    if (e.skillId().equals(sc.skillId())
                            && e.effectiveLevel() != null
                            && (e.quoteVerified() || e.overridden())) {
                        eligibleCount++;
                        minLvl = Math.min(minLvl, e.effectiveLevel());
                        maxLvl = Math.max(maxLvl, e.effectiveLevel());
                    }
                }
                if (eligibleCount == 0) {
                    assertThat(sc.status()).isEqualTo(ScoreStatus.NOT_ASSESSED);
                    assertThat(sc.score()).isNull();
                } else {
                    assertThat(sc.status()).isEqualTo(ScoreStatus.SCORED);
                    assertThat(sc.score()).isBetween(BigDecimal.valueOf(minLvl), BigDecimal.valueOf(maxLvl));
                    assertThat(sc.score()).isBetween(BigDecimal.ZERO, new BigDecimal("4.00"));
                    scoredSum += sc.score().doubleValue();
                    scoredCount++;
                }
            }

            if (scoredCount == 0) {
                assertThat(res.fitScore()).isNull();
            } else {
                BigDecimal expectedFit = BigDecimal.valueOf(scoredSum / scoredCount).setScale(2, RoundingMode.HALF_UP);
                assertThat(res.fitScore()).isEqualByComparingTo(expectedFit);
            }

            // Adding an extra untested (N/A) skill MUST NOT change fitScore (REQ-EP-02)
            List<UUID> skillsWithExtraNa = new ArrayList<>(skills);
            skillsWithExtraNa.add(UUID.randomUUID());
            SkillFormulaCalculator.FormulaResult withExtraNa = formulaCalculator.calculate(
                    skillsWithExtraNa, questions, evals, answeredQs, cap);
            assertThat(withExtraNa.fitScore()).isEqualTo(res.fitScore());

            // Coverage oracle (Decision 2.12, REQ-FE-03)
            double coveredWeight = 0.0;
            for (SkillFormulaCalculator.QuestionWeight q : questions) {
                if (answeredQs.contains(q.coreQuestionId()) && scoredQs.contains(q.coreQuestionId())) {
                    coveredWeight += q.weight().doubleValue();
                }
            }
            BigDecimal expectedCoverage = BigDecimal.valueOf(coveredWeight / totalQWeight)
                    .setScale(4, RoundingMode.HALF_UP);
            assertThat(res.coverage()).isEqualByComparingTo(expectedCoverage);
            assertThat(res.coverage()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        }
    }

    @Test
    void d2CappedSharesAlwaysSumToOneAndRespectCapForTwoOrMoreEntries() {
        Random rnd = new Random(SEED + 3);
        double maxShare = 0.50;
        for (int r = 0; r < ROUNDS; r++) {
            int n = 2 + rnd.nextInt(10);
            double[] weights = new double[n];
            for (int i = 0; i < n; i++) {
                weights[i] = 0.05 + rnd.nextDouble() * 20.0;
            }
            double[] shares = SkillFormulaCalculator.computeCappedShares(weights, maxShare);
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                assertThat(shares[i]).isGreaterThanOrEqualTo(0.0);
                assertThat(shares[i]).isLessThanOrEqualTo(maxShare + 1e-9);
                sum += shares[i];
            }
            assertThat(Math.abs(sum - 1.0)).isLessThan(1e-9);
        }
    }

    @Test
    void releaseGateInvariantsEnforceFlagsAndThresholds() {
        Random rnd = new Random(SEED + 4);
        for (int r = 0; r < ROUNDS; r++) {
            boolean substantive = rnd.nextBoolean();
            boolean verified = rnd.nextBoolean();
            Integer r1 = rnd.nextInt(6) == 0 ? null : rnd.nextInt(5);
            Integer r2 = rnd.nextInt(6) == 0 ? null : rnd.nextInt(5);
            BigDecimal conf = BigDecimal.valueOf(rnd.nextInt(10001), 4);
            BigDecimal threshold = BigDecimal.valueOf(rnd.nextInt(10001), 4);
            boolean paste = rnd.nextBoolean();
            boolean injection = rnd.nextBoolean();
            boolean parseFailed = rnd.nextBoolean();

            ReleaseGateEvaluator.FlagEvaluation eval = releaseGateEvaluator.evaluateResponseFlags(
                    substantive, verified, verified, r1, r2, conf, threshold, paste, injection, parseFailed);

            boolean mustFlag = paste
                    || injection
                    || parseFailed
                    || (substantive && !verified)
                    || (r1 != null && r2 != null && Math.abs(r1 - r2) >= 1)
                    || (verified && (r1 != null || r2 != null) && conf.compareTo(threshold) < 0);

            if (mustFlag) {
                assertThat(eval.reviewStatus()).isEqualTo(ReleaseStatus.FLAGGED);
                assertThat(eval.flagReasons()).isNotBlank();
            } else {
                assertThat(eval.reviewStatus()).isEqualTo(ReleaseStatus.PENDING);
                assertThat(eval.flagReasons()).isNull();
            }

            // Attempt release gate invariant
            BigDecimal coverage = BigDecimal.valueOf(rnd.nextInt(10001), 4);
            BigDecimal minCoverage = BigDecimal.valueOf(rnd.nextInt(10001), 4);
            int nResp = 1 + rnd.nextInt(6);
            List<ReleaseStatus> statuses = new ArrayList<>(nResp);
            int expectedFlagged = 0;
            for (int i = 0; i < nResp; i++) {
                ReleaseStatus st = rnd.nextBoolean() ? ReleaseStatus.PENDING : ReleaseStatus.FLAGGED;
                if (st == ReleaseStatus.FLAGGED) {
                    expectedFlagged++;
                }
                statuses.add(st);
            }
            ReleaseGateEvaluator.AttemptReleaseDecision decision = releaseGateEvaluator
                    .evaluateAttemptRelease(statuses, coverage, minCoverage, 2, false);

            boolean expectedAuto = expectedFlagged == 0 && coverage.compareTo(minCoverage) >= 0;
            assertThat(decision.flaggedCount()).isEqualTo(expectedFlagged);
            assertThat(decision.autoReleased()).isEqualTo(expectedAuto);
            assertThat(decision.releaseStatus())
                    .isEqualTo(expectedAuto ? ReleaseStatus.AUTO_RELEASED : ReleaseStatus.FLAGGED);
        }
    }

    @Test
    void deterministicAdviceNeverClassifiesNaAsImprovementAndKeepsBucketsDisjoint() {
        Random rnd = new Random(SEED + 5);
        for (int r = 0; r < ROUNDS; r++) {
            int n = rnd.nextInt(8);
            List<EvaluationDtos.MatrixCell> cells = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                boolean na = rnd.nextBoolean();
                BigDecimal val = na ? null : BigDecimal.valueOf(rnd.nextInt(401), 2);
                cells.add(new EvaluationDtos.MatrixCell(
                        UUID.randomUUID(), "skill_" + i,
                        na ? ScoreStatus.NOT_ASSESSED : ScoreStatus.SCORED,
                        val, null, na ? 0 : 1, ReleaseStatus.APPROVED));
            }
            EvaluationDtos.CandidateAdvice advice = adviceBuilder.build(cells);

            Set<String> sSet = new HashSet<>(advice.strengths());
            Set<String> iSet = new HashSet<>(advice.improvements());
            Set<String> nSet = new HashSet<>(advice.notAssessed());
            assertThat(Collections.disjoint(sSet, iSet)).isTrue();
            assertThat(Collections.disjoint(sSet, nSet)).isTrue();
            assertThat(Collections.disjoint(iSet, nSet)).isTrue();

            for (EvaluationDtos.MatrixCell c : cells) {
                if (c.status() == ScoreStatus.NOT_ASSESSED) {
                    assertThat(nSet).contains(c.skillName());
                    assertThat(iSet).doesNotContain(c.skillName());
                } else if (c.value().compareTo(new BigDecimal("3.00")) >= 0) {
                    assertThat(sSet).contains(c.skillName());
                } else if (c.value().compareTo(new BigDecimal("2.00")) <= 0) {
                    assertThat(iSet).contains(c.skillName());
                }
            }
        }
    }

    @Test
    void judgeParserRejectsAllMalformedOutOfRangeOrExtraKeyPayloads() {
        TwoRunResponseJudge judge = new TwoRunResponseJudge(
                ChatClient.builder(mock(ChatModel.class)),
                mock(ContextCreationService.class),
                JsonMapper.builder().build());

        String[] badPayloads = {
            null, "", "   ", "not json", "[]", "{}",
            "{\"level\":-1,\"verbatimQuote\":\"exact quote\",\"rationale\":\"ok\"}",
            "{\"level\":5,\"verbatimQuote\":\"exact quote\",\"rationale\":\"ok\"}",
            "{\"level\":2.5,\"verbatimQuote\":\"exact quote\",\"rationale\":\"ok\"}",
            "{\"level\":\"3\",\"verbatimQuote\":\"exact quote\",\"rationale\":\"ok\"}",
            "{\"level\":3,\"verbatimQuote\":\"exact quote\",\"rationale\":\"ok\",\"rank\":1}",
            "{\"level\":3,\"verbatimQuote\":\"exact quote\",\"rationale\":\"ok\",\"fit\":3.5}",
            "{\"level\":3,\"verbatimQuote\":\"exact quote\",\"rationale\":\"ok\",\"confidence\":0.9}",
            "{\"level\":3,\"verbatimQuote\":\"exact quote\",\"rationale\":\"ok\",\"total\":10}",
            "{\"level\":3,\"verbatimQuote\":123,\"rationale\":\"ok\"}",
            "{\"level\":3,\"verbatimQuote\":\"exact quote\",\"rationale\":null}",
            // REQ-EP-10: prose around a valid object is accepted now; see TwoRunResponseJudgeTest.
            "{\"level\":3,\"verbatimQuote\":\"exact quote\",\"rationale\":\"ok\",\"suggestion\":42}"
        };

        for (String payload : badPayloads) {
            TwoRunResponseJudge.ParsedRun parsed = judge.parseStrict(payload);
            assertThat(parsed.valid()).as("payload must fail strict parse: %s", payload).isFalse();
            assertThat(parsed.level()).isNull();
        }

        for (int lvl = 0; lvl <= 4; lvl++) {
            String ok = "{\"level\":" + lvl + ",\"verbatimQuote\":\"exact verified text\",\"rationale\":\"good\"}";
            TwoRunResponseJudge.ParsedRun parsed = judge.parseStrict(ok);
            assertThat(parsed.valid()).isTrue();
            assertThat(parsed.level()).isEqualTo(lvl);
        }
    }
}
