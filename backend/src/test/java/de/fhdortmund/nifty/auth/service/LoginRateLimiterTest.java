package de.fhdortmund.nifty.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.shared.exception.DomainException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** REQ-AA-14: 5 failures per email in 15 minutes block sign-in with 429. A success resets the count. */
class LoginRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock, 100);

    @Test
    void sixthAttemptAfterFiveFailuresAnswers429WithRetryAfter() {
        failTimes("prof@example.org", 5);

        assertThatThrownBy(() -> limiter.checkAllowed("prof@example.org"))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getRetryAfterSeconds()).isEqualTo(15 * 60);
                });
    }

    @Test
    void fourFailuresStillAllowTheNextAttempt() {
        failTimes("prof@example.org", 4);

        assertThatCode(() -> limiter.checkAllowed("prof@example.org")).doesNotThrowAnyException();
    }

    @Test
    void retryAfterShrinksAsTheWindowRuns() {
        failTimes("prof@example.org", 5);
        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> limiter.checkAllowed("prof@example.org"))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(5 * 60));
    }

    @Test
    void theBlockEndsAfterFifteenMinutes() {
        failTimes("prof@example.org", 5);
        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> limiter.checkAllowed("prof@example.org")).doesNotThrowAnyException();
    }

    @Test
    void successResetsTheCount() {
        failTimes("prof@example.org", 4);
        limiter.reset("prof@example.org");
        failTimes("prof@example.org", 4);

        assertThatCode(() -> limiter.checkAllowed("prof@example.org")).doesNotThrowAnyException();
    }

    @Test
    void theKeyIsTheLowerCaseEmailAndOtherEmailsAreFree() {
        failTimes("Prof@Example.ORG", 3);
        failTimes(" prof@example.org ", 2);

        assertThatThrownBy(() -> limiter.checkAllowed("PROF@example.org")).isInstanceOf(DomainException.class);
        assertThatCode(() -> limiter.checkAllowed("other@example.org")).doesNotThrowAnyException();
    }

    // shortcut in the limiter: the map has a hard size, so the oldest entry leaves first.
    @Test
    void theMapIsBoundedAndTheOldestEntryLeavesFirst() {
        LoginRateLimiter small = new LoginRateLimiter(clock, 2);
        for (int i = 0; i < 5; i++) {
            small.recordFailure("first@example.org");
        }
        small.recordFailure("second@example.org");
        small.recordFailure("third@example.org");

        assertThatCode(() -> small.checkAllowed("first@example.org")).doesNotThrowAnyException();
    }

    private void failTimes(String email, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(email);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-27T10:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
