package de.fhdortmund.nifty.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Expected domain failure. Services throw it (or a subclass); GlobalExceptionHandler turns it into an
 * RFC 9457 ProblemDetail with this status and detail, so the detail must be safe to show to clients.
 */
public class DomainException extends RuntimeException {

    private final HttpStatus status;

    public DomainException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
