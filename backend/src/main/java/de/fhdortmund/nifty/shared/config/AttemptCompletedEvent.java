package de.fhdortmund.nifty.shared.config;

import java.util.UUID;

/** Published by assessment when an attempt completes. Listeners use {@code @TransactionalEventListener(AFTER_COMMIT)}. */
public record AttemptCompletedEvent(UUID attemptId, UUID configId, UUID inviteId) {
}
