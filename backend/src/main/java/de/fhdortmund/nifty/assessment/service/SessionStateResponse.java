package de.fhdortmund.nifty.assessment.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REQ-AC-06: the session as the candidate sees it after a reload. No scores, weights or flags (REQ-AC-03).
 * {@code expiresAt} is set only while a question is open.
 */
public record SessionStateResponse(
        UUID attemptId,
        AttemptStatus status,
        int attemptNo,
        boolean bfi10Enabled,
        int questionsRemaining,
        Instant expiresAt,
        List<Message> messages
) {
    public record Message(int seq, String author, String content, Instant createdAt) {
    }
}
