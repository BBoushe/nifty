package de.fhdortmund.nifty.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** REQ-AC-09 / Decisions 2.14, 2.24: too-fast and too-quick-to-type answers set pasteSuspected. */
class PasteVelocityDetectorTest {

    private static final Instant ASKED = Instant.parse("2026-11-26T10:00:00Z");

    @Test
    void answerBeforeTheMinimumTimeIsSuspected() {
        assertThat(PasteVelocityDetector.pasteSuspected(ASKED, ASKED.plusSeconds(6), 30, "x".repeat(900))).isTrue();
        assertThat(PasteVelocityDetector.pasteSuspected(ASKED, ASKED.plusSeconds(29), 30, "short")).isTrue();
    }

    @Test
    void longAnswerFasterThanTwelveCharactersPerSecondIsSuspectedAfterTheMinimumTime() {
        // 600 chars in 40 s = 15 chars/s.
        assertThat(PasteVelocityDetector.pasteSuspected(ASKED, ASKED.plusSeconds(40), 30, "x".repeat(600))).isTrue();
        // 150 chars in 12 s = 12.5 chars/s.
        assertThat(PasteVelocityDetector.pasteSuspected(ASKED, ASKED.plusSeconds(12), 10, "x".repeat(150))).isTrue();
    }

    @Test
    void exactlyTwelveCharactersPerSecondAndShortFastAnswersAreNotSuspected() {
        // 600 chars in 50 s = 12 chars/s: the rule is "faster than 12".
        assertThat(PasteVelocityDetector.pasteSuspected(ASKED, ASKED.plusSeconds(50), 30, "x".repeat(600))).isFalse();
        // 149 chars in 6 s (~25 chars/s) stay below the 150-character floor of the velocity rule.
        assertThat(PasteVelocityDetector.pasteSuspected(ASKED, ASKED.plusSeconds(6), 5, "x".repeat(149))).isFalse();
    }

    @Test
    void answerExactlyAtTheMinimumTimeIsNotSuspected() {
        // "before askedAt + minimum time": the boundary itself is allowed.
        assertThat(PasteVelocityDetector.pasteSuspected(ASKED, ASKED.plusSeconds(30), 30, "short")).isFalse();
        assertThat(PasteVelocityDetector.pasteSuspected(ASKED, ASKED.plusMillis(29_999), 30, "short")).isTrue();
    }

    @Test
    void blankAnswerIsNeverSuspected() {
        assertThat(PasteVelocityDetector.pasteSuspected(ASKED, ASKED.plusSeconds(1), 30, "   ")).isFalse();
    }
}
