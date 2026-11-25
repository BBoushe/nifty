package de.fhdortmund.nifty.evaluation.service;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ReleaseStatus {
    PENDING("pending"), FLAGGED("flagged"), AUTO_RELEASED("auto_released"), APPROVED("approved"),
    OVERRIDDEN("overridden");

    private final String literal;

    ReleaseStatus(String literal) {
        this.literal = literal;
    }

    /** JSON and DB literal. */
    @JsonValue
    public String literal() {
        return literal;
    }
}
