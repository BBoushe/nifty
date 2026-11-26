package de.fhdortmund.nifty.studio.service;

import java.util.UUID;

/** Result of one-shot assessment creation: {@code created = true} -> 201, {@code false} -> 200 idempotent replay. */
public record CreatedAssessment(UUID configId, boolean created) {
}
