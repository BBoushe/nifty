package de.fhdortmund.nifty.assessment.service;

import de.fhdortmund.nifty.assessment.repository.AssessmentAttemptEntity;
import de.fhdortmund.nifty.assessment.repository.AssessmentAttemptRepository;
import de.fhdortmund.nifty.assessment.repository.BfiResponseEntity;
import de.fhdortmund.nifty.assessment.repository.BfiResponseRepository;
import de.fhdortmund.nifty.assessment.repository.ConversationEntryEntity;
import de.fhdortmund.nifty.assessment.repository.ConversationEntryRepository;
import de.fhdortmund.nifty.shared.config.AttemptCompletedEvent;
import de.fhdortmund.nifty.shared.exception.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-AC-17 / Contract pack §3.6: read-only views for evaluation and teams. The transcript carries ledger data only;
 * BFI-10 trait means leave this slice already scored and never reach the evaluator (REQ-AC-16). No Streams.
 */
@Service
@Transactional(readOnly = true)
class AssessmentQueryServiceImpl implements AssessmentQueryService {

    private static final String COMPLETED = AttemptStatus.COMPLETED.name();

    private final AssessmentAttemptRepository attempts;
    private final ConversationEntryRepository entries;
    private final BfiResponseRepository bfiResponses;

    AssessmentQueryServiceImpl(AssessmentAttemptRepository attempts, ConversationEntryRepository entries,
            BfiResponseRepository bfiResponses) {
        this.attempts = attempts;
        this.entries = entries;
        this.bfiResponses = bfiResponses;
    }

    /** One entry per answered question: the interviewer row followed by the candidate row that answers it. */
    @Override
    public ImmutableTranscriptView getTranscriptForAttempt(UUID attemptId) {
        AssessmentAttemptEntity attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "Attempt not found."));
        List<ConversationEntryEntity> rows = entries.findByAttemptIdOrderBySeqAsc(attemptId);
        List<TranscriptEntryView> pairs = new ArrayList<>(rows.size() / 2);
        for (int i = 0; i + 1 < rows.size(); i++) {
            LedgerEntry question = LedgerEntry.of(rows.get(i));
            LedgerEntry answer = LedgerEntry.of(rows.get(i + 1));
            if (question.isInterviewer() && !answer.isInterviewer()) {
                pairs.add(new TranscriptEntryView(answer.id(), answer.seq(), question.coreQuestionId(),
                        question.origin(), question.content(), answer.content(), question.targetSkillIds(),
                        question.createdAt(), question.expiresAt(), answer.createdAt(),
                        Duration.between(question.createdAt(), answer.createdAt()).toSeconds(), answer.timedOut(),
                        answer.pasteSuspected(), answer.injectionSuspected()));
                i++;
            }
        }
        return new ImmutableTranscriptView(attempt.getId(), attempt.getConfigId(), attempt.getInviteId(),
                "v" + attempt.getConfigVersion(), attempt.getAttemptNo(), attempt.getStartedAt(),
                attempt.getCompletedAt(), pairs);
    }

    @Override
    public Optional<UUID> getLatestCompletedAttemptId(UUID configId, UUID inviteId) {
        return attempts.findFirstByConfigIdAndInviteIdAndStatusOrderByAttemptNoDesc(configId, inviteId, COMPLETED)
                .map(AssessmentAttemptEntity::getId);
    }

    /** Decision 2.13: only the latest completed attempt of each invite counts. */
    @Override
    public List<StudentSoftSkillProfile> getSoftSkillProfilesForConfig(UUID configId) {
        List<AssessmentAttemptEntity> completed =
                attempts.findByConfigIdAndStatusOrderByInviteIdAscAttemptNoDesc(configId, COMPLETED);
        List<StudentSoftSkillProfile> profiles = new ArrayList<>();
        UUID previousInvite = null;
        for (int i = 0; i < completed.size(); i++) {
            AssessmentAttemptEntity attempt = completed.get(i);
            if (!attempt.getInviteId().equals(previousInvite)) {
                profiles.add(profileOf(attempt));
                previousInvite = attempt.getInviteId();
            }
        }
        return profiles;
    }

    @Override
    public Optional<StudentSoftSkillProfile> getSoftSkillProfileForAttempt(UUID attemptId) {
        return attempts.findById(attemptId).filter(a -> COMPLETED.equals(a.getStatus())).map(this::profileOf);
    }

    @Override
    public List<AttemptCompletedEvent> completedAttemptsSince(Instant since) {
        List<AssessmentAttemptEntity> completed =
                attempts.findByStatusAndCompletedAtGreaterThanEqual(COMPLETED, since);
        List<AttemptCompletedEvent> events = new ArrayList<>(completed.size());
        for (int i = 0; i < completed.size(); i++) {
            AssessmentAttemptEntity attempt = completed.get(i);
            events.add(new AttemptCompletedEvent(attempt.getId(), attempt.getConfigId(), attempt.getInviteId()));
        }
        return events;
    }

    // shortcut: one BFI-10 lookup per invite; batch the read if a cohort grows past a few hundred candidates.
    private StudentSoftSkillProfile profileOf(AssessmentAttemptEntity attempt) {
        Map<String, Double> scores = bfiResponses.findById(attempt.getId()).map(AssessmentQueryServiceImpl::traits)
                .orElse(null);
        return new StudentSoftSkillProfile(attempt.getInviteId(), attempt.getId(), scores);
    }

    private static Map<String, Double> traits(BfiResponseEntity bfi) {
        return new Bfi10Traits(bfi.getConscientiousness(), bfi.getAgreeableness(), bfi.getNeuroticism(),
                bfi.getExtraversion(), bfi.getOpenness()).asMap();
    }
}
