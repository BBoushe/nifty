package de.fhdortmund.nifty.assessment.service;

import de.fhdortmund.nifty.assessment.repository.AssessmentAttemptEntity;
import de.fhdortmund.nifty.assessment.repository.AssessmentAttemptRepository;
import de.fhdortmund.nifty.assessment.repository.BfiResponseEntity;
import de.fhdortmund.nifty.assessment.repository.BfiResponseRepository;
import de.fhdortmund.nifty.assessment.repository.ConversationEntryEntity;
import de.fhdortmund.nifty.assessment.repository.ConversationEntryRepository;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.AnswerTiming;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.AskCore;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.AskFollowUp;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.Finish;
import de.fhdortmund.nifty.assessment.service.AttemptStateMachine.NextStep;
import de.fhdortmund.nifty.auth.service.CandidateSessionPrincipal;
import de.fhdortmund.nifty.context.service.ContextCreationService;
import de.fhdortmund.nifty.context.service.dto.CoreQuestionPromptView;
import de.fhdortmund.nifty.context.service.dto.DialogueTurnView;
import de.fhdortmund.nifty.context.service.dto.InterviewerContextInput;
import de.fhdortmund.nifty.shared.config.AttemptCompletedEvent;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.InterviewerQuestion;
import de.fhdortmund.nifty.studio.service.view.InterviewerView;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every database write of an attempt, one short transaction each, under a row lock on the attempt. Never calls the
 * interviewer LLM, so no transaction waits on the model (Decision 1.11).
 */
@Service
@Transactional(readOnly = true)
class AttemptService {

    /** Result of a write that may need the interviewer next. */
    sealed interface TurnOutcome {
    }

    /** JSON state for the browser: a SOFT_SKILLS-only start or a resumed attempt. */
    record ShowState(SessionStateResponse state) implements TurnOutcome {
    }

    /** The interviewer LLM must word {@code step}; nothing is stored for it yet. */
    record AwaitInterviewer(UUID attemptId, NextStep step, InterviewerContextInput input) implements TurnOutcome {
    }

    /** A question is stored and open. */
    record Asked(LedgerEntry question, int questionsRemaining) implements TurnOutcome {
    }

    record Finished(AttemptStatus status) implements TurnOutcome {
    }

    private static final String NOT_FOUND = "Assessment session not found.";
    private static final String FOREIGN = "This session belongs to another candidate.";
    private static final String COMPLETED = AttemptStatus.COMPLETED.name();

    private final AssessmentAttemptRepository attempts;
    private final ConversationEntryRepository entries;
    private final BfiResponseRepository bfiResponses;
    private final StudioQueryService studio;
    private final ContextCreationService context;
    private final ApplicationEventPublisher events;
    private final Duration grace;

    AttemptService(AssessmentAttemptRepository attempts, ConversationEntryRepository entries,
            BfiResponseRepository bfiResponses, StudioQueryService studio, ContextCreationService context,
            ApplicationEventPublisher events,
            // REQ-AC-08: the spec sets no number; 5 s covers the browser auto-submit at zero plus network delay.
            @Value("${nifty.assessment.grace-seconds:5}") long graceSeconds) {
        this.attempts = attempts;
        this.entries = entries;
        this.bfiResponses = bfiResponses;
        this.studio = studio;
        this.context = context;
        this.events = events;
        this.grace = Duration.ofSeconds(graceSeconds);
    }

    /** REQ-AC-01 / REQ-AC-06 / REQ-AC-11: resume the open attempt, or start a new one within the limit. */
    @Transactional
    TurnOutcome start(CandidateSessionPrincipal who) {
        InterviewerView view = studio.interviewerView(who.configId());
        // shortcut: a double-click race fails on UNIQUE (invite_id, attempt_no) as a 500; add a 409 mapping if seen.
        Optional<AssessmentAttemptEntity> open =
                attempts.findFirstByInviteIdAndStatusNotOrderByAttemptNoDesc(who.inviteId(), COMPLETED);
        if (open.isPresent()) {
            return new ShowState(stateOf(open.get(), view, ledgerOf(open.get().getId())));
        }
        int started = (int) attempts.countByInviteId(who.inviteId());
        AttemptStateMachine.checkCanStart(started, view.maxAttempts());
        AttemptStatus status = AttemptStateMachine.initialStatus(view);
        AssessmentAttemptEntity attempt = attempts.save(new AssessmentAttemptEntity(who.inviteId(), who.configId(),
                versionNumber(view.configVersion()), started + 1, status.name(), Instant.now()));
        if (status == AttemptStatus.BFI10) {
            return new ShowState(stateOf(attempt, view, List.of()));
        }
        return planNext(attempt, view, List.of(), true);
    }

    /**
     * REQ-AC-07..09 / REQ-AC-20: stores the answer with its flags. A too-late answer is closed without text, the
     * interview moves on, and {@link LateAnswerException} (409) still commits that work.
     */
    @Transactional(noRollbackFor = LateAnswerException.class)
    TurnOutcome answer(UUID attemptId, UUID inviteId, String content) {
        AssessmentAttemptEntity attempt = lockOwned(attemptId, inviteId);
        requireStatus(attempt, AttemptStatus.IN_PROGRESS);
        InterviewerView view = studio.interviewerView(attempt.getConfigId());
        List<LedgerEntry> ledger = ledgerOf(attemptId);
        LedgerEntry open = AttemptStateMachine.openQuestion(ledger)
                .orElseThrow(() -> new DomainException(HttpStatus.CONFLICT, "No question is open right now."));
        Instant now = Instant.now();
        AnswerTiming timing = AttemptStateMachine.timing(open.expiresAt(), now, grace);
        if (timing == AnswerTiming.TOO_LATE) {
            closeWithoutAnswer(attempt, view, ledger, open, now);
            throw new LateAnswerException();
        }
        boolean timedOut = timing == AnswerTiming.TIMED_OUT;
        boolean hasText = !content.isBlank();
        boolean paste = PasteVelocityDetector.pasteSuspected(open.createdAt(), now,
                questionOf(view, open).minTimeSec(), content);
        // Decision 2.24: context checks the raw text and writes the audit row itself.
        boolean injection = hasText && context.inspectCandidateInput(attemptId, content).injectionSuspected();
        ledger.add(append(attemptId, ledger, LedgerEntry.CANDIDATE, open, content, now, null, timedOut, paste,
                injection));
        return hasText ? planNext(attempt, view, ledger, !timedOut) : advanceWithoutInterviewer(attempt, view, ledger, now);
    }

    /** Stores the streamed interviewer text; blank text falls back to the configured next core question. */
    @Transactional
    TurnOutcome recordInterviewerReply(UUID attemptId, NextStep step, String text) {
        AssessmentAttemptEntity attempt = attempts.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, NOT_FOUND));
        InterviewerView view = studio.interviewerView(attempt.getConfigId());
        List<LedgerEntry> ledger = ledgerOf(attemptId);
        Optional<LedgerEntry> open = AttemptStateMachine.openQuestion(ledger);
        if (open.isPresent() || !AttemptStatus.IN_PROGRESS.name().equals(attempt.getStatus())) {
            return outcomeOf(attempt, view, ledger); // someone else moved this attempt on already
        }
        Instant now = Instant.now();
        if (text.isBlank()) {
            return advanceWithoutInterviewer(attempt, view, ledger, now);
        }
        return ask(attempt, view, ledger, step, text, now);
    }

    /** REQ-AC-08: the sweep closes a question that expired past the grace window. */
    @Transactional
    void closeExpired(UUID attemptId) {
        AssessmentAttemptEntity attempt = attempts.findByIdForUpdate(attemptId).orElse(null);
        if (attempt == null || !AttemptStatus.IN_PROGRESS.name().equals(attempt.getStatus())) {
            return;
        }
        List<LedgerEntry> ledger = ledgerOf(attemptId);
        Optional<LedgerEntry> open = AttemptStateMachine.openQuestion(ledger);
        Instant now = Instant.now();
        if (open.isPresent() && AttemptStateMachine.timing(open.get().expiresAt(), now, grace) == AnswerTiming.TOO_LATE) {
            closeWithoutAnswer(attempt, studio.interviewerView(attempt.getConfigId()), ledger, open.get(), now);
        }
    }

    List<UUID> expiredAttemptIds() {
        return attempts.findIdsWithQuestionExpiredBefore(Instant.now().minus(grace));
    }

    SessionStateResponse state(UUID attemptId, UUID inviteId) {
        AssessmentAttemptEntity attempt = owned(attempts.findById(attemptId), inviteId);
        return stateOf(attempt, studio.interviewerView(attempt.getConfigId()), ledgerOf(attemptId));
    }

    /** REQ-AC-14 / REQ-AC-15: scores once, stores the row and completes the attempt. */
    @Transactional
    void submitBfi10(UUID attemptId, UUID inviteId, List<Integer> items) {
        int[] answers = Bfi10ScoringService.validate(items);
        AssessmentAttemptEntity attempt = lockOwned(attemptId, inviteId);
        requireStatus(attempt, AttemptStatus.BFI10);
        Bfi10Traits traits = Bfi10ScoringService.score(answers);
        Instant now = Instant.now();
        bfiResponses.save(new BfiResponseEntity(attemptId, answers, traits.conscientiousness(), traits.agreeableness(),
                traits.neuroticism(), traits.extraversion(), traits.openness(), now));
        complete(attempt, now);
    }

    private TurnOutcome planNext(AssessmentAttemptEntity attempt, InterviewerView view, List<LedgerEntry> ledger,
            boolean followUpAllowed) {
        NextStep step = AttemptStateMachine.next(view, ledger, followUpAllowed);
        if (step instanceof Finish) {
            return finishInterview(attempt, view, Instant.now());
        }
        return new AwaitInterviewer(attempt.getId(), step, contextInput(attempt.getId(), view, ledger, step));
    }

    private void closeWithoutAnswer(AssessmentAttemptEntity attempt, InterviewerView view, List<LedgerEntry> ledger,
            LedgerEntry open, Instant now) {
        ledger.add(append(attempt.getId(), ledger, LedgerEntry.CANDIDATE, open, "", now, null, true, false, false));
        advanceWithoutInterviewer(attempt, view, ledger, now);
    }

    /** No LLM: the next core question in its configured words, or the end of the interview. */
    private TurnOutcome advanceWithoutInterviewer(AssessmentAttemptEntity attempt, InterviewerView view,
            List<LedgerEntry> ledger, Instant now) {
        NextStep step = AttemptStateMachine.next(view, ledger, false);
        if (step instanceof AskCore core) {
            return ask(attempt, view, ledger, step, core.question().text(), now);
        }
        return finishInterview(attempt, view, now);
    }

    private Asked ask(AssessmentAttemptEntity attempt, InterviewerView view, List<LedgerEntry> ledger, NextStep step,
            String text, Instant now) {
        InterviewerQuestion q = step instanceof AskCore core ? core.question() : ((AskFollowUp) step).question();
        EntryOrigin origin = step instanceof AskFollowUp ? EntryOrigin.FOLLOW_UP : EntryOrigin.CORE;
        // REQ-AC-07: a follow-up uses the time limits of its core question.
        LedgerEntry question = toEntry(entries.save(new ConversationEntryEntity(attempt.getId(), ledger.size() + 1,
                LedgerEntry.INTERVIEWER, q.coreQuestionId(), origin.literal(), text, q.targetSkillIds(), now,
                now.plusSeconds(q.maxTimeSec()), false, false, false)));
        ledger.add(question);
        return new Asked(question, AttemptStateMachine.questionsRemaining(view, ledger));
    }

    private Finished finishInterview(AssessmentAttemptEntity attempt, InterviewerView view, Instant now) {
        AttemptStatus next = AttemptStateMachine.statusAfterInterview(view);
        if (next == AttemptStatus.COMPLETED) {
            complete(attempt, now);
        } else {
            attempt.moveTo(next.name(), null);
        }
        return new Finished(next);
    }

    /** REQ-AC-18: published inside the committing transaction; listeners run AFTER_COMMIT. */
    private void complete(AssessmentAttemptEntity attempt, Instant now) {
        attempt.moveTo(COMPLETED, now);
        events.publishEvent(new AttemptCompletedEvent(attempt.getId(), attempt.getConfigId(), attempt.getInviteId()));
    }

    private TurnOutcome outcomeOf(AssessmentAttemptEntity attempt, InterviewerView view, List<LedgerEntry> ledger) {
        Optional<LedgerEntry> open = AttemptStateMachine.openQuestion(ledger);
        if (open.isPresent() && AttemptStatus.IN_PROGRESS.name().equals(attempt.getStatus())) {
            return new Asked(open.get(), AttemptStateMachine.questionsRemaining(view, ledger));
        }
        return new Finished(AttemptStatus.valueOf(attempt.getStatus()));
    }

    private LedgerEntry append(UUID attemptId, List<LedgerEntry> ledger, String author, LedgerEntry question,
            String content, Instant now, Instant expiresAt, boolean timedOut, boolean paste, boolean injection) {
        return toEntry(entries.save(new ConversationEntryEntity(attemptId, ledger.size() + 1, author,
                question.coreQuestionId(), question.origin().literal(), content, question.targetSkillIds(), now,
                expiresAt, timedOut, paste, injection)));
    }

    /** REQ-AC-12 / Decision 1.21: only rubric-blind fields and this attempt's own turns reach context. */
    private static InterviewerContextInput contextInput(UUID attemptId, InterviewerView view, List<LedgerEntry> ledger,
            NextStep step) {
        InterviewerQuestion q = step instanceof AskCore core ? core.question() : ((AskFollowUp) step).question();
        int used = step instanceof AskFollowUp followUp ? followUp.followUpsUsed() : 0;
        List<DialogueTurnView> turns = new ArrayList<>(ledger.size());
        for (int i = 0; i < ledger.size(); i++) {
            LedgerEntry e = ledger.get(i);
            turns.add(new DialogueTurnView(e.seq(), e.author(), e.origin().literal(), e.content()));
        }
        Set<String> skillNames = new LinkedHashSet<>();
        int remaining = 0;
        for (int i = 0; i < view.questions().size(); i++) {
            InterviewerQuestion each = view.questions().get(i);
            skillNames.addAll(each.targetSkillNames());
            if (each.position() > q.position()) {
                remaining++;
            }
        }
        return new InterviewerContextInput(attemptId, view.configVersion(), view.vision(), List.copyOf(skillNames),
                new CoreQuestionPromptView(q.coreQuestionId(), q.text(), q.targetSkillNames(), q.followUpLimit(), used),
                remaining, view.inventedQuestionsEnabled(), turns);
    }

    private SessionStateResponse stateOf(AssessmentAttemptEntity attempt, InterviewerView view,
            List<LedgerEntry> ledger) {
        boolean interviewing = AttemptStatus.IN_PROGRESS.name().equals(attempt.getStatus());
        List<SessionStateResponse.Message> messages = new ArrayList<>(ledger.size());
        for (int i = 0; i < ledger.size(); i++) {
            LedgerEntry e = ledger.get(i);
            messages.add(new SessionStateResponse.Message(e.seq(), e.author(), e.content(), e.createdAt()));
        }
        Instant expiresAt = interviewing
                ? AttemptStateMachine.openQuestion(ledger).map(LedgerEntry::expiresAt).orElse(null) : null;
        return new SessionStateResponse(attempt.getId(), AttemptStatus.valueOf(attempt.getStatus()),
                attempt.getAttemptNo(), view.bfi10Enabled(),
                interviewing ? AttemptStateMachine.questionsRemaining(view, ledger) : 0, expiresAt, messages);
    }

    private List<LedgerEntry> ledgerOf(UUID attemptId) {
        List<ConversationEntryEntity> rows = entries.findByAttemptIdOrderBySeqAsc(attemptId);
        List<LedgerEntry> ledger = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            ledger.add(toEntry(rows.get(i)));
        }
        return ledger;
    }

    static LedgerEntry toEntry(ConversationEntryEntity e) {
        return new LedgerEntry(e.getId(), e.getSeq(), e.getAuthor(), e.getCoreQuestionId(),
                EntryOrigin.fromLiteral(e.getOrigin()), e.getContent(), e.getTargetSkills(), e.getCreatedAt(),
                e.getExpiresAt(), e.isTimedOut(), e.isPasteSuspected(), e.isInjectionSuspected());
    }

    private AssessmentAttemptEntity lockOwned(UUID attemptId, UUID inviteId) {
        return owned(attempts.findByIdForUpdate(attemptId), inviteId);
    }

    private static AssessmentAttemptEntity owned(Optional<AssessmentAttemptEntity> attempt, UUID inviteId) {
        AssessmentAttemptEntity found = attempt.orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (!found.getInviteId().equals(inviteId)) {
            throw new DomainException(HttpStatus.FORBIDDEN, FOREIGN);
        }
        return found;
    }

    private static void requireStatus(AssessmentAttemptEntity attempt, AttemptStatus expected) {
        if (!expected.name().equals(attempt.getStatus())) {
            throw new DomainException(HttpStatus.CONFLICT, "This session is not at the " + expected + " step.");
        }
    }

    private static InterviewerQuestion questionOf(InterviewerView view, LedgerEntry entry) {
        for (int i = 0; i < view.questions().size(); i++) {
            if (view.questions().get(i).coreQuestionId().equals(entry.coreQuestionId())) {
                return view.questions().get(i);
            }
        }
        throw new IllegalStateException("Ledger entry " + entry.id() + " names no core question of the config");
    }

    /** InterviewerView carries "v1"; the table stores 1. */
    static int versionNumber(String configVersion) {
        return Integer.parseInt(configVersion.startsWith("v") ? configVersion.substring(1) : configVersion);
    }
}
