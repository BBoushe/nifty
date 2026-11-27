package de.fhdortmund.nifty.assessment.service;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * REQ-AC-08: closes questions nobody answered, so an absent candidate's clock keeps running (Decision 2.14, no pause).
 * It also reopens attempts whose interviewer reply never reached the ledger (stream died, server restart), and
 * completes attempts left in the BFI-10 step (Decision 2.33).
 * Each attempt commits on its own; one failure does not stop the sweep.
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
        each(attempts.expiredAttemptIds(), attempts::closeExpired);
        each(attempts.staleAttemptIds(), attempts::recoverStale);
        each(attempts.bfi10ExpiredAttemptIds(), attempts::completeWithoutBfi10); // Decision 2.33
    }

    private static void each(List<UUID> ids, Consumer<UUID> action) {
        for (int i = 0; i < ids.size(); i++) {
            try {
                action.accept(ids.get(i));
            } catch (RuntimeException ex) {
                log.warn("Expiry sweep failed for attempt {}: {}", ids.get(i), ex.getMessage());
            }
        }
    }
}
