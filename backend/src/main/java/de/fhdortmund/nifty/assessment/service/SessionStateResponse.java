package de.fhdortmund.nifty.assessment.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REQ-AC-06: the session as the candidate sees it after a reload. No scores, weights or flags (REQ-AC-03).
 * {@code attemptsLeft} (REQ-AC-22) is the number of attempts the invite may still start, for the retake button.
 * {@code expiresAt} is set only while a question is open. {@code serverTime} is the server clock when the state was
 * read, so the browser can correct its countdown.
 */
public record SessionStateResponse(
        UUID attemptId,
        AttemptStatus status,
        int attemptNo,
        boolean bfi10Enabled,
        int questionsRemaining,
        int attemptsLeft,
        Instant expiresAt,
        List<Message> messages,
        Instant serverTime
) {
    public record Message(int seq, String author, String content, Instant createdAt) {
    }
}
