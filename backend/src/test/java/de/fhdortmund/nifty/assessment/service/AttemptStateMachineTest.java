package de.fhdortmund.nifty.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.AnswerTiming;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.AskCore;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.AskFollowUp;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.Finish;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.NextStep;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.InterviewerQuestion;
import de.fhdortmund.nifty.studio.service.view.InterviewerView;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** REQ-AC-01, 02, 06, 08, 11 / Decisions 2.13, 2.14, 2.20, 2.23: pure attempt rules, no Spring. */
class AttemptStateMachineTest {

    private static final Instant T0 = Instant.parse("2026-11-26T10:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(5);

    private final InterviewerQuestion q1 = question(1, 0);
    private final InterviewerQuestion q2 = question(2, 1);
    private final InterviewerQuestion q3 = question(3, 0);
    private final InterviewerView view = view(GroupingMode.HARD_SKILLS, false, List.of(q1, q2, q3));

    @Test
    void emptyLedgerAsksTheFirstCoreQuestion() {
        assertThat(AttemptStateMachine.next(view, List.of(), true)).isEqualTo(new AskCore(q1));
    }

    @Test
    void answeredCoreQuestionWithoutFollowUpLimitMovesToTheNextCoreQuestionInOrder() {
        List<LedgerEntry> ledger = new ArrayList<>();
        ask(ledger, q1, EntryOrigin.CORE);
        answer(ledger, q1, EntryOrigin.CORE, "My answer");

        assertThat(AttemptStateMachine.next(view, ledger, true)).isEqualTo(new AskCore(q2));
    }

    @Test
    void followUpLimitOneAllowsExactlyOneFollowUpThenTheNextCoreQuestion() {
        List<LedgerEntry> ledger = new ArrayList<>();
        ask(ledger, q2, EntryOrigin.CORE);
        answer(ledger, q2, EntryOrigin.CORE, "First answer");
        assertThat(AttemptStateMachine.next(view, ledger, true)).isEqualTo(new AskFollowUp(q2, 0));

        ask(ledger, q2, EntryOrigin.FOLLOW_UP);
        answer(ledger, q2, EntryOrigin.FOLLOW_UP, "More detail");
        assertThat(AttemptStateMachine.next(view, ledger, true)).isEqualTo(new AskCore(q3));
    }

    @Test
    void followUpIsSkippedWhenNotAllowedForBlankOrTimedOutAnswers() {
        List<LedgerEntry> ledger = new ArrayList<>();
        ask(ledger, q2, EntryOrigin.CORE);
        answer(ledger, q2, EntryOrigin.CORE, "");

        assertThat(AttemptStateMachine.next(view, ledger, false)).isEqualTo(new AskCore(q3));
    }

    @Test
    void lastCoreQuestionFinishesTheInterview() {
        List<LedgerEntry> ledger = new ArrayList<>();
        ask(ledger, q3, EntryOrigin.CORE);
        answer(ledger, q3, EntryOrigin.CORE, "Done");

        assertThat(AttemptStateMachine.next(view, ledger, true)).isInstanceOf(Finish.class);
    }

    @Test
    void inventedQuestionsDisabledGivesOnlyCoreAndFollowUpOriginsAndCoversEveryCoreQuestion() {
        List<LedgerEntry> ledger = new ArrayList<>();
        List<UUID> askedCore = new ArrayList<>();
        NextStep step = AttemptStateMachine.next(view, ledger, true);
        while (!(step instanceof Finish)) {
            EntryOrigin origin = step instanceof AskFollowUp ? EntryOrigin.FOLLOW_UP : EntryOrigin.CORE;
            InterviewerQuestion q = step instanceof AskCore core ? core.question() : ((AskFollowUp) step).question();
            if (origin == EntryOrigin.CORE) {
                askedCore.add(q.coreQuestionId());
            }
            ask(ledger, q, origin);
            answer(ledger, q, origin, "answer");
            step = AttemptStateMachine.next(view, ledger, true);
        }

        assertThat(askedCore).containsExactly(q1.coreQuestionId(), q2.coreQuestionId(), q3.coreQuestionId());
        assertThat(ledger).extracting(LedgerEntry::origin).doesNotContain(EntryOrigin.INVENTED);
    }

    @Test
    void softSkillsOnlyWithBfi10StartsAtTheBfi10Step() {
        assertThat(AttemptStateMachine.initialStatus(view(GroupingMode.SOFT_SKILLS, true, List.of())))
                .isEqualTo(AttemptStatus.BFI10);
        assertThat(AttemptStateMachine.initialStatus(view)).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(AttemptStateMachine.initialStatus(view(GroupingMode.COMBINED, true, List.of(q1))))
                .isEqualTo(AttemptStatus.IN_PROGRESS);
    }

    @Test
    void interviewEndGoesToBfi10OnlyWhenItIsOn() {
        assertThat(AttemptStateMachine.statusAfterInterview(view)).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(AttemptStateMachine.statusAfterInterview(view(GroupingMode.COMBINED, true, List.of(q1))))
                .isEqualTo(AttemptStatus.BFI10);
    }

    @Test
    void attemptLimitRejectsTheThirdAttemptOfTwoWith409() {
        AttemptStateMachine.checkCanStart(1, 2);
        assertThatThrownBy(() -> AttemptStateMachine.checkCanStart(2, 2))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void answerTimingKeepsPartialAnswersInsideTheGraceWindowAndRejectsLaterOnes() {
        Instant expiresAt = T0.plusSeconds(180);
        assertThat(AttemptStateMachine.timing(expiresAt, expiresAt, GRACE)).isEqualTo(AnswerTiming.ON_TIME);
        assertThat(AttemptStateMachine.timing(expiresAt, expiresAt.plusMillis(400), GRACE))
                .isEqualTo(AnswerTiming.TIMED_OUT);
        assertThat(AttemptStateMachine.timing(expiresAt, expiresAt.plus(GRACE), GRACE))
                .isEqualTo(AnswerTiming.TIMED_OUT);
        assertThat(AttemptStateMachine.timing(expiresAt, expiresAt.plusSeconds(120), GRACE))
                .isEqualTo(AnswerTiming.TOO_LATE);
    }

    @Test
    void openQuestionIsTheLastInterviewerEntryAndQuestionsRemainingCountsLaterCoreQuestions() {
        List<LedgerEntry> ledger = new ArrayList<>();
        assertThat(AttemptStateMachine.openQuestion(ledger)).isEmpty();
        assertThat(AttemptStateMachine.questionsRemaining(view, ledger)).isEqualTo(3);

        ask(ledger, q1, EntryOrigin.CORE);
        assertThat(AttemptStateMachine.openQuestion(ledger)).contains(ledger.getLast());
        assertThat(AttemptStateMachine.questionsRemaining(view, ledger)).isEqualTo(2);

        answer(ledger, q1, EntryOrigin.CORE, "a");
        assertThat(AttemptStateMachine.openQuestion(ledger)).isEmpty();
    }

    private static InterviewerQuestion question(int position, int followUpLimit) {
        return new InterviewerQuestion(UUID.randomUUID(), position, "Question " + position, 30, 180, followUpLimit,
                List.of(UUID.randomUUID()), List.of("Skill " + position));
    }

    private static InterviewerView view(GroupingMode mode, boolean bfi10, List<InterviewerQuestion> questions) {
        return new InterviewerView(UUID.randomUUID(), "v1", "Vision", 2, false, bfi10, mode, questions);
    }

    private static void ask(List<LedgerEntry> ledger, InterviewerQuestion q, EntryOrigin origin) {
        ledger.add(new LedgerEntry(UUID.randomUUID(), ledger.size() + 1, LedgerEntry.INTERVIEWER, q.coreQuestionId(),
                origin, q.text(), q.targetSkillIds(), T0, T0.plusSeconds(q.maxTimeSec()), false, false, false));
    }

    private static void answer(List<LedgerEntry> ledger, InterviewerQuestion q, EntryOrigin origin, String text) {
        ledger.add(new LedgerEntry(UUID.randomUUID(), ledger.size() + 1, LedgerEntry.CANDIDATE, q.coreQuestionId(),
                origin, text, q.targetSkillIds(), T0.plusSeconds(60), null, false, false, false));
    }
}
