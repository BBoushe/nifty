package de.fhdortmund.nifty.assessment.service;

import de.fhdortmund.nifty.assessment.repository.ConversationEntryEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One ledger row as the service layer sees it. {@code createdAt} is askedAt for an interviewer row and submittedAt
 * for a candidate row. Only interviewer rows carry {@code expiresAt}.
 */
record LedgerEntry(
        UUID id,
        int seq,
        String author,
        UUID coreQuestionId,
        EntryOrigin origin,
        String content,
        List<UUID> targetSkillIds,
        Instant createdAt,
        Instant expiresAt,
        boolean timedOut,
        boolean pasteSuspected,
        boolean injectionSuspected
) {
    static final String INTERVIEWER = "interviewer";
    static final String CANDIDATE = "candidate";

    static LedgerEntry of(ConversationEntryEntity e) {
        return new LedgerEntry(e.getId(), e.getSeq(), e.getAuthor(), e.getCoreQuestionId(),
                EntryOrigin.fromLiteral(e.getOrigin()), e.getContent(), e.getTargetSkills(), e.getCreatedAt(),
                e.getExpiresAt(), e.isTimedOut(), e.isPasteSuspected(), e.isInjectionSuspected());
    }

    boolean isInterviewer() {
        return INTERVIEWER.equals(author);
    }
}
