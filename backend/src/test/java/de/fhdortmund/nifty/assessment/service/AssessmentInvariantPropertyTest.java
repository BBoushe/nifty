package de.fhdortmund.nifty.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.AnswerTiming;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.AskCore;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.AskFollowUp;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.Finish;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.NextStep;
import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.InterviewerQuestion;
import de.fhdortmund.nifty.studio.service.view.InterviewerView;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Property-style tests (plain loops, fixed seed, no extra dependency) for REQ-AC-01, 02, 06, 08, 09, 14 and 15.
 * Every random input comes from {@link #SEED}, so a failure replays exactly. The oracles come from the spec text
 * (key table, thresholds), not from the production code.
 */
class AssessmentInvariantPropertyTest {

    private static final long SEED = 20261126L;
    private static final int ROUNDS = 2000;
    private static final Instant T0 = Instant.parse("2026-11-26T10:00:00Z");

    /** REQ-AC-15 key, written out from the spec: trait -> {normal item, reversed item} (1-based). */
    private static final Map<String, int[]> KEY = Map.of(
            "extraversion", new int[] {6, 1},
            "agreeableness", new int[] {2, 7},
            "conscientiousness", new int[] {8, 3},
            "neuroticism", new int[] {9, 4},
            "openness", new int[] {10, 5});

    @Test
    void interviewAsksEveryCoreQuestionOnceInOrderAndNeverBreaksTheFollowUpLimit() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            List<InterviewerQuestion> questions = randomQuestions(rnd);
            InterviewerView view = new InterviewerView(UUID.randomUUID(), "v1", "Vision", 1, false,
                    GroupingMode.HARD_SKILLS, questions);
            List<LedgerEntry> ledger = new ArrayList<>();
            List<UUID> coreAsked = new ArrayList<>();
            Map<UUID, Integer> followUps = new HashMap<>();
            int maxTurns = 0;
            for (InterviewerQuestion q : questions) {
                maxTurns += 1 + q.followUpLimit();
            }
            int previousRemaining = Integer.MAX_VALUE;
            boolean lastAnswerEligible = true;
            NextStep step = AttemptStateMachine.next(view, ledger, true);
            int turns = 0;
            while (!(step instanceof Finish)) {
                assertThat(++turns).as("round %d terminates", round).isLessThanOrEqualTo(maxTurns);
                InterviewerQuestion q;
                EntryOrigin origin;
                if (step instanceof AskCore core) {
                    q = core.question();
                    origin = EntryOrigin.CORE;
                    coreAsked.add(q.coreQuestionId());
                } else {
                    AskFollowUp followUp = (AskFollowUp) step;
                    q = followUp.question();
                    origin = EntryOrigin.FOLLOW_UP;
                    assertThat(lastAnswerEligible).as("follow-up only after a non-blank, on-time answer").isTrue();
                    assertThat(followUp.followUpsUsed()).isEqualTo(followUps.getOrDefault(q.coreQuestionId(), 0));
                    followUps.merge(q.coreQuestionId(), 1, Integer::sum);
                    assertThat(followUps.get(q.coreQuestionId())).isLessThanOrEqualTo(q.followUpLimit());
                }
                ledger.add(entry(ledger, LedgerEntry.INTERVIEWER, q, origin, q.text(), false));
                int remaining = AttemptStateMachine.questionsRemaining(view, ledger);
                assertThat(remaining).isLessThanOrEqualTo(previousRemaining).isGreaterThanOrEqualTo(0);
                previousRemaining = remaining;
                assertThat(AttemptStateMachine.openQuestion(ledger)).contains(ledger.getLast());

                int mode = rnd.nextInt(4); // 0 blank, 1 timed out, else on-time text
                boolean timedOut = mode == 1;
                String text = mode == 0 ? "" : "answer";
                ledger.add(entry(ledger, LedgerEntry.CANDIDATE, q, origin, text, timedOut));
                assertThat(AttemptStateMachine.openQuestion(ledger)).isEmpty();
                lastAnswerEligible = !text.isBlank() && !timedOut;
                step = AttemptStateMachine.next(view, ledger, lastAnswerEligible);
            }
            List<UUID> expectedOrder = new ArrayList<>();
            for (InterviewerQuestion q : questions) {
                expectedOrder.add(q.coreQuestionId());
            }
            assertThat(coreAsked).as("round %d", round).containsExactlyElementsOf(expectedOrder);
            assertThat(previousRemaining).isEqualTo(questions.isEmpty() ? Integer.MAX_VALUE : 0);
        }
    }

    @Test
    void answerTimingSplitsExactlyAtExpiresAtAndAtTheGraceEnd() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            Instant expiresAt = T0.plusSeconds(rnd.nextInt(600));
            Duration grace = Duration.ofMillis(rnd.nextInt(10_000));
            Instant submitted = expiresAt.plusMillis(rnd.nextInt(40_000) - 20_000);
            AnswerTiming timing = AttemptStateMachine.timing(expiresAt, submitted, grace);
            AnswerTiming expected = !submitted.isAfter(expiresAt) ? AnswerTiming.ON_TIME
                    : submitted.isAfter(expiresAt.plus(grace)) ? AnswerTiming.TOO_LATE : AnswerTiming.TIMED_OUT;
            assertThat(timing).as("round %d", round).isEqualTo(expected);
        }
    }

    @Test
    void pasteFlagFollowsTheSpecThresholdsAndIsMonotoneInTimeAndLength() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            int minTime = 1 + rnd.nextInt(120);
            int length = rnd.nextInt(1200);
            long elapsedMillis = rnd.nextInt(300_000);
            String content = "x".repeat(length);
            Instant submitted = T0.plusMillis(elapsedMillis);
            // REQ-AC-09 oracle: before askedAt + minimum time, or >= 150 chars faster than 12 chars per second.
            boolean expected = length > 0
                    && (elapsedMillis < minTime * 1000L || (length >= 150 && length * 1000.0 / elapsedMillis > 12.0));
            boolean actual = PasteVelocityDetector.pasteSuspected(T0, submitted, minTime, content);
            assertThat(actual).as("round %d: %d chars in %d ms, min %d s", round, length, elapsedMillis, minTime)
                    .isEqualTo(expected);
            if (actual) {
                assertThat(PasteVelocityDetector.pasteSuspected(T0, T0.plusMillis(elapsedMillis / 2), minTime, content))
                        .as("faster is never less suspicious").isTrue();
                assertThat(PasteVelocityDetector.pasteSuspected(T0, submitted, minTime, content + "y".repeat(50)))
                        .as("longer is never less suspicious").isTrue();
            }
        }
    }

    @Test
    void bfi10TraitsMatchTheKeyStayInRangeAndMirrorWhenEveryAnswerIsReversed() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            int[] items = new int[10];
            int[] mirrored = new int[10];
            for (int i = 0; i < 10; i++) {
                items[i] = 1 + rnd.nextInt(5);
                mirrored[i] = 6 - items[i];
            }
            Map<String, Double> traits = Bfi10ScoringService.score(items).asMap();
            Map<String, Double> mirror = Bfi10ScoringService.score(mirrored).asMap();
            assertThat(traits).containsOnlyKeys(KEY.keySet());
            for (Map.Entry<String, int[]> key : KEY.entrySet()) {
                int normal = items[key.getValue()[0] - 1];
                int reversed = 6 - items[key.getValue()[1] - 1];
                double expected = (normal + reversed) / 2.0;
                double actual = traits.get(key.getKey());
                assertThat(actual).as("round %d %s", round, key.getKey()).isEqualTo(expected);
                assertThat(actual).isBetween(1.0, 5.0);
                assertThat(actual * 2).isEqualTo(Math.rint(actual * 2)); // means of two integers: x.0 or x.5
                assertThat(mirror.get(key.getKey())).isEqualTo(6.0 - actual);
            }
        }
    }

    @Test
    void bfi10ValidationAcceptsExactlyTenAnswersFromOneToFiveAndNamesEveryOtherItem() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            int size = rnd.nextInt(4) == 0 ? rnd.nextInt(13) : 10;
            List<Integer> items = new ArrayList<>();
            List<String> expectedBad = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                int kind = rnd.nextInt(10);
                Integer value = kind == 0 ? null : kind == 1 ? rnd.nextInt(13) - 6 : 1 + rnd.nextInt(5);
                items.add(value);
                if (i < 10 && (value == null || value < 1 || value > 5)) {
                    expectedBad.add("items[" + i + "]");
                }
            }
            for (int i = size; i < 10; i++) {
                expectedBad.add("items[" + i + "]");
            }
            if (size > 10) {
                expectedBad.add("items");
            }
            if (expectedBad.isEmpty()) {
                int[] answers = Bfi10ScoringService.validate(items);
                for (int i = 0; i < 10; i++) {
                    assertThat(answers[i]).isEqualTo(items.get(i));
                }
            } else {
                assertThatThrownBy(() -> Bfi10ScoringService.validate(items))
                        .as("round %d", round)
                        .isInstanceOfSatisfying(FieldValidationException.class, ex -> assertThat(ex.getErrors())
                                .extracting(FieldErrorDetail::field).containsExactlyElementsOf(expectedBad));
            }
        }
    }

    @Test
    void storedTraitMeansConvertToTheSameValuesWithoutRescoring() {
        Bfi10Traits traits = new Bfi10Traits(new BigDecimal("1.50"), new BigDecimal("2.00"), new BigDecimal("3.50"),
                new BigDecimal("4.00"), new BigDecimal("5.00"));
        assertThat(traits.asMap()).containsExactly(
                Map.entry("conscientiousness", 1.5), Map.entry("agreeableness", 2.0), Map.entry("neuroticism", 3.5),
                Map.entry("extraversion", 4.0), Map.entry("openness", 5.0));
    }

    private static List<InterviewerQuestion> randomQuestions(Random rnd) {
        int count = rnd.nextInt(7);
        List<InterviewerQuestion> questions = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            questions.add(new InterviewerQuestion(UUID.randomUUID(), i, "Question " + i, 30, 180, rnd.nextInt(4),
                    List.of(UUID.randomUUID()), List.of("Skill " + i)));
        }
        return questions;
    }

    private static LedgerEntry entry(List<LedgerEntry> ledger, String author, InterviewerQuestion q,
            EntryOrigin origin, String content, boolean timedOut) {
        boolean interviewer = LedgerEntry.INTERVIEWER.equals(author);
        return new LedgerEntry(UUID.randomUUID(), ledger.size() + 1, author, q.coreQuestionId(), origin, content,
                q.targetSkillIds(), T0, interviewer ? T0.plusSeconds(q.maxTimeSec()) : null, timedOut, false, false);
    }
}
