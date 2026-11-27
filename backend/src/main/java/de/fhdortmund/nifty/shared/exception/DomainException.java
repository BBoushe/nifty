package de.fhdortmund.nifty.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Expected domain failure. Services throw it (or a subclass); GlobalExceptionHandler turns it into an
 * RFC 9457 ProblemDetail with this status and detail, so the detail must be safe to show to clients.
 */
public class DomainException extends RuntimeException {

    private final HttpStatus status;
    // REQ-AA-14: seconds for the Retry-After header of a 429. Null for every other failure.
    private final Long retryAfterSeconds;

    public DomainException(HttpStatus status, String detail) {
        this(status, detail, null);
    }

    public DomainException(HttpStatus status, String detail, Long retryAfterSeconds) {
        super(detail);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
