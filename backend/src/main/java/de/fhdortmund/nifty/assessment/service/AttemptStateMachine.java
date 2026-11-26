package de.fhdortmund.nifty.assessment.service;

import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.InterviewerQuestion;
import de.fhdortmund.nifty.studio.service.view.InterviewerView;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/**
 * Pure attempt rules (Decisions 1.11, 2.13, 2.14, 2.20, 2.23). Java decides question order, follow-up counts and
 * deadlines; the interviewer LLM only words the questions. Plain loops (coding standard #3).
 */
final class AttemptStateMachine {

    /** The next interviewer turn after the ledger so far. */
    sealed interface NextStep permits Ask, Finish {
    }

    /** A turn that asks about one core question: the question itself or a follow-up on it. */
    sealed interface Ask extends NextStep permits AskCore, AskFollowUp {
        InterviewerQuestion question();
    }

    record AskCore(InterviewerQuestion question) implements Ask {
    }

    record AskFollowUp(InterviewerQuestion question, int followUpsUsed) implements Ask {
    }

    record Finish() implements NextStep {
    }

    enum AnswerTiming { ON_TIME, TIMED_OUT, TOO_LATE }

    private AttemptStateMachine() {
    }

    /** REQ-AC-01 / Decision 2.23: a SOFT_SKILLS-only configuration skips the interview. */
    static AttemptStatus initialStatus(InterviewerView view) {
        boolean softSkillsOnly = view.groupingMode() == GroupingMode.SOFT_SKILLS && view.questions().isEmpty();
        return softSkillsOnly && view.bfi10Enabled() ? AttemptStatus.BFI10 : AttemptStatus.IN_PROGRESS;
    }

    static AttemptStatus statusAfterInterview(InterviewerView view) {
        return view.bfi10Enabled() ? AttemptStatus.BFI10 : AttemptStatus.COMPLETED;
    }

    /** REQ-AC-11 / Decision 2.13. */
    static void checkCanStart(int startedAttempts, int maxAttempts) {
        if (startedAttempts >= maxAttempts) {
            throw new DomainException(HttpStatus.CONFLICT, "You have used all attempts for this assessment.");
        }
    }

    /**
     * REQ-AC-01 / REQ-AC-02: core questions in position order, then up to {@code followUpLimit} follow-ups each.
     * {@code followUpAllowed} is false after a blank or timed-out answer, so the interview moves on.
     * shortcut: always asks every allowed follow-up and never an invented question; let the interviewer end a topic
     * early or invent questions when the team wants that.
     */
    static NextStep next(InterviewerView view, List<LedgerEntry> ledger, boolean followUpAllowed) {
        List<InterviewerQuestion> questions = view.questions();
        LedgerEntry lastAsked = lastInterviewerEntry(ledger);
        if (lastAsked == null) {
            return questions.isEmpty() ? new Finish() : new AskCore(questions.getFirst());
        }
        int index = indexOf(questions, lastAsked);
        InterviewerQuestion current = questions.get(index);
        int used = followUpsUsed(ledger, current);
        if (followUpAllowed && used < current.followUpLimit()) {
            return new AskFollowUp(current, used);
        }
        return index + 1 < questions.size() ? new AskCore(questions.get(index + 1)) : new Finish();
    }

    /** REQ-AC-08 / Decision 2.20: inside the grace window the answer is kept with timedOut = true. */
    static AnswerTiming timing(Instant expiresAt, Instant submittedAt, Duration grace) {
        if (!submittedAt.isAfter(expiresAt)) {
            return AnswerTiming.ON_TIME;
        }
        return submittedAt.isAfter(expiresAt.plus(grace)) ? AnswerTiming.TOO_LATE : AnswerTiming.TIMED_OUT;
    }

    /** The question waiting for an answer: the last ledger row, when the interviewer wrote it. */
    static Optional<LedgerEntry> openQuestion(List<LedgerEntry> ledger) {
        return !ledger.isEmpty() && ledger.getLast().isInterviewer() ? Optional.of(ledger.getLast()) : Optional.empty();
    }

    /** Core questions after the current one (same meaning as InterviewerContextInput.coreQuestionsRemaining). */
    static int questionsRemaining(InterviewerView view, List<LedgerEntry> ledger) {
        LedgerEntry lastAsked = lastInterviewerEntry(ledger);
        int total = view.questions().size();
        return lastAsked == null ? total : total - indexOf(view.questions(), lastAsked) - 1;
    }

    private static LedgerEntry lastInterviewerEntry(List<LedgerEntry> ledger) {
        for (int i = ledger.size() - 1; i >= 0; i--) {
            if (ledger.get(i).isInterviewer()) {
                return ledger.get(i);
            }
        }
        return null;
    }

    /** The configured core question that a ledger row belongs to. */
    static InterviewerQuestion questionOf(InterviewerView view, LedgerEntry entry) {
        return view.questions().get(indexOf(view.questions(), entry));
    }

    private static int indexOf(List<InterviewerQuestion> questions, LedgerEntry entry) {
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).coreQuestionId().equals(entry.coreQuestionId())) {
                return i;
            }
        }
        throw new IllegalStateException("Ledger entry " + entry.id() + " names no core question of the config");
    }

    // PIT: negating isInterviewer() survives as an equivalent mutant. next() runs only after an answer, so each
    // interviewer follow-up row has exactly one candidate follow-up row, and both counts are equal.
    private static int followUpsUsed(List<LedgerEntry> ledger, InterviewerQuestion question) {
        int used = 0;
        for (int i = 0; i < ledger.size(); i++) {
            LedgerEntry entry = ledger.get(i);
            if (entry.isInterviewer() && entry.origin() == EntryOrigin.FOLLOW_UP
                    && question.coreQuestionId().equals(entry.coreQuestionId())) {
                used++;
            }
        }
        return used;
    }
}
