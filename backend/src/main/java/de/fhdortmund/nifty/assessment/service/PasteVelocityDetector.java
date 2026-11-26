package de.fhdortmund.nifty.assessment.service;

import java.time.Duration;
import java.time.Instant;

/** REQ-AC-09 / Decisions 2.14, 2.24: deterministic paste flag from server timestamps only. No Streams. */
final class PasteVelocityDetector {

    static final int VELOCITY_MIN_CHARS = 150;
    static final int MAX_CHARS_PER_SECOND = 12;

    private PasteVelocityDetector() {
    }

    static boolean pasteSuspected(Instant askedAt, Instant submittedAt, int minTimeSec, String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        long elapsedMillis = Duration.between(askedAt, submittedAt).toMillis();
        if (elapsedMillis < minTimeSec * 1000L) {
            return true;
        }
        // chars / seconds > 12, in integer milliseconds to avoid rounding.
        return content.length() >= VELOCITY_MIN_CHARS
                && content.length() * 1000L > MAX_CHARS_PER_SECOND * elapsedMillis;
    }
}
