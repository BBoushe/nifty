package de.fhdortmund.nifty.evaluation.service;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ScoreStatus {
    SCORED("SCORED"), NOT_ASSESSED("N/A");

    private final String literal;

    ScoreStatus(String literal) {
        this.literal = literal;
    }

    /** JSON and DB literal. */
    @JsonValue
    public String literal() {
        return literal;
    }
}
