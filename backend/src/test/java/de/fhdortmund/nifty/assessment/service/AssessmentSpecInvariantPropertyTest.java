package de.fhdortmund.nifty.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.assessment.repository.AssessmentAttemptEntity;
import de.fhdortmund.nifty.assessment.repository.AssessmentAttemptRepository;
import de.fhdortmund.nifty.assessment.repository.BfiResponseEntity;
import de.fhdortmund.nifty.assessment.repository.BfiResponseRepository;
import de.fhdortmund.nifty.assessment.repository.ConversationEntryEntity;
import de.fhdortmund.nifty.assessment.repository.ConversationEntryRepository;
import de.fhdortmund.nifty.assessment.service.AttemptService.Asked;
import de.fhdortmund.nifty.assessment.service.AttemptService.AwaitInterviewer;
import de.fhdortmund.nifty.assessment.service.AttemptService.Finished;
import de.fhdortmund.nifty.assessment.service.AttemptService.ShowState;
import de.fhdortmund.nifty.assessment.service.AttemptService.TurnOutcome;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.AnswerTiming;
import de.fhdortmund.nifty.auth.service.CandidateSessionPrincipal;
import de.fhdortmund.nifty.context.service.ContextCreationService;
import de.fhdortmund.nifty.context.service.dto.SanitizationReport;
import de.fhdortmund.nifty.shared.config.AttemptCompletedEvent;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.InterviewerQuestion;
import de.fhdortmund.nifty.studio.service.view.InterviewerView;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Spec-derived invariant (property) tests for the {@code assessment} slice: REQ-AC-04, 06, 08, 11, 15, 17, 18, 20 and
 * Decisions 2.13, 2.20, 2.24, 2.26, 2.29, 4.3. Plain loops with {@code new Random(SEED)}, no Spring context.
 * Repositories and upstream services are Mockito fakes; the oracles are written from the spec, not from the code.
 * Complements {@link AssessmentInvariantPropertyTest}, which covers the pure helpers.
 */
class AssessmentSpecInvariantPropertyTest {

    private static final long SEED = 20261126L;
    private static final int ROUNDS = 400;
    private static final long GRACE_SECONDS = 5; // Decision 2.29 default
    private static final String COMPLETED = "COMPLETED";
    private static final Instant T0 = Instant.parse("2026-11-26T10:00:00Z");

    /** REQ-AC-15 key from the spec: trait -> {normal item, reversed item}, 1-based. */
    private static final Map<String, int[]> KEY = Map.of(
            "extraversion", new int[] {6, 1},
            "agreeableness", new int[] {2, 7},
            "conscientiousness", new int[] {8, 3},
            "neuroticism", new int[] {9, 4},
            "openness", new int[] {10, 5});
    private static final boolean[] REVERSED = {true, false, true, true, true, false, true, false, false, false};

    // ---------------------------------------------------------------- 1. BFI-10 (REQ-AC-15, Decision 4.3)

    @Test
    @DisplayName("REQ-AC-15: trait means stay in 1.0..5.0, all-3 gives 3.0, reverse items invert exactly once")
    void shouldInvertEachReverseKeyedBfi10ItemExactlyOnceAndKeepMeansInRange() {
        Random rnd = new Random(SEED);
        Bfi10Traits allThree = Bfi10ScoringService.score(new int[] {3, 3, 3, 3, 3, 3, 3, 3, 3, 3});
        for (double v : allThree.asMap().values()) {
            assertThat(v).isEqualTo(3.0);
        }
        for (int round = 0; round < ROUNDS; round++) {
            int[] items = randomItems(rnd);
            Map<String, Double> traits = Bfi10ScoringService.score(items).asMap();
            assertThat(traits).containsOnlyKeys(KEY.keySet());
            for (double v : traits.values()) {
                assertThat(v).isBetween(1.0, 5.0);
            }
            // Raising one answer by 1 moves its trait by exactly +0.5 (normal) or -0.5 (reversed once), nothing else.
            int item = rnd.nextInt(10);
            if (items[item] < 5) {
                int[] raised = items.clone();
                raised[item]++;
                Map<String, Double> after = Bfi10ScoringService.score(raised).asMap();
                String trait = traitOf(item + 1);
                for (String t : KEY.keySet()) {
                    double delta = after.get(t) - traits.get(t);
                    assertThat(delta).as("round %d item %d trait %s", round, item + 1, t)
                            .isEqualTo(t.equals(trait) ? (REVERSED[item] ? -0.5 : 0.5) : 0.0);
                }
            }
        }
    }

    @Test
    @DisplayName("REQ-AC-15: swapping two same-keyed items of different traits only trades their contributions")
    void shouldTradeContributionsWhenSwappingTwoSameKeyedItemsOfDifferentTraits() {
        // The key has no two items of one trait with the same keying (each trait = one normal + one reversed item),
        // so the within-trait swap property of the task does not exist; the cross-trait form is tested instead.
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            int[] items = randomItems(rnd);
            int i = rnd.nextInt(10);
            int j = rnd.nextInt(10);
            if (i == j || REVERSED[i] != REVERSED[j] || traitOf(i + 1).equals(traitOf(j + 1))) {
                continue;
            }
            int[] swapped = items.clone();
            swapped[i] = items[j];
            swapped[j] = items[i];
            Map<String, Double> before = Bfi10ScoringService.score(items).asMap();
            Map<String, Double> after = Bfi10ScoringService.score(swapped).asMap();
            double sign = REVERSED[i] ? -0.5 : 0.5;
            assertThat(after.get(traitOf(i + 1)) - before.get(traitOf(i + 1))).isEqualTo(sign * (items[j] - items[i]));
            assertThat(after.get(traitOf(j + 1)) - before.get(traitOf(j + 1))).isEqualTo(sign * (items[i] - items[j]));
            for (String t : KEY.keySet()) {
                if (!t.equals(traitOf(i + 1)) && !t.equals(traitOf(j + 1))) {
                    assertThat(after.get(t)).isEqualTo(before.get(t));
                }
            }
        }
    }

    @Test
    @DisplayName("REQ-AC-14: wrong count or an answer outside 1..5 is rejected and nothing is stored or published")
    void shouldRejectBfi10AnswersOutsideOneToFiveOrWithWrongCountWithoutSideEffects() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            Harness h = new Harness(rnd, bfiView(rnd, 0), 1);
            h.startAtBfi();
            List<Integer> items = new ArrayList<>();
            int size = rnd.nextInt(3) == 0 ? rnd.nextInt(14) : 10;
            for (int i = 0; i < size; i++) {
                items.add(1 + rnd.nextInt(5));
            }
            boolean invalid = size != 10;
            if (!invalid) {
                int bad = rnd.nextInt(10);
                int[] outside = {0, 6, -3, 99, Integer.MAX_VALUE, Integer.MIN_VALUE};
                items.set(bad, rnd.nextBoolean() ? outside[rnd.nextInt(outside.length)] : null);
            }
            assertThatThrownBy(() -> h.service.submitBfi10(h.attempt.getId(), h.inviteId, items))
                    .as("round %d items=%s", round, items).isInstanceOf(FieldValidationException.class);
            verify(h.bfi, never()).save(any());
            assertThat(h.published).isEmpty();
            assertThat(h.attempt.getStatus()).isEqualTo("BFI10");
        }
    }

    // ---------------------------------------------------------------- 2. Timing (REQ-AC-08, Decision 2.29)

    @Test
    @DisplayName("REQ-AC-08: the grace boundary is exact, to the nanosecond")
    void shouldSplitAtExpiryAndAtExactlyExpiryPlusGrace() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            Instant expires = T0.plusMillis(rnd.nextInt(1_000_000));
            Duration grace = Duration.ofMillis(rnd.nextInt(30_000));
            assertThat(AttemptStateMachine.timing(expires, expires, grace)).isEqualTo(AnswerTiming.ON_TIME);
            assertThat(AttemptStateMachine.timing(expires, expires.minusNanos(1), grace)).isEqualTo(AnswerTiming.ON_TIME);
            Instant graceEnd = expires.plus(grace);
            assertThat(AttemptStateMachine.timing(expires, graceEnd, grace))
                    .isEqualTo(grace.isZero() ? AnswerTiming.ON_TIME : AnswerTiming.TIMED_OUT);
            assertThat(AttemptStateMachine.timing(expires, graceEnd.plusNanos(1), grace))
                    .isEqualTo(AnswerTiming.TOO_LATE);
        }
    }

    @Test
    @DisplayName("REQ-AC-08 / Decision 2.29: the server clock decides; the client sends no time")
    void shouldTakeAnswerTimeFromTheServerClockOnly() {
        for (Method m : AttemptService.class.getDeclaredMethods()) {
            if (m.getName().equals("answer")) {
                assertThat(m.getParameterTypes()).containsExactly(UUID.class, UUID.class, String.class);
            }
        }
        for (Method m : AssessmentSessionService.class.getDeclaredMethods()) {
            if (m.getName().equals("answer")) {
                assertThat(m.getParameterTypes()).containsExactly(UUID.class, String.class);
            }
        }
        assertThat(answerRequestClass().getRecordComponents()).extracting(RecordComponent::getName)
                .containsExactly("content");
    }

    // ---------------------------------------------------------------- 3 + 4 + 5. Full interview through AttemptService

    @Test
    @DisplayName("REQ-AC-04/08/20, Decisions 2.20/2.29: ledger keeps seq gap-free, every answer once, flags per timing")
    void shouldKeepLedgerGapFreeRecordEveryAnswerOnceAndFlagTimedOutAnswersPerSpec() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            boolean bfi = rnd.nextInt(3) == 0;
            InterviewerView view = randomView(rnd, bfi);
            Harness h = new Harness(rnd, view, 1);
            h.interview(round);
            assertLedgerShape(h, view, round);
            if (bfi) {
                assertThat(h.attempt.getStatus()).isEqualTo("BFI10");
                assertThat(h.published).as("BFI-10 step still open: no completion yet").isEmpty();
            } else {
                assertThat(h.attempt.getStatus()).isEqualTo(COMPLETED);
                assertThat(h.published).as("round %d", round)
                        .containsExactly(new AttemptCompletedEvent(h.attempt.getId(), h.configId, h.inviteId));
            }
        }
    }

    @Test
    @DisplayName("REQ-AC-18: BFI-10 finish publishes AttemptCompletedEvent exactly once, never twice, never early")
    void shouldPublishCompletionEventExactlyOnceForBfi10Finish() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            boolean softOnly = rnd.nextBoolean();
            Harness h = softOnly ? new Harness(rnd, bfiView(rnd, 0), 1) : new Harness(rnd, bfiView(rnd, 1 + rnd.nextInt(3)), 1);
            if (softOnly) {
                assertThat(h.startAtBfi()).isInstanceOf(ShowState.class);
            } else {
                h.interview(round);
            }
            assertThat(h.attempt.getStatus()).isEqualTo("BFI10");
            assertThat(h.published).isEmpty();
            int[] raw = randomItems(rnd);
            List<Integer> items = new ArrayList<>();
            for (int v : raw) {
                items.add(v);
            }
            h.service.submitBfi10(h.attempt.getId(), h.inviteId, items);
            assertThat(h.published).containsExactly(new AttemptCompletedEvent(h.attempt.getId(), h.configId, h.inviteId));
            assertThat(h.attempt.getStatus()).isEqualTo(COMPLETED);
            assertThat(h.attempt.getCompletedAt()).isNotNull();
            // stored trait means equal the spec oracle
            org.mockito.ArgumentCaptor<BfiResponseEntity> saved = org.mockito.ArgumentCaptor.forClass(BfiResponseEntity.class);
            verify(h.bfi, times(1)).save(saved.capture());
            assertThat(saved.getValue().getExtraversion().doubleValue()).isEqualTo(oracle(raw, "extraversion"));
            assertThat(saved.getValue().getAgreeableness().doubleValue()).isEqualTo(oracle(raw, "agreeableness"));
            assertThat(saved.getValue().getConscientiousness().doubleValue()).isEqualTo(oracle(raw, "conscientiousness"));
            assertThat(saved.getValue().getNeuroticism().doubleValue()).isEqualTo(oracle(raw, "neuroticism"));
            assertThat(saved.getValue().getOpenness().doubleValue()).isEqualTo(oracle(raw, "openness"));
            // a second submit must not complete again
            assertThatThrownBy(() -> h.service.submitBfi10(h.attempt.getId(), h.inviteId, items))
                    .isInstanceOfSatisfying(DomainException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
            assertThat(h.published).hasSize(1);
            verify(h.bfi, times(1)).save(any());
        }
    }

    @Test
    @DisplayName("REQ-AC-18 / REQ-AC-08: expiry sweep completes an attempt once and leaves open questions alone")
    void shouldPublishCompletionEventExactlyOnceWhenTheExpirySweepClosesTheLastQuestion() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            boolean bfi = rnd.nextInt(4) == 0;
            InterviewerView view = randomView(rnd, bfi);
            Harness h = new Harness(rnd, view, 1);
            h.openFirstQuestion();
            int closed = 0;
            while (h.attempt.getStatus().equals("IN_PROGRESS")) {
                int sizeBefore = h.ledger.size();
                // not yet past expiry + grace: the sweep must not touch the attempt
                h.reopen(-(GRACE_SECONDS * 1000 - 1500) + rnd.nextInt(100_000));
                h.service.closeExpired(h.attempt.getId());
                assertThat(h.ledger).hasSize(sizeBefore);
                assertThat(h.published).isEmpty();
                // past expiry + grace: exactly one question closes, blank and timed out, no follow-up
                h.reopen(-(GRACE_SECONDS * 1000 + 2000) - rnd.nextInt(100_000));
                h.service.closeExpired(h.attempt.getId());
                closed++;
                ConversationEntryEntity closing = h.ledger.get(sizeBefore);
                assertThat(closing.getAuthor()).isEqualTo(LedgerEntry.CANDIDATE);
                assertThat(closing.getContent()).isEmpty();
                assertThat(closing.isTimedOut()).isTrue();
                if (h.attempt.getStatus().equals("IN_PROGRESS")) {
                    assertThat(h.ledger).hasSize(sizeBefore + 2);
                    assertThat(h.ledger.getLast().getOrigin()).isEqualTo("core");
                    assertThat(h.published).isEmpty();
                }
            }
            assertThat(closed).as("sweep never asks follow-ups").isEqualTo(view.questions().size());
            assertThat(h.attempt.getStatus()).isEqualTo(bfi ? "BFI10" : COMPLETED);
            assertThat(h.published).hasSize(bfi ? 0 : 1);
            int size = h.ledger.size();
            h.service.closeExpired(h.attempt.getId()); // sweeping a finished attempt changes nothing
            assertThat(h.ledger).hasSize(size);
            assertThat(h.published).hasSize(bfi ? 0 : 1);
        }
    }

    @Test
    @DisplayName("REQ-AC-11 / REQ-AC-18 / Decision 2.13: a retake gets a new attempt number and id; the limit gives 409")
    void shouldStartRetakeAsNewAttemptAndRejectStartAtTheLimit() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            int max = 1 + rnd.nextInt(5);
            int started = rnd.nextInt(max + 1);
            Harness h = new Harness(rnd, randomView(rnd, false, max), started);
            if (started >= max) {
                assertThatThrownBy(() -> h.service.start(h.principal)).isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
                verify(h.attempts, never()).save(any());
            } else {
                TurnOutcome out = h.service.start(h.principal);
                assertThat(out).isInstanceOf(AwaitInterviewer.class);
                assertThat(h.attempt.getAttemptNo()).isEqualTo(started + 1);
                assertThat(h.attempt.getId()).isNotNull();
                assertThat(h.ledger).as("fresh ledger for the retake").isEmpty();
            }
        }
    }

    @Test
    @DisplayName("REQ-AC-06: resumed state keeps the stored expiresAt, carries the server clock, never over max time")
    void shouldReturnStoredExpiryAndServerClockOnResume() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            Harness h = new Harness(rnd, randomView(rnd, false), 1);
            h.openFirstQuestion();
            long expiresIn = 1000 + rnd.nextInt(170_000);
            h.reopen(expiresIn);
            Instant stored = h.ledger.getLast().getExpiresAt();
            Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);
            SessionStateResponse state = h.service.state(h.attempt.getId(), h.inviteId);
            Instant after = Instant.now();
            assertThat(state.expiresAt()).isEqualTo(stored);
            assertThat(state.serverTime()).isBetween(before, after);
            long remainingMs = Duration.between(state.serverTime(), state.expiresAt()).toMillis();
            assertThat(remainingMs).as("round %d", round).isPositive().isLessThanOrEqualTo(expiresIn);
            assertThat(state.messages()).hasSize(h.ledger.size());
            assertThat(state.attemptId()).isEqualTo(h.attempt.getId());
        }
    }

    // ---------------------------------------------------------------- 3. Answer length (REQ-AC-20, Decision 2.26)

    @Test
    @DisplayName("REQ-AC-20: content over 4000 chars is rejected on field content; up to 4000 is accepted")
    void shouldRejectAnswerContentOver4000CharactersAndAcceptUpToTheLimit() throws Exception {
        Random rnd = new Random(SEED);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Constructor<?> ctor = answerRequestClass().getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        int[] edges = {0, 1, 3999, 4000, 4001, 4002, 8000};
        for (int round = 0; round < ROUNDS; round++) {
            int length = round < edges.length ? edges[round] : rnd.nextInt(9000);
            Object request = ctor.newInstance("a".repeat(length));
            Set<ConstraintViolation<Object>> violations = validator.validate(request);
            if (length > 4000) {
                assertThat(violations).as("length %d", length).hasSize(1);
                assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("content");
            } else {
                assertThat(violations).as("length %d", length).isEmpty();
            }
        }
        assertThat(validator.validate(ctor.newInstance((Object) null))).isNotEmpty();
    }

    // ---------------------------------------------------------------- 4. Transcript (REQ-AC-04 / REQ-AC-17)

    @Test
    @DisplayName("REQ-AC-17 assessment-transcript/v1: entries in seq order with origin and flags, each answer once")
    void shouldReturnTranscriptEntriesInSeqOrderWithOriginAndFlagsForEveryAnswerOnce() {
        Random rnd = new Random(SEED);
        EntryOrigin[] origins = EntryOrigin.values();
        for (int round = 0; round < ROUNDS; round++) {
            AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
            ConversationEntryRepository entries = mock(ConversationEntryRepository.class);
            UUID attemptId = UUID.randomUUID();
            AssessmentAttemptEntity attempt = attemptEntity(UUID.randomUUID(), UUID.randomUUID(), 1 + rnd.nextInt(3),
                    COMPLETED);
            ReflectionTestUtils.setField(attempt, "id", attemptId);
            when(attempts.findById(attemptId)).thenReturn(Optional.of(attempt));
            int pairs = rnd.nextInt(12);
            List<ConversationEntryEntity> rows = new ArrayList<>();
            List<UUID> answerIds = new ArrayList<>();
            List<EntryOrigin> expectedOrigins = new ArrayList<>();
            List<boolean[]> expectedFlags = new ArrayList<>();
            Instant clock = T0;
            for (int p = 0; p < pairs; p++) {
                EntryOrigin origin = origins[rnd.nextInt(origins.length)];
                UUID core = UUID.randomUUID();
                List<UUID> skills = List.of(UUID.randomUUID(), UUID.randomUUID());
                clock = clock.plusSeconds(1 + rnd.nextInt(30));
                rows.add(ledgerRow(attemptId, rows.size() + 1, "interviewer", core, origin, "Q" + p, skills, clock,
                        clock.plusSeconds(180), false, false, false));
                Instant answeredAt = clock.plusSeconds(rnd.nextInt(200));
                boolean[] flags = {rnd.nextBoolean(), rnd.nextBoolean(), rnd.nextBoolean()};
                ConversationEntryEntity answer = ledgerRow(attemptId, rows.size() + 1, "candidate", core, origin, "A" + p,
                        skills, answeredAt, null, flags[0], flags[1], flags[2]);
                rows.add(answer);
                answerIds.add(answer.getId());
                expectedOrigins.add(origin);
                expectedFlags.add(flags);
                clock = answeredAt;
            }
            if (rnd.nextBoolean()) { // an open question at the end is not an entry yet
                rows.add(ledgerRow(attemptId, rows.size() + 1, "interviewer", UUID.randomUUID(), EntryOrigin.CORE, "open",
                        List.of(), clock, clock.plusSeconds(180), false, false, false));
            }
            when(entries.findByAttemptIdOrderBySeqAsc(attemptId)).thenReturn(rows);
            AssessmentQueryServiceImpl query = new AssessmentQueryServiceImpl(attempts, entries,
                    mock(BfiResponseRepository.class));

            ImmutableTranscriptView view = query.getTranscriptForAttempt(attemptId);

            assertThat(view.entries()).as("round %d", round).hasSize(pairs);
            assertThat(view.entries()).extracting(TranscriptEntryView::entryId).containsExactlyElementsOf(answerIds);
            assertThat(new HashSet<>(answerIds)).hasSize(pairs);
            int lastSeq = 0;
            for (int p = 0; p < pairs; p++) {
                TranscriptEntryView e = view.entries().get(p);
                assertThat(e.seq()).isGreaterThan(lastSeq);
                lastSeq = e.seq();
                assertThat(e.origin()).isEqualTo(expectedOrigins.get(p));
                assertThat(e.timedOut()).isEqualTo(expectedFlags.get(p)[0]);
                assertThat(e.pasteSuspected()).isEqualTo(expectedFlags.get(p)[1]);
                assertThat(e.injectionSuspected()).isEqualTo(expectedFlags.get(p)[2]);
                assertThat(e.questionText()).isEqualTo("Q" + p);
                assertThat(e.answerText()).isEqualTo("A" + p);
                assertThat(e.targetSkillIds()).hasSize(2);
                assertThat(e.durationSec()).isEqualTo(Duration.between(e.askedAt(), e.submittedAt()).toSeconds());
            }
        }
    }

    // ---------------------------------------------------------------- 5 + 6. Retake and soft-skill profiles

    @Test
    @DisplayName("Decision 2.13 / REQ-AC-17: one soft-skill profile per invite from the latest completed attempt, never re-inverted")
    void shouldReturnOneProfilePerCandidateFromTheLatestCompletedAttemptWithoutInvertingAgain() {
        Random rnd = new Random(SEED);
        for (int round = 0; round < ROUNDS; round++) {
            UUID configId = UUID.randomUUID();
            AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
            BfiResponseRepository bfi = mock(BfiResponseRepository.class);
            List<AssessmentAttemptEntity> completed = new ArrayList<>();
            Map<UUID, AssessmentAttemptEntity> latestByInvite = new java.util.HashMap<>();
            Map<UUID, Map<String, Double>> stored = new java.util.HashMap<>();
            List<AssessmentAttemptEntity> all = new ArrayList<>();
            int invites = 1 + rnd.nextInt(8);
            for (int i = 0; i < invites; i++) {
                UUID invite = UUID.randomUUID();
                int count = 1 + rnd.nextInt(4);
                for (int no = 1; no <= count; no++) {
                    String status = rnd.nextInt(4) == 0 ? (rnd.nextBoolean() ? "IN_PROGRESS" : "BFI10") : COMPLETED;
                    AssessmentAttemptEntity a = attemptEntity(invite, configId, no, status);
                    ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
                    all.add(a);
                    when(attempts.findById(a.getId())).thenReturn(Optional.of(a));
                    if (rnd.nextInt(5) > 0) { // a missing row models "BFI-10 off"
                        Map<String, Double> means = new java.util.LinkedHashMap<>();
                        BigDecimal[] v = new BigDecimal[5];
                        for (int t = 0; t < 5; t++) {
                            v[t] = BigDecimal.valueOf(2 + rnd.nextInt(9)).divide(BigDecimal.valueOf(2));
                        }
                        stored.put(a.getId(), Map.of("conscientiousness", v[0].doubleValue(), "agreeableness",
                                v[1].doubleValue(), "neuroticism", v[2].doubleValue(), "extraversion", v[3].doubleValue(),
                                "openness", v[4].doubleValue()));
                        when(bfi.findById(a.getId())).thenReturn(Optional.of(new BfiResponseEntity(a.getId(),
                                randomItems(rnd), v[0], v[1], v[2], v[3], v[4], T0)));
                    }
                    if (COMPLETED.equals(status)) {
                        completed.add(a);
                        AssessmentAttemptEntity best = latestByInvite.get(invite);
                        if (best == null || best.getAttemptNo() < no) {
                            latestByInvite.put(invite, a);
                        }
                    }
                }
            }
            completed.sort(Comparator.comparing((AssessmentAttemptEntity a) -> a.getInviteId().toString())
                    .thenComparing(Comparator.comparingInt(AssessmentAttemptEntity::getAttemptNo).reversed()));
            when(attempts.findByConfigIdAndStatusOrderByInviteIdAscAttemptNoDesc(configId, COMPLETED)).thenReturn(completed);
            for (UUID invite : latestByInvite.keySet()) {
                when(attempts.findFirstByConfigIdAndInviteIdAndStatusOrderByAttemptNoDesc(configId, invite, COMPLETED))
                        .thenReturn(Optional.of(latestByInvite.get(invite)));
            }
            AssessmentQueryServiceImpl query = new AssessmentQueryServiceImpl(attempts,
                    mock(ConversationEntryRepository.class), bfi);

            List<StudentSoftSkillProfile> profiles = query.getSoftSkillProfilesForConfig(configId);

            assertThat(profiles).as("round %d: one per candidate", round).hasSize(latestByInvite.size());
            Set<UUID> seen = new HashSet<>();
            for (StudentSoftSkillProfile p : profiles) {
                assertThat(seen.add(p.inviteId())).as("no duplicate invite").isTrue();
                AssessmentAttemptEntity latest = latestByInvite.get(p.inviteId());
                assertThat(p.attemptId()).isEqualTo(latest.getId());
                Map<String, Double> expected = stored.get(latest.getId());
                if (expected == null) {
                    assertThat(p.softSkillScores()).isNull();
                } else {
                    assertThat(p.softSkillScores()).isEqualTo(expected); // stored value passes through unchanged
                    for (double v : p.softSkillScores().values()) {
                        assertThat(v).isBetween(1.0, 5.0);
                    }
                }
                assertThat(query.getLatestCompletedAttemptId(configId, p.inviteId())).contains(latest.getId());
            }
            for (AssessmentAttemptEntity a : all) {
                assertThat(query.getSoftSkillProfileForAttempt(a.getId()).isPresent())
                        .as("profile for a single attempt needs COMPLETED").isEqualTo(COMPLETED.equals(a.getStatus()));
            }
        }
    }

    // ================================================================= helpers

    private static Class<?> answerRequestClass() {
        try {
            return Class.forName("de.fhdortmund.nifty.assessment.web.AnswerRequest");
        } catch (ClassNotFoundException ex) {
            throw new AssertionError(ex);
        }
    }

    private static int[] randomItems(Random rnd) {
        int[] items = new int[10];
        for (int i = 0; i < 10; i++) {
            items[i] = 1 + rnd.nextInt(5);
        }
        return items;
    }

    private static String traitOf(int item) {
        for (Map.Entry<String, int[]> e : KEY.entrySet()) {
            if (e.getValue()[0] == item || e.getValue()[1] == item) {
                return e.getKey();
            }
        }
        throw new AssertionError("item " + item);
    }

    private static double oracle(int[] items, String trait) {
        int[] key = KEY.get(trait);
        return (items[key[0] - 1] + (6 - items[key[1] - 1])) / 2.0;
    }

    private static InterviewerView randomView(Random rnd, boolean bfi) {
        return randomView(rnd, bfi, 3);
    }

    private static InterviewerView randomView(Random rnd, boolean bfi, int maxAttempts) {
        return bfiView(rnd, 1 + rnd.nextInt(4), bfi, maxAttempts, GroupingMode.HARD_SKILLS);
    }

    /** bfi on; {@code count} 0 gives a SOFT_SKILLS-only configuration. */
    private static InterviewerView bfiView(Random rnd, int count) {
        return bfiView(rnd, count, true, 3, count == 0 ? GroupingMode.SOFT_SKILLS : GroupingMode.HARD_SKILLS);
    }

    private static InterviewerView bfiView(Random rnd, int count, boolean bfi, int maxAttempts, GroupingMode mode) {
        List<InterviewerQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            questions.add(new InterviewerQuestion(UUID.randomUUID(), i, "Question " + i, 30, 180, rnd.nextInt(4),
                    List.of(UUID.randomUUID()), List.of("Skill " + i)));
        }
        return new InterviewerView(UUID.randomUUID(), "v1", "Vision", maxAttempts, bfi, mode, questions);
    }

    private static AssessmentAttemptEntity attemptEntity(UUID invite, UUID config, int attemptNo, String status) {
        AssessmentAttemptEntity a = new AssessmentAttemptEntity(invite, config, 1, attemptNo, status, T0);
        ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
        return a;
    }

    private static ConversationEntryEntity ledgerRow(UUID attemptId, int seq, String author, UUID core, EntryOrigin origin,
            String content, List<UUID> skills, Instant createdAt, Instant expiresAt, boolean timedOut, boolean paste,
            boolean injection) {
        ConversationEntryEntity e = new ConversationEntryEntity(attemptId, seq, author, core, origin.literal(), content,
                skills, createdAt, expiresAt, timedOut, paste, injection);
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        return e;
    }

    /** Ledger-level invariants of a finished interview (REQ-AC-04, Decisions 2.20, 2.29). */
    private static void assertLedgerShape(Harness h, InterviewerView view, int round) {
        List<ConversationEntryEntity> ledger = h.ledger;
        assertThat(ledger).as("round %d", round).isNotEmpty();
        for (int i = 0; i < ledger.size(); i++) {
            ConversationEntryEntity e = ledger.get(i);
            assertThat(e.getSeq()).as("round %d: seq strictly +1, no gaps", round).isEqualTo(i + 1);
            assertThat(e.getAttemptId()).isEqualTo(h.attempt.getId());
            assertThat(e.getAuthor()).isEqualTo(i % 2 == 0 ? "interviewer" : "candidate");
            if (i % 2 == 1) {
                ConversationEntryEntity q = ledger.get(i - 1);
                assertThat(e.getOrigin()).isEqualTo(q.getOrigin());
                assertThat(e.getCoreQuestionId()).isEqualTo(q.getCoreQuestionId());
            }
        }
        assertThat(ledger.size() % 2).as("no question left open at the end").isZero();
        Set<String> candidateTexts = new HashSet<>();
        int candidateRows = 0;
        for (int i = 1; i < ledger.size(); i += 2) {
            candidateRows++;
            if (!ledger.get(i).getContent().isBlank()) {
                assertThat(candidateTexts.add(ledger.get(i).getContent())).as("answer stored once").isTrue();
            }
        }
        assertThat(candidateRows).isEqualTo(h.answersGiven);
        assertThat(candidateTexts).containsExactlyInAnyOrderElementsOf(h.expectedStoredTexts);
        for (String lateText : h.lateTexts) {
            assertThat(candidateTexts).as("too-late text is never stored").doesNotContain(lateText);
        }
        List<UUID> coreOrder = new ArrayList<>();
        for (int i = 0; i < ledger.size(); i += 2) {
            if (ledger.get(i).getOrigin().equals("core")) {
                coreOrder.add(ledger.get(i).getCoreQuestionId());
            }
        }
        List<UUID> configured = new ArrayList<>();
        for (InterviewerQuestion q : view.questions()) {
            configured.add(q.coreQuestionId());
        }
        assertThat(coreOrder).containsExactlyElementsOf(configured);
    }

    /** In-memory fake of the persistence plus mocked upstream slices; real {@link AttemptService} on top. */
    private static final class Harness {
        final Random rnd;
        final UUID inviteId = UUID.randomUUID();
        final UUID configId;
        final InterviewerView view;
        final CandidateSessionPrincipal principal;
        final AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        final ConversationEntryRepository entries = mock(ConversationEntryRepository.class);
        final BfiResponseRepository bfi = mock(BfiResponseRepository.class);
        final ContextCreationService context = mock(ContextCreationService.class);
        final List<ConversationEntryEntity> ledger = new ArrayList<>();
        final List<Object> published = new ArrayList<>();
        final List<String> expectedStoredTexts = new ArrayList<>();
        final List<String> lateTexts = new ArrayList<>();
        final AttemptService service;
        AssessmentAttemptEntity attempt;
        boolean nextInjection;
        int answersGiven;
        private int tokens;

        Harness(Random rnd, InterviewerView view, int startedAttempts) {
            this.rnd = rnd;
            this.view = view;
            this.configId = view.configId();
            this.principal = new CandidateSessionPrincipal(inviteId, configId, "cand", "hash");
            StudioQueryService studio = mock(StudioQueryService.class);
            when(studio.interviewerView(any())).thenReturn(view);
            ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
            org.mockito.Mockito.doAnswer(inv -> published.add(inv.getArgument(0))).when(events).publishEvent(any(Object.class));
            when(attempts.countByInviteId(inviteId)).thenReturn((long) startedAttempts);
            when(attempts.save(any())).thenAnswer(inv -> {
                attempt = inv.getArgument(0);
                ReflectionTestUtils.setField(attempt, "id", UUID.randomUUID());
                return attempt;
            });
            when(attempts.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(attempt));
            when(attempts.findById(any())).thenAnswer(inv -> Optional.ofNullable(attempt));
            when(entries.save(any())).thenAnswer(inv -> {
                ConversationEntryEntity e = inv.getArgument(0);
                ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                ledger.add(e);
                return e;
            });
            when(entries.findByAttemptIdOrderBySeqAsc(any())).thenAnswer(inv -> new ArrayList<>(ledger));
            when(context.inspectCandidateInput(any(), any())).thenAnswer(
                    inv -> new SanitizationReport(nextInjection, List.of(), false));
            this.service = new AttemptService(attempts, entries, bfi, studio, context, events, GRACE_SECONDS, 240, 1800);
        }

        TurnOutcome startAtBfi() {
            return service.start(principal);
        }

        /** Starts and stores the first interviewer question with its configured wording. */
        void openFirstQuestion() {
            TurnOutcome out = service.start(principal);
            AwaitInterviewer await = (AwaitInterviewer) out;
            service.recordInterviewerReply(attempt.getId(), await.step(), "Question 1");
        }

        /** Moves the open question's deadline to now + offset, keeping its max time; server rows are immutable. */
        void reopen(long expiresInMillis) {
            int last = ledger.size() - 1;
            ConversationEntryEntity q = ledger.get(last);
            long maxTime = Duration.between(q.getCreatedAt(), q.getExpiresAt()).toSeconds();
            Instant expires = Instant.now().plusMillis(expiresInMillis).truncatedTo(ChronoUnit.MICROS);
            ConversationEntryEntity moved = new ConversationEntryEntity(q.getAttemptId(), q.getSeq(), q.getAuthor(),
                    q.getCoreQuestionId(), q.getOrigin(), q.getContent(), q.getTargetSkills(),
                    expires.minusSeconds(maxTime), expires, false, false, false);
            ReflectionTestUtils.setField(moved, "id", q.getId());
            ledger.set(last, moved);
        }

        /** Plays a whole interview with random timing and content; asserts each answer row against the spec. */
        void interview(int round) {
            TurnOutcome out = service.start(principal);
            while (true) {
                if (out instanceof AwaitInterviewer await) {
                    boolean blank = rnd.nextInt(6) == 0;
                    String text = blank ? "" : "Interviewer says " + tokens++;
                    int before = ledger.size();
                    out = service.recordInterviewerReply(attempt.getId(), await.step(), text);
                    if (out instanceof Asked) {
                        ConversationEntryEntity q = ledger.get(before);
                        assertThat(q.getAuthor()).isEqualTo(LedgerEntry.INTERVIEWER);
                        if (blank) { // Decision 2.29: fallback is the configured core question
                            assertThat(q.getOrigin()).isEqualTo("core");
                            assertThat(q.getContent()).isEqualTo(
                                    AttemptStateMachine.questionOf(view, LedgerEntry.of(q)).text());
                        } else {
                            assertThat(q.getContent()).isEqualTo(text);
                        }
                    }
                    continue;
                }
                if (!attempt.getStatus().equals("IN_PROGRESS") || out instanceof Finished) {
                    return;
                }
                answerOnce(round);
                out = lastOutcome;
            }
        }

        private TurnOutcome lastOutcome;

        private void answerOnce(int round) {
            assertThat(published).as("no completion while a question is open").isEmpty();
            int mode = rnd.nextInt(8); // 0-3 on time text, 4 on time blank, 5 timed-out text, 6 timed-out blank, 7 late
            long expiresIn = switch (mode) {
                case 5, 6 -> -500 - rnd.nextInt(3000); // inside the 5 s grace, 1.5 s from its end
                case 7 -> -(GRACE_SECONDS * 1000 + 2000) - rnd.nextInt(60_000);
                default -> 5000 + rnd.nextInt(115_000); // elapsed >= 60 s, so never "too fast"
            };
            reopen(expiresIn);
            ConversationEntryEntity open = ledger.getLast();
            boolean blank = mode == 4 || mode == 6;
            String text = blank ? (rnd.nextBoolean() ? "" : "   ") : "answer-" + round + "-" + tokens++
                    + "x".repeat(rnd.nextInt(300));
            nextInjection = rnd.nextBoolean();
            int row = ledger.size();
            Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);
            lastOutcome = null;
            answersGiven++;
            if (mode == 7) {
                lateTexts.add(text);
                assertThatThrownBy(() -> service.answer(attempt.getId(), inviteId, text))
                        .isInstanceOfSatisfying(DomainException.class,
                                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
                ConversationEntryEntity closed = ledger.get(row);
                assertThat(closed.getContent()).isEmpty();
                assertThat(closed.isTimedOut()).isTrue();
                assertThat(closed.isPasteSuspected()).isFalse();
                assertThat(closed.isInjectionSuspected()).isFalse();
            } else {
                lastOutcome = service.answer(attempt.getId(), inviteId, text);
                ConversationEntryEntity stored = ledger.get(row);
                Instant after = Instant.now();
                assertThat(stored.getCreatedAt()).as("server clock stamps the answer").isBetween(before, after);
                assertThat(stored.getContent()).isEqualTo(text); // text kept unchanged, also blank text
                assertThat(stored.isTimedOut()).isEqualTo(mode == 5 || mode == 6);
                assertThat(stored.isPasteSuspected()).isFalse();
                assertThat(stored.isInjectionSuspected()).isEqualTo(!blank && nextInjection);
                if (!blank) {
                    expectedStoredTexts.add(text);
                }
            }
            boolean followUpsAllowed = mode <= 3;
            if (!followUpsAllowed && ledger.size() > row + 1) {
                ConversationEntryEntity next = ledger.get(row + 1);
                assertThat(next.getOrigin()).as("Decision 2.29: blank, timed-out or late answer skips follow-ups")
                        .isEqualTo("core");
                assertThat(next.getCoreQuestionId()).isNotEqualTo(open.getCoreQuestionId());
            }
            if (lastOutcome == null) { // late: state moved on without an interviewer call
                lastOutcome = attempt.getStatus().equals("IN_PROGRESS") ? new Asked(attempt.getId(),
                        LedgerEntry.of(ledger.getLast()), 0) : new Finished(AttemptStatus.valueOf(attempt.getStatus()));
            }
        }
    }
}
