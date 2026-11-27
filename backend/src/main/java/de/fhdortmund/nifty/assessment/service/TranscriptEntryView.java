package de.fhdortmund.nifty.assessment.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One entry = one question/answer pair. entryId is the ID of the candidate-answer row in conversation_entry. */
public record TranscriptEntryView(
        UUID entryId,
        int seq,
        UUID coreQuestionId,
        EntryOrigin origin,
        String questionText,
        String answerText,                 // empty/null if unanswered after expiry
        List<UUID> targetSkillIds,
        Instant askedAt,
        Instant expiresAt,
        Instant submittedAt,
        long durationSec,
        boolean timedOut,
        boolean pasteSuspected,
        boolean injectionSuspected
) {
}
