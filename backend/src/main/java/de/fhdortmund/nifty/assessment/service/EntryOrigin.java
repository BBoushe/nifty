package de.fhdortmund.nifty.assessment.service;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EntryOrigin {
    CORE("core"), FOLLOW_UP("follow_up"), INVENTED("invented");

    private final String literal;

    EntryOrigin(String literal) {
        this.literal = literal;
    }

    /** JSON and DB literal. */
    @JsonValue
    public String literal() {
        return literal;
    }

    static EntryOrigin fromLiteral(String literal) {
        for (EntryOrigin origin : values()) {
            if (origin.literal.equals(literal)) {
                return origin;
            }
        }
        throw new IllegalArgumentException("Unknown entry origin: " + literal);
    }
}
