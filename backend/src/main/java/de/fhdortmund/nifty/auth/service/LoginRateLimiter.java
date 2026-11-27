package de.fhdortmund.nifty.auth.service;

import de.fhdortmund.nifty.auth.repository.ProfessorAccountEntity;
import de.fhdortmund.nifty.shared.exception.DomainException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * REQ-AA-14: after 5 failed professor sign-ins for one email within 15 minutes, further sign-ins answer 429
 * with {@code Retry-After}, also with the correct password. A success resets the count. The key is the lower-case
 * email, so a wrong password for an unknown email counts too and gives nothing away.
 */
// shortcut: in-memory, single instance only; move to a shared store when the backend scales out
@Component
public class LoginRateLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED = 10_000;
    private static final String TOO_MANY = "Too many failed sign-in attempts. Try again later.";

    /** Failures since {@code start}. The window is fixed from the first failure, not sliding. */
    private record Failures(Instant start, int count) {
    }

    private final Clock clock;
    private final Map<String, Failures> byEmail;

    @Autowired
    public LoginRateLimiter() {
        this(Clock.systemUTC(), MAX_TRACKED);
    }

    LoginRateLimiter(Clock clock, int maxTracked) {
        this.clock = clock;
        // Bounded: the oldest entry leaves first, so the map cannot grow without limit.
        // shortcut: a flood of other emails can push one email out early. Upgrade with the shared store.
        this.byEmail = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Failures> eldest) {
                return size() > maxTracked;
            }
        };
    }

    /** Throws 429 while the email is blocked. */
    public synchronized void checkAllowed(String email) {
        String key = key(email);
        Failures failures = byEmail.get(key);
        if (failures == null) {
            return;
        }
        Instant now = clock.instant();
        Instant end = failures.start().plus(WINDOW);
        if (!now.isBefore(end)) {
            byEmail.remove(key);
        } else if (failures.count() >= MAX_FAILURES) {
            long seconds = Math.max(1, (Duration.between(now, end).toMillis() + 999) / 1000);
            throw new DomainException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY, seconds);
        }
    }

    public synchronized void recordFailure(String email) {
        String key = key(email);
        Instant now = clock.instant();
        Failures failures = byEmail.get(key);
        if (failures == null || !now.isBefore(failures.start().plus(WINDOW))) {
            byEmail.put(key, new Failures(now, 1));
        } else {
            byEmail.put(key, new Failures(failures.start(), failures.count() + 1));
        }
    }

    public synchronized void reset(String email) {
        byEmail.remove(key(email));
    }

    private static String key(String email) {
        return ProfessorAccountEntity.normalizeEmail(email);
    }
}
