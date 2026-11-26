package de.fhdortmund.nifty.assessment.service;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What a session start or an answer returns (REQ-AC-05): a JSON state (SOFT_SKILLS-only start or a resumed attempt)
 * or an SSE stream. The stream sends {@code delta} events, then one {@code question} or {@code done} event.
 */
public sealed interface SessionReply {

    record State(SessionStateResponse state) implements SessionReply {
    }

    /** Runs after the request transaction committed, so the interviewer call holds no database lock. */
    record Stream(Consumer<SseSink> body) implements SessionReply {
    }

    /** SSE {@code delta}: one chunk of the interviewer reply. */
    record Delta(String text) {
    }

    /** SSE {@code question}: the reply is complete and the question is open until {@code expiresAt}. */
    record Question(UUID entryId, EntryOrigin origin, Instant expiresAt, int questionsRemaining) {
    }

    /** SSE {@code done}: the interview ended; {@code status} is BFI10 or COMPLETED. */
    record Done(AttemptStatus status) {
    }
}
