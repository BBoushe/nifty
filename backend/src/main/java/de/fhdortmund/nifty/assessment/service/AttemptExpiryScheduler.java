package de.fhdortmund.nifty.assessment.service;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * REQ-AC-08: closes questions nobody answered, so an absent candidate's clock keeps running (Decision 2.14, no pause).
 * Each attempt commits on its own; one failure does not stop the sweep.
 * shortcut: an attempt whose interviewer stream died mid-reply (server crash) stays without an open question;
 * add a stale-reply recovery here if that happens.
 */
@Component
class AttemptExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttemptExpiryScheduler.class);

    private final AttemptService attempts;

    AttemptExpiryScheduler(AttemptService attempts) {
        this.attempts = attempts;
    }

    @Scheduled(cron = "${nifty.assessment.expiry-sweep-cron:*/15 * * * * *}")
    void sweep() {
        List<UUID> expired = attempts.expiredAttemptIds();
        for (int i = 0; i < expired.size(); i++) {
            try {
                attempts.closeExpired(expired.get(i));
            } catch (RuntimeException ex) {
                log.warn("Expiry sweep failed for attempt {}: {}", expired.get(i), ex.getMessage());
            }
        }
    }
}
