package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import de.fhdortmund.nifty.evaluation.service.ConfidenceCalculator.ConfidenceSignals;
import de.fhdortmund.nifty.evaluation.service.SkillFormulaCalculator.CalculatedSkillScore;
import de.fhdortmund.nifty.evaluation.service.SkillFormulaCalculator.FormulaResult;
import de.fhdortmund.nifty.evaluation.service.SkillFormulaCalculator.QuestionWeight;
import de.fhdortmund.nifty.evaluation.service.SkillFormulaCalculator.ScoredEntryInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spec-derived invariant (property) tests for the evaluation slice: REQ-EP-01..03/07, REQ-FE-03/06,
 * REQ-SM-01/05 (AGENTS.md section 2 items 1-5; Decisions 2.10-2.12, 2.20, 2.22, 2.24).
 * Plain JUnit loops, fixed seed, no Spring. Oracles are written from the EARS text, not from production code.
 * Complements (does not replace) {@link EvaluationInvariantPropertyTest}.
 */
class EvaluationSpecInvariantPropertyTest {

    private static final long SEED = 20261126L;
    private static final int ROUNDS = 400;
    private static final BigDecimal CAP = new BigDecimal("0.5000");

    private final VerbatimQuoteVerifier verifier = new VerbatimQuoteVerifier();
    private final SkillFormulaCalculator formula = new SkillFormulaCalculator();
    private final ConfidenceCalculator confidence = new ConfidenceCalculator();
    private final ReleaseGateEvaluator gate = new ReleaseGateEvaluator();

    // ------------------------------------------------------------------ 1. quote verification (REQ-EP-01)

    private static final String[] WORDS = {"wrap", "transaction", "index", "email", "rollback", "isolation",
            "deadlock", "checksum", "cache", "retry", "breaker", "spring", "flyway", "commit", "column"};

    private static String sentence(Random rnd, int words, String tag) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words; i++) {
            if (i > 0) {
                sb.append(rnd.nextBoolean() ? " " : (rnd.nextBoolean() ? "\n" : "  \t"));
            }
            sb.append(WORDS[rnd.nextInt(WORDS.length)]);
        }
        return sb.append(' ').append(tag).toString();
    }

    private static String collapse(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("REQ-EP-01 shouldVerifyEveryExactSubstringAndRejectSingleCharacterChange")
    void shouldVerifyEveryExactSubstringAndRejectSingleCharacterChange() {
        Random rnd = new Random(SEED);
        for (int r = 0; r < ROUNDS; r++) {
            // lowercase-only answer: an uppercase letter or '#' can never occur in it by accident
            String answer = sentence(rnd, 6 + rnd.nextInt(12), "tag" + r);
            String flat = collapse(answer);
            int len = 8 + rnd.nextInt(Math.max(1, flat.length() - 8));
            int from = rnd.nextInt(flat.length() - len + 1);
            String quote = flat.substring(from, from + len);
            if (quote.isBlank() || collapse(quote).length() < 8) {
                continue;
            }
            assertThat(verifier.isVerified(answer, quote)).as("exact substring '%s'", quote).isTrue();

            int pos;
            do {
                pos = rnd.nextInt(quote.length());
            } while (Character.isWhitespace(quote.charAt(pos)));
            char orig = quote.charAt(pos);
            String replaced = quote.substring(0, pos) + '#' + quote.substring(pos + 1);
            assertThat(verifier.isVerified(answer, replaced)).as("char changed '%s'", replaced).isFalse();
            String cased = quote.substring(0, pos) + Character.toUpperCase(orig) + quote.substring(pos + 1);
            if (Character.isLetter(orig)) {
                assertThat(verifier.isVerified(answer, cased)).as("case change '%s'", cased).isFalse();
            }
            assertThat(verifier.isVerified(answer, quote + "#")).as("appended char").isFalse();
        }
    }

    @Test
    @DisplayName("REQ-EP-01 shouldDiscardQuoteNotVerbatimInTranscript: spanning two entries or from another entry")
    void shouldDiscardQuoteNotVerbatimInTranscript() {
        Random rnd = new Random(SEED + 1);
        for (int r = 0; r < ROUNDS; r++) {
            String a = sentence(rnd, 5 + rnd.nextInt(8), "alpha" + r);
            String b = "omega" + r + " " + sentence(rnd, 5 + rnd.nextInt(8), "beta");
            String fa = collapse(a);
            String fb = collapse(b);
            // tail of A glued to head of B (the way two adjacent ledger entries would read)
            String tail = fa.substring(Math.max(0, fa.length() - 4 - rnd.nextInt(10)));
            String head = fb.substring(0, Math.min(fb.length(), 6 + rnd.nextInt(10)));
            String spanning = tail + (rnd.nextBoolean() ? " " : "\n") + head;
            assertThat(verifier.isVerified(a, spanning)).as("spanning quote vs A").isFalse();
            assertThat(verifier.isVerified(b, spanning)).as("spanning quote vs B").isFalse();
            // a quote taken from B and verified against A (entryId mismatch) must fail
            String fromB = collapse(head).length() >= 8 ? head : fb;
            assertThat(verifier.isVerified(a, fromB)).as("quote of B vs entry A").isFalse();
            assertThat(verifier.isVerified(b, fromB)).as("sanity: quote of B vs B").isTrue();
        }
    }

    @Test
    @DisplayName("REQ-EP-01 blank, short, null quotes never verify; line break scenario verifies; no Unicode folding")
    void shouldRejectBlankShortAndNullQuotesAndKeepUnicodeStrict() {
        Random rnd = new Random(SEED + 2);
        String answer = "I would wrap it in a\ntransaction and add an index on email";
        assertThat(verifier.isVerified(answer, "wrap it in a transaction")).isTrue(); // spec scenario
        assertThat(verifier.isVerified(null, "wrap it in a transaction")).isFalse();
        assertThat(verifier.isVerified(answer, null)).isFalse();
        assertThat(verifier.isVerified("", "")).isFalse();
        assertThat(verifier.isVerified("        ", "        ")).isFalse();
        for (int r = 0; r < ROUNDS; r++) {
            String ws = " ".repeat(rnd.nextInt(30)) + (rnd.nextBoolean() ? "\n\t" : "");
            assertThat(verifier.isVerified(answer, ws)).as("blank quote").isFalse();
            int n = 1 + rnd.nextInt(7); // collapsed length 1..7 is below the 8 character minimum
            String shortQuote = answer.substring(2, 2 + n);
            if (!shortQuote.isBlank() && collapse(shortQuote).length() < 8) {
                assertThat(verifier.isVerified(answer, shortQuote)).as("short '%s'", shortQuote).isFalse();
            }
        }
        assertThat(verifier.isVerified("wrap it in", "wrap it in")).isTrue(); // exactly 10 chars
        assertThat(verifier.isVerified("12345678", "12345678")).isTrue(); // boundary: exactly 8
        assertThat(verifier.isVerified("1234567", "1234567")).isFalse(); // boundary: 7
        // the spec changes only whitespace runs: precomposed vs decomposed e-acute are different characters
        assertThat(verifier.isVerified("café au lait is fine", "café au lait")).isTrue();
        assertThat(verifier.isVerified("café au lait is fine", "café au lait")).isFalse();
        assertThat(verifier.isVerified("Spring Boot rocks", "spring boot rocks")).isFalse(); // case-sensitive
    }

    // ------------------------------------------------------------------ fixtures for formula tests

    private static BigDecimal d(long unscaled, int scale) {
        return BigDecimal.valueOf(unscaled, scale);
    }

    private static ScoredEntryInput entry(UUID q, UUID skill, EntryOrigin origin, BigDecimal weight,
            Integer level, boolean verified, boolean overridden) {
        return new ScoredEntryInput(UUID.randomUUID(), q, skill, origin, weight, level, d(5000, 4),
                verified, overridden, "pending");
    }

    private record Fx(List<UUID> skills, List<QuestionWeight> questions, List<ScoredEntryInput> evals,
            Set<UUID> answered) {
        FormulaResult run(SkillFormulaCalculator f) {
            return f.calculate(skills, questions, evals, answered, CAP);
        }
    }

    private static Fx randomFixture(Random rnd, boolean allowZeroWeights) {
        int sc = 1 + rnd.nextInt(5);
        int qc = 1 + rnd.nextInt(5);
        List<UUID> skills = new ArrayList<>();
        for (int i = 0; i < sc; i++) {
            skills.add(UUID.randomUUID());
        }
        List<QuestionWeight> qs = new ArrayList<>();
        Set<UUID> answered = new HashSet<>();
        for (int i = 0; i < qc; i++) {
            UUID id = UUID.randomUUID();
            BigDecimal w = allowZeroWeights && rnd.nextInt(3) == 0 ? BigDecimal.ZERO : d(25 + rnd.nextInt(476), 2);
            qs.add(new QuestionWeight(id, w));
            if (rnd.nextInt(4) != 0) {
                answered.add(id);
            }
        }
        List<ScoredEntryInput> evals = new ArrayList<>();
        int n = rnd.nextInt(14);
        for (int i = 0; i < n; i++) {
            QuestionWeight q = qs.get(rnd.nextInt(qc));
            EntryOrigin origin = EntryOrigin.values()[rnd.nextInt(EntryOrigin.values().length)];
            evals.add(entry(q.coreQuestionId(), skills.get(rnd.nextInt(sc)), origin, q.weight(),
                    rnd.nextInt(6) == 0 ? null : rnd.nextInt(5), rnd.nextInt(3) != 0, rnd.nextInt(8) == 0));
        }
        return new Fx(skills, qs, evals, answered);
    }

    private static boolean eligible(ScoredEntryInput e) {
        return e.effectiveLevel() != null && (e.quoteVerified() || e.overridden());
    }

    private static List<ScoredEntryInput> eligibleFor(Fx fx, UUID skill) {
        List<ScoredEntryInput> out = new ArrayList<>();
        for (ScoredEntryInput e : fx.evals()) {
            if (e.skillId().equals(skill) && eligible(e)) {
                out.add(e);
            }
        }
        return out;
    }

    private static Map<UUID, CalculatedSkillScore> byId(FormulaResult r) {
        Map<UUID, CalculatedSkillScore> m = new HashMap<>();
        for (CalculatedSkillScore s : r.skillScores()) {
            m.put(s.skillId(), s);
        }
        return m;
    }

    /** REQ-FE-06 water-filling, written literally from the spec text. */
    private static double specScore(List<ScoredEntryInput> es) {
        int n = es.size();
        if (n == 1) {
            return es.get(0).effectiveLevel();
        }
        double[] w = new double[n];
        double total = 0;
        for (int i = 0; i < n; i++) {
            w[i] = es.get(i).coreQuestionWeight().doubleValue() * (es.get(i).origin() == EntryOrigin.INVENTED ? 0.5 : 1.0);
            total += w[i];
        }
        double[] share = new double[n];
        boolean[] capped = new boolean[n];
        for (int i = 0; i < n; i++) {
            share[i] = w[i] / total;
        }
        while (true) {
            boolean any = false;
            for (int i = 0; i < n; i++) {
                if (!capped[i] && share[i] > 0.5 + 1e-12) {
                    capped[i] = true;
                    any = true;
                }
            }
            if (!any) {
                break;
            }
            int cappedCount = 0;
            double free = 0;
            for (int i = 0; i < n; i++) {
                if (capped[i]) {
                    cappedCount++;
                } else {
                    free += w[i];
                }
            }
            for (int i = 0; i < n; i++) {
                share[i] = capped[i] ? 0.5 : (1.0 - 0.5 * cappedCount) * w[i] / free;
            }
        }
        double s = 0;
        for (int i = 0; i < n; i++) {
            s += share[i] * es.get(i).effectiveLevel();
        }
        return s;
    }

    private static void assertScoreClose(BigDecimal actual, double expected, String msg) {
        assertThat(actual.doubleValue()).as(msg).isBetween(expected - 0.005 - 1e-6, expected + 0.005 + 1e-6);
    }

    // ------------------------------------------------------------------ 2. explicit N/A (REQ-EP-02, REQ-FE-03)

    @Test
    @DisplayName("REQ-EP-02 shouldRecordMissingEvidenceAsNAAndExcludeFromFit")
    void shouldRecordMissingEvidenceAsNAAndExcludeFromFit() {
        Random rnd = new Random(SEED + 3);
        int naSkills = 0;
        int allNaRuns = 0;
        for (int r = 0; r < ROUNDS; r++) {
            Fx fx = randomFixture(rnd, false);
            FormulaResult res = fx.run(formula);
            double sum = 0;
            double min = Double.MAX_VALUE;
            double max = -1;
            int scored = 0;
            for (UUID skill : fx.skills()) {
                CalculatedSkillScore s = byId(res).get(skill);
                if (eligibleFor(fx, skill).isEmpty()) {
                    naSkills++;
                    assertThat(s.status()).isEqualTo(ScoreStatus.NOT_ASSESSED);
                    assertThat(s.score()).as("N/A is never 0 or 1").isNull();
                } else {
                    assertThat(s.status()).isEqualTo(ScoreStatus.SCORED);
                    assertThat(s.score()).isBetween(BigDecimal.ZERO, new BigDecimal("4.00"));
                    scored++;
                    sum += s.score().doubleValue();
                    min = Math.min(min, s.score().doubleValue());
                    max = Math.max(max, s.score().doubleValue());
                }
            }
            if (scored == 0) {
                allNaRuns++;
                assertThat(res.fitScore()).as("all N/A -> fit is N/A (null), not 0").isNull();
            } else {
                assertThat(res.fitScore()).isNotNull();
                assertThat(res.fitScore().doubleValue()).isBetween(sum / scored - 0.005 - 1e-9, sum / scored + 0.005 + 1e-9);
                assertThat(res.fitScore().doubleValue()).isBetween(min - 1e-9, max + 1e-9);
            }
            // adding N/A skills never changes fit, scores or coverage
            Fx more = new Fx(new ArrayList<>(fx.skills()), fx.questions(), fx.evals(), fx.answered());
            more.skills().add(UUID.randomUUID());
            more.skills().add(UUID.randomUUID());
            FormulaResult res2 = more.run(formula);
            assertThat(res2.fitScore()).isEqualTo(res.fitScore());
            assertThat(res2.coverage()).isEqualTo(res.coverage());
            // evidence that failed verification (and was not overridden) is discarded: adding it changes nothing
            List<ScoredEntryInput> withJunk = new ArrayList<>(fx.evals());
            for (int j = 0; j < 1 + rnd.nextInt(3); j++) {
                QuestionWeight q = fx.questions().get(rnd.nextInt(fx.questions().size()));
                withJunk.add(entry(q.coreQuestionId(), fx.skills().get(rnd.nextInt(fx.skills().size())),
                        EntryOrigin.CORE, q.weight(), rnd.nextInt(5), false, false));
            }
            FormulaResult res3 = new Fx(fx.skills(), fx.questions(), withJunk, fx.answered()).run(formula);
            assertThat(res3.fitScore()).isEqualTo(res.fitScore());
            assertThat(res3.coverage()).isEqualTo(res.coverage());
            for (UUID skill : fx.skills()) {
                assertThat(byId(res3).get(skill).score()).isEqualTo(byId(res).get(skill).score());
            }
        }
        assertThat(naSkills).as("generator exercised N/A").isGreaterThan(50);
        assertThat(allNaRuns).as("generator exercised all-N/A").isGreaterThan(5);
    }

    // ------------------------------------------------------------------ 3. Formula B (REQ-FE-06, Decision 2.22)

    @Test
    @DisplayName("REQ-FE-06 spec scenarios: weight cap, follow-up inheritance, cap with three entries")
    void shouldMatchSpecScenariosForCapAndFollowUpInheritance() {
        UUID git = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID q3 = UUID.randomUUID();
        List<QuestionWeight> qs = List.of(new QuestionWeight(q1, d(300, 2)), new QuestionWeight(q2, d(100, 2)),
                new QuestionWeight(q3, d(100, 2)));
        Set<UUID> all = Set.of(q1, q2, q3);
        FormulaResult cap2 = formula.calculate(List.of(git), qs, List.of(
                entry(q1, git, EntryOrigin.CORE, d(300, 2), 4, true, false),
                entry(q2, git, EntryOrigin.CORE, d(100, 2), 2, true, false)), all, CAP);
        assertThat(cap2.skillScores().get(0).score()).isEqualByComparingTo("3.00");
        FormulaResult cap3 = formula.calculate(List.of(git), qs, List.of(
                entry(q1, git, EntryOrigin.CORE, d(300, 2), 4, true, false),
                entry(q2, git, EntryOrigin.CORE, d(100, 2), 2, true, false),
                entry(q3, git, EntryOrigin.CORE, d(100, 2), 0, true, false)), all, CAP);
        assertThat(cap3.skillScores().get(0).score()).isEqualByComparingTo("2.50");
        FormulaResult follow = formula.calculate(List.of(git), qs, List.of(
                entry(q2, git, EntryOrigin.CORE, d(200, 2), 3, true, false),
                entry(q2, git, EntryOrigin.FOLLOW_UP, d(200, 2), 1, true, false)), all, CAP);
        assertThat(follow.skillScores().get(0).score()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("REQ-FE-06 shouldComputeCappedWeightedMeanWithFollowUpAndInventedWeights")
    void shouldComputeCappedWeightedMeanWithFollowUpAndInventedWeights() {
        Random rnd = new Random(SEED + 4);
        int capBinding = 0;
        for (int r = 0; r < ROUNDS; r++) {
            Fx fx = randomFixture(rnd, false);
            FormulaResult res = fx.run(formula);
            for (UUID skill : fx.skills()) {
                List<ScoredEntryInput> es = eligibleFor(fx, skill);
                if (es.isEmpty()) {
                    continue;
                }
                double expected = specScore(es);
                assertScoreClose(byId(res).get(skill).score(), expected, "round " + r);
                int min = 4;
                int max = 0;
                double wsum = 0;
                double wmax = 0;
                for (ScoredEntryInput e : es) {
                    min = Math.min(min, e.effectiveLevel());
                    max = Math.max(max, e.effectiveLevel());
                    double w = e.coreQuestionWeight().doubleValue() * (e.origin() == EntryOrigin.INVENTED ? 0.5 : 1);
                    wsum += w;
                    wmax = Math.max(wmax, w);
                }
                assertThat(byId(res).get(skill).score().doubleValue()).isBetween(min - 1e-9, max + 1e-9);
                if (es.size() >= 2 && wmax / wsum > 0.5) {
                    capBinding++;
                }
            }
        }
        assertThat(capBinding).as("generator exercised a binding cap").isGreaterThan(50);
    }

    @Test
    @DisplayName("REQ-FE-06 shouldLimitOneEntryContributionToHalfOfSkillScore (D2 cap, black box)")
    void shouldLimitOneEntryContributionToHalfOfSkillScore() {
        Random rnd = new Random(SEED + 5);
        int checked = 0;
        for (int r = 0; r < ROUNDS; r++) {
            Fx fx = randomFixture(rnd, false);
            for (UUID skill : fx.skills()) {
                List<ScoredEntryInput> es = eligibleFor(fx, skill);
                if (es.size() < 2) {
                    continue;
                }
                ScoredEntryInput target = es.get(rnd.nextInt(es.size()));
                int newLevel = (target.effectiveLevel() + 1 + rnd.nextInt(4)) % 5;
                List<ScoredEntryInput> changed = new ArrayList<>();
                for (ScoredEntryInput e : fx.evals()) {
                    changed.add(e == target
                            ? new ScoredEntryInput(e.entryId(), e.coreQuestionId(), e.skillId(), e.origin(),
                                    e.coreQuestionWeight(), newLevel, e.confidence(), e.quoteVerified(),
                                    e.overridden(), e.reviewStatus())
                            : e);
                }
                BigDecimal before = byId(fx.run(formula)).get(skill).score();
                BigDecimal after = byId(new Fx(fx.skills(), fx.questions(), changed, fx.answered()).run(formula))
                        .get(skill).score();
                double delta = Math.abs(after.doubleValue() - before.doubleValue());
                assertThat(delta).as("one entry moved the skill by more than 0.50*|dLevel|")
                        .isLessThanOrEqualTo(0.5 * Math.abs(newLevel - target.effectiveLevel()) + 0.0101);
                checked++;
            }
        }
        assertThat(checked).isGreaterThan(100);
    }

    @Test
    @DisplayName("REQ-FE-01 shouldNotChangeResultsWhenEntriesOrSkillsArePermuted")
    void shouldNotChangeResultsWhenEntriesOrSkillsArePermuted() {
        Random rnd = new Random(SEED + 6);
        for (int r = 0; r < ROUNDS; r++) {
            Fx fx = randomFixture(rnd, false);
            FormulaResult base = fx.run(formula);
            List<ScoredEntryInput> evals = new ArrayList<>(fx.evals());
            List<UUID> skills = new ArrayList<>(fx.skills());
            List<QuestionWeight> qs = new ArrayList<>(fx.questions());
            Collections.shuffle(evals, rnd);
            Collections.shuffle(skills, rnd);
            Collections.shuffle(qs, rnd);
            FormulaResult perm = new Fx(skills, qs, evals, fx.answered()).run(formula);
            // 0.01 tolerance: floating point summation order may flip the 2nd decimal at a rounding boundary
            for (UUID s : fx.skills()) {
                CalculatedSkillScore a = byId(base).get(s);
                CalculatedSkillScore b = byId(perm).get(s);
                assertThat(b.status()).isEqualTo(a.status());
                if (a.score() == null) {
                    assertThat(b.score()).isNull();
                } else {
                    assertThat(b.score().subtract(a.score()).abs().doubleValue()).isLessThanOrEqualTo(0.0100001);
                }
            }
            assertThat(perm.coverage()).isEqualByComparingTo(base.coverage());
            if (base.fitScore() == null) {
                assertThat(perm.fitScore()).isNull();
            } else {
                assertThat(perm.fitScore().subtract(base.fitScore()).abs().doubleValue()).isLessThanOrEqualTo(0.0100001);
            }
        }
    }

    @Test
    @DisplayName("REQ-FE-06 shouldKeepFitAndCoverageWhenAllCoreWeightsAreScaledByPositiveConstant")
    void shouldKeepFitAndCoverageWhenAllCoreWeightsAreScaledByPositiveConstant() {
        // Implied by the spec: score is a weighted mean, the cap is a weight SHARE, coverage is a weight RATIO.
        Random rnd = new Random(SEED + 7);
        BigDecimal[] factors = {new BigDecimal("0.5"), new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("10")};
        for (int r = 0; r < ROUNDS; r++) {
            Fx fx = randomFixture(rnd, false);
            BigDecimal c = factors[rnd.nextInt(factors.length)];
            List<QuestionWeight> qs = new ArrayList<>();
            for (QuestionWeight q : fx.questions()) {
                qs.add(new QuestionWeight(q.coreQuestionId(), q.weight().multiply(c)));
            }
            List<ScoredEntryInput> evals = new ArrayList<>();
            for (ScoredEntryInput e : fx.evals()) {
                evals.add(new ScoredEntryInput(e.entryId(), e.coreQuestionId(), e.skillId(), e.origin(),
                        e.coreQuestionWeight().multiply(c), e.effectiveLevel(), e.confidence(), e.quoteVerified(),
                        e.overridden(), e.reviewStatus()));
            }
            FormulaResult a = fx.run(formula);
            FormulaResult b = new Fx(fx.skills(), qs, evals, fx.answered()).run(formula);
            assertThat(b.coverage().subtract(a.coverage()).abs().doubleValue()).isLessThanOrEqualTo(0.0001001);
            if (a.fitScore() == null) {
                assertThat(b.fitScore()).isNull();
            } else {
                assertThat(b.fitScore().subtract(a.fitScore()).abs().doubleValue()).isLessThanOrEqualTo(0.0100001);
            }
        }
    }

    @Test
    @DisplayName("REQ-FE-01 shouldNeverProduceNaNInfinityOrOutOfRangeForZeroWeightsAndEmptyInput")
    void shouldNeverProduceNaNInfinityOrOutOfRangeForZeroWeightsAndEmptyInput() {
        FormulaResult empty = formula.calculate(List.of(), List.of(), List.of(), Set.of(), CAP);
        assertThat(empty.skillScores()).isEmpty();
        assertThat(empty.fitScore()).isNull();
        assertThat(empty.coverage()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);

        Random rnd = new Random(SEED + 8);
        for (int r = 0; r < ROUNDS; r++) {
            Fx fx = randomFixture(rnd, true);
            FormulaResult res = fx.run(formula);
            assertThat(res.coverage()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
            for (UUID skill : fx.skills()) {
                List<ScoredEntryInput> es = eligibleFor(fx, skill);
                CalculatedSkillScore s = byId(res).get(skill);
                if (es.isEmpty()) {
                    assertThat(s.score()).isNull();
                    continue;
                }
                int min = 4;
                int max = 0;
                for (ScoredEntryInput e : es) {
                    min = Math.min(min, e.effectiveLevel());
                    max = Math.max(max, e.effectiveLevel());
                }
                assertThat(s.score()).as("SCORED skill needs a score").isNotNull();
                assertThat(s.score().doubleValue())
                        .as("weighted mean of levels %d..%d must stay in that range (round %d)", min, max, r)
                        .isBetween(min - 1e-9, max + 1e-9);
            }
        }
    }

    // ------------------------------------------------------------------ 4. coverage (REQ-FE-03, 2.12, 2.20, 2.22)

    @Test
    @DisplayName("REQ-FE-03 shouldWeightCoverageByAnsweredScoredCoreQuestionsAndNeverLowerItWhenAnswering")
    void shouldWeightCoverageByAnsweredScoredCoreQuestionsAndNeverLowerItWhenAnswering() {
        Random rnd = new Random(SEED + 9);
        for (int r = 0; r < ROUNDS; r++) {
            Fx raw = randomFixture(rnd, false);
            // invented entries are covered by shouldNotCountInventedEntriesTowardCoverage; keep this oracle isolated
            List<ScoredEntryInput> noInvented = new ArrayList<>();
            for (ScoredEntryInput e : raw.evals()) {
                noInvented.add(e.origin() != EntryOrigin.INVENTED ? e
                        : new ScoredEntryInput(e.entryId(), e.coreQuestionId(), e.skillId(), EntryOrigin.FOLLOW_UP,
                                e.coreQuestionWeight(), e.effectiveLevel(), e.confidence(), e.quoteVerified(),
                                e.overridden(), e.reviewStatus()));
            }
            Fx fx = new Fx(raw.skills(), raw.questions(), noInvented, raw.answered());
            FormulaResult res = fx.run(formula);
            Set<UUID> scoredQ = new HashSet<>();
            for (ScoredEntryInput e : fx.evals()) {
                if (eligible(e)) {
                    scoredQ.add(e.coreQuestionId());
                }
            }
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal covered = BigDecimal.ZERO;
            for (QuestionWeight q : fx.questions()) {
                total = total.add(q.weight());
                if (fx.answered().contains(q.coreQuestionId()) && scoredQ.contains(q.coreQuestionId())) {
                    covered = covered.add(q.weight());
                }
            }
            BigDecimal expected = covered.divide(total, 10, RoundingMode.HALF_UP);
            assertThat(res.coverage().subtract(expected).abs().doubleValue()).isLessThanOrEqualTo(0.00005 + 1e-9);
            assertThat(res.coverage()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);

            // answering one more question never lowers coverage
            Set<UUID> more = new HashSet<>(fx.answered());
            more.add(fx.questions().get(rnd.nextInt(fx.questions().size())).coreQuestionId());
            BigDecimal after = new Fx(fx.skills(), fx.questions(), fx.evals(), more).run(formula).coverage();
            assertThat(after).isGreaterThanOrEqualTo(res.coverage());
        }
    }

    @Test
    @DisplayName("REQ-FE-03 spec scenario: weights 1,1,1,2 with question 4 unanswered gives coverage 0.60")
    void shouldReportCoverageSixtyPercentForSpecScenario() {
        UUID s = UUID.randomUUID();
        List<UUID> q = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        int[] w = {1, 1, 1, 2};
        List<QuestionWeight> qs = new ArrayList<>();
        List<ScoredEntryInput> evals = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            qs.add(new QuestionWeight(q.get(i), d(w[i] * 100L, 2)));
            if (i < 3) {
                evals.add(entry(q.get(i), s, EntryOrigin.CORE, d(100, 2), 3, true, false));
            }
        }
        FormulaResult res = formula.calculate(List.of(s), qs, evals, Set.of(q.get(0), q.get(1), q.get(2)), CAP);
        assertThat(res.coverage()).isEqualByComparingTo("0.60");
        assertThat(res.fitScore()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("Decision 2.22 shouldNotCountInventedEntriesTowardCoverage")
    void shouldNotCountInventedEntriesTowardCoverage() {
        // 2.22: "An invented-question entry ... never counts toward coverage."
        Random rnd = new Random(SEED + 10);
        for (int r = 0; r < 50; r++) {
            UUID skill = UUID.randomUUID();
            UUID q1 = UUID.randomUUID();
            UUID q2 = UUID.randomUUID();
            List<QuestionWeight> qs = List.of(new QuestionWeight(q1, d(100, 2)), new QuestionWeight(q2, d(100, 2)));
            // q1 has a CORE entry; q2 has only an INVENTED entry attached to it
            List<ScoredEntryInput> evals = List.of(
                    entry(q1, skill, EntryOrigin.CORE, d(100, 2), rnd.nextInt(5), true, false),
                    entry(q2, skill, EntryOrigin.INVENTED, d(100, 2), rnd.nextInt(5), true, false));
            FormulaResult res = formula.calculate(List.of(skill), qs, evals, Set.of(q1, q2), CAP);
            assertThat(res.coverage()).as("only q1 may count: expected 0.50").isEqualByComparingTo("0.50");
        }
    }

    // ------------------------------------------------------------------ 5. confidence (REQ-EP-03, 2.11)

    private static ConfidenceSignals randomSignals(Random rnd) {
        int total = rnd.nextInt(5);
        int verified = total == 0 ? 0 : rnd.nextInt(total + 1);
        Integer r1 = rnd.nextInt(8) == 0 ? null : rnd.nextInt(5);
        Integer r2 = rnd.nextInt(8) == 0 ? null : rnd.nextInt(5);
        EntryOrigin o = EntryOrigin.values()[rnd.nextInt(EntryOrigin.values().length)];
        return new ConfidenceSignals(verified, total, r1, r2, o, rnd.nextInt(300), rnd.nextInt(4), rnd.nextInt(4));
    }

    private static ConfidenceSignals with(ConfidenceSignals s, Integer r1, Integer r2) {
        return new ConfidenceSignals(s.verifiedEvidenceCount(), s.totalQuoteAttempts(), r1, r2, s.questionOrigin(),
                s.answerLength(), s.promptedTermsCount(), s.unpromptedTermsCount());
    }

    @Test
    @DisplayName("REQ-EP-03 shouldComputeConfidenceFromMeasurableSignalsOnlyAndLowerItOnJudgeDisagreement")
    void shouldComputeConfidenceFromMeasurableSignalsOnlyAndLowerItOnJudgeDisagreement() {
        Random rnd = new Random(SEED + 11);
        for (int r = 0; r < ROUNDS; r++) {
            ConfidenceSignals s = randomSignals(rnd);
            BigDecimal c = confidence.calculate(s);
            assertThat(c).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
            ConfidenceSignals copy = with(s, s.run1Level(), s.run2Level());
            assertThat(confidence.calculate(copy)).as("identical inputs -> identical output").isEqualByComparingTo(c);

            int a = rnd.nextInt(5);
            int b = rnd.nextInt(5);
            if (a != b) {
                BigDecimal agreed = confidence.calculate(with(s, a, a));
                BigDecimal disagreed = confidence.calculate(with(s, a, b));
                assertThat(disagreed).as("disagree (%d,%d) must be lower than agree", a, b).isLessThan(agreed);
                assertThat(confidence.calculate(with(s, a, b))).isEqualByComparingTo(confidence.calculate(with(s, b, a)));
            }
            // only agreement matters, not the absolute level the judges chose
            int shift = rnd.nextInt(3);
            assertThat(confidence.calculate(with(s, 0, 0))).isEqualByComparingTo(confidence.calculate(with(s, 1 + shift, 1 + shift)));
            assertThat(confidence.calculate(with(s, 0, 1))).isEqualByComparingTo(confidence.calculate(with(s, 2 + shift % 2, 3 + shift % 2)));

            // more measurable support never lowers confidence
            if (s.verifiedEvidenceCount() < s.totalQuoteAttempts()) {
                ConfidenceSignals more = new ConfidenceSignals(s.verifiedEvidenceCount() + 1, s.totalQuoteAttempts(),
                        s.run1Level(), s.run2Level(), s.questionOrigin(), s.answerLength(),
                        s.promptedTermsCount(), s.unpromptedTermsCount());
                assertThat(confidence.calculate(more)).isGreaterThanOrEqualTo(c);
            }
            ConfidenceSignals longer = new ConfidenceSignals(s.verifiedEvidenceCount(), s.totalQuoteAttempts(),
                    s.run1Level(), s.run2Level(), s.questionOrigin(), s.answerLength() + 1 + rnd.nextInt(200),
                    s.promptedTermsCount(), s.unpromptedTermsCount());
            assertThat(confidence.calculate(longer)).isGreaterThanOrEqualTo(c);
            ConfidenceSignals unprompted = new ConfidenceSignals(s.verifiedEvidenceCount(), s.totalQuoteAttempts(),
                    s.run1Level(), s.run2Level(), s.questionOrigin(), s.answerLength(),
                    s.promptedTermsCount(), s.unpromptedTermsCount() + 1);
            assertThat(confidence.calculate(unprompted)).isGreaterThanOrEqualTo(c);
        }
        for (var component : ConfidenceSignals.class.getRecordComponents()) {
            assertThat(component.getName().toLowerCase())
                    .as("signals must be measurable, never LLM self-report")
                    .doesNotContain("confidence", "selfreport", "rationale", "llm");
        }
    }

    // ------------------------------------------------------------------ 6. release gate (REQ-EP-07, SM-01, SM-05)

    private static BigDecimal unit(Random rnd) {
        return d(rnd.nextInt(10001), 4);
    }

    private ReleaseGateEvaluator.FlagEvaluation flags(boolean substantive, boolean verified, Integer r1, Integer r2,
            BigDecimal conf, BigDecimal thr, boolean paste, boolean inj) {
        return gate.evaluateResponseFlags(substantive, verified, verified, r1, r2, conf, thr, paste, inj, false);
    }

    private static boolean specFlagged(boolean substantive, boolean verified, Integer r1, Integer r2,
            BigDecimal conf, BigDecimal thr, boolean paste, boolean inj) {
        boolean scored = verified && (r1 != null || r2 != null);
        return paste || inj
                || (substantive && !verified)
                || (r1 != null && r2 != null && !r1.equals(r2))
                || (scored && conf.compareTo(thr) < 0);
    }

    @Test
    @DisplayName("REQ-EP-07 / REQ-SM-01 shouldAlwaysFlagPasteOrInjectionAndMatchSpecFlagRules")
    void shouldAlwaysFlagPasteOrInjectionAndMatchSpecFlagRules() {
        Random rnd = new Random(SEED + 12);
        int flaggedSeen = 0;
        int pendingSeen = 0;
        for (int r = 0; r < ROUNDS * 3; r++) {
            boolean subst = rnd.nextBoolean();
            boolean ver = rnd.nextBoolean();
            Integer r1 = rnd.nextInt(6) == 0 ? null : rnd.nextInt(5);
            Integer r2 = rnd.nextInt(6) == 0 ? null : (rnd.nextBoolean() ? r1 : Integer.valueOf(rnd.nextInt(5)));
            BigDecimal conf = unit(rnd);
            BigDecimal thr = unit(rnd);
            boolean paste = rnd.nextInt(4) == 0;
            boolean inj = rnd.nextInt(4) == 0;
            var out = flags(subst, ver, r1, r2, conf, thr, paste, inj);
            if (paste || inj) {
                assertThat(out.reviewStatus()).as("paste/injection is always flagged").isEqualTo(ReleaseStatus.FLAGGED);
                assertThat(out.flagReasons()).isNotBlank();
            }
            boolean expected = specFlagged(subst, ver, r1, r2, conf, thr, paste, inj);
            assertThat(out.reviewStatus() == ReleaseStatus.FLAGGED)
                    .as("s=%s v=%s r1=%s r2=%s c=%s t=%s p=%s i=%s", subst, ver, r1, r2, conf, thr, paste, inj)
                    .isEqualTo(expected);
            assertThat(out.reviewStatus() == ReleaseStatus.FLAGGED).isEqualTo(out.flagReasons() != null);
            if (expected) {
                flaggedSeen++;
            } else {
                pendingSeen++;
            }
        }
        assertThat(flaggedSeen).isGreaterThan(100);
        assertThat(pendingSeen).isGreaterThan(100);
    }

    @Test
    @DisplayName("REQ-SM-01 shouldNotFailConfidenceThresholdForNAScore")
    void shouldNotFailConfidenceThresholdForNAScore() {
        Random rnd = new Random(SEED + 13);
        for (int r = 0; r < ROUNDS; r++) {
            BigDecimal thr = d(1 + rnd.nextInt(10000), 4);
            BigDecimal conf = thr.multiply(unit(rnd)).setScale(4, RoundingMode.DOWN); // strictly below or equal
            // blank answer: no evidence, N/A, nothing else wrong
            var blank = flags(false, false, null, null, conf, thr, false, false);
            assertThat(blank.reviewStatus()).as("N/A without evidence").isEqualTo(ReleaseStatus.PENDING);
            // verified quote exists but the judges produced no level: still no score
            var noLevel = flags(true, true, null, null, BigDecimal.ZERO, BigDecimal.ONE, false, false);
            assertThat(noLevel.reviewStatus()).isEqualTo(ReleaseStatus.PENDING);
            // a real score at the same low confidence IS flagged (guards against a vacuous pass)
            if (conf.compareTo(thr) < 0) {
                var scored = flags(true, true, 2, 2, conf, thr, false, false);
                assertThat(scored.reviewStatus()).isEqualTo(ReleaseStatus.FLAGGED);
            }
        }
        // equality passes: "below the threshold" is strict
        assertThat(flags(true, true, 2, 2, new BigDecimal("0.6000"), new BigDecimal("0.6000"), false, false)
                .reviewStatus()).isEqualTo(ReleaseStatus.PENDING);
    }

    @Test
    @DisplayName("REQ-SM-05 shouldAutoReleaseOnlyWhenEveryThresholdPassesCoverageMeetsMinimumAndNothingIsFlagged")
    void shouldAutoReleaseOnlyWhenEveryThresholdPassesCoverageMeetsMinimumAndNothingIsFlagged() {
        Random rnd = new Random(SEED + 14);
        int auto = 0;
        int notAuto = 0;
        for (int r = 0; r < ROUNDS * 2; r++) {
            int n = 1 + rnd.nextInt(5);
            BigDecimal coverage = unit(rnd);
            BigDecimal minCoverage = unit(rnd);
            List<ReleaseStatus> statuses = new ArrayList<>();
            List<Object[]> params = new ArrayList<>();
            boolean allPass = true;
            for (int i = 0; i < n; i++) {
                // bias towards clean responses so that auto-release actually happens
                boolean subst = true;
                boolean ver = rnd.nextInt(10) != 0;
                Integer r1 = rnd.nextInt(10) == 0 ? null : 2;
                Integer r2 = rnd.nextInt(10) == 0 ? null : (rnd.nextInt(8) == 0 ? 3 : 2);
                BigDecimal conf = unit(rnd);
                BigDecimal thr = conf.multiply(new BigDecimal("0.5")).add(d(rnd.nextInt(5001), 4)).min(BigDecimal.ONE);
                boolean paste = rnd.nextInt(15) == 0;
                boolean inj = rnd.nextInt(15) == 0;
                params.add(new Object[] {subst, ver, r1, r2, conf, thr, paste, inj});
                var out = flags(subst, ver, r1, r2, conf, thr, paste, inj);
                statuses.add(out.reviewStatus());
                allPass &= !specFlagged(subst, ver, r1, r2, conf, thr, paste, inj);
            }
            boolean expectAuto = allPass && coverage.compareTo(minCoverage) >= 0;
            var decision = gate.evaluateAttemptRelease(statuses, coverage, minCoverage);
            assertThat(decision.autoReleased()).isEqualTo(expectAuto);
            assertThat(decision.releaseStatus())
                    .isEqualTo(expectAuto ? ReleaseStatus.AUTO_RELEASED : ReleaseStatus.FLAGGED);
            if (expectAuto) {
                auto++;
            } else {
                notAuto++;
            }

            // lowering any threshold never turns auto_released into a non-released attempt
            if (expectAuto) {
                List<ReleaseStatus> lowered = new ArrayList<>();
                for (Object[] p : params) {
                    BigDecimal lowerThr = ((BigDecimal) p[5]).multiply(unit(rnd)).setScale(4, RoundingMode.DOWN);
                    lowered.add(flags((boolean) p[0], (boolean) p[1], (Integer) p[2], (Integer) p[3],
                            (BigDecimal) p[4], lowerThr, (boolean) p[6], (boolean) p[7]).reviewStatus());
                }
                assertThat(gate.evaluateAttemptRelease(lowered, coverage, minCoverage).autoReleased())
                        .as("lower threshold must keep auto_released").isTrue();
                // lowering the minimum coverage or raising coverage also keeps it
                assertThat(gate.evaluateAttemptRelease(statuses, coverage, minCoverage.multiply(unit(rnd))).autoReleased())
                        .isTrue();
            }
            // any flagged response blocks release whatever the coverage
            List<ReleaseStatus> withFlag = new ArrayList<>(statuses);
            withFlag.add(rnd.nextInt(withFlag.size() + 1), ReleaseStatus.FLAGGED);
            assertThat(gate.evaluateAttemptRelease(withFlag, BigDecimal.ONE, BigDecimal.ZERO).autoReleased()).isFalse();
        }
        assertThat(auto).as("generator exercised auto_released").isGreaterThan(20);
        assertThat(notAuto).as("generator exercised blocked attempts").isGreaterThan(50);
    }
}
