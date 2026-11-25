package de.fhdortmund.nifty.shared.config;

import java.util.UUID;

/**
 * Published by studio when a professor deletes an assessment. Decision 4.4: listeners are synchronous
 * {@code @EventListener} + {@code @Transactional(MANDATORY)}, so the purge runs in the deleting transaction.
 */
public record AssessmentDeletedEvent(UUID configId, String actorId) {
}
