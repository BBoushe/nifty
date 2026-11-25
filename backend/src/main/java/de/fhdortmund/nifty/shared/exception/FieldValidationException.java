package de.fhdortmund.nifty.shared.exception;

import java.util.List;

/**
 * Services throw this to report all invalid fields at once (REQ-BF-02). GlobalExceptionHandler maps it to
 * a {@code 400} ProblemDetail with {@code errors[]}.
 */
public class FieldValidationException extends RuntimeException {

    private final List<FieldErrorDetail> errors;

    public FieldValidationException(List<FieldErrorDetail> errors) {
        super("Invalid request content.");
        this.errors = List.copyOf(errors);
    }

    public List<FieldErrorDetail> getErrors() {
        return errors;
    }
}
