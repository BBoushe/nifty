package de.fhdortmund.nifty.shared.exception;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single error boundary (backend-java.md §8, docs/api.md §3): every API error is an RFC 9457
 * ProblemDetail. Spring MVC exceptions are mapped by the base class; nothing leaks stack traces or
 * exception class names.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String GENERIC_DETAIL = "An unexpected error occurred.";
    private static final URI ABOUT_BLANK = URI.create("about:blank");

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setProperty("errors", invalidFields(ex));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @ExceptionHandler(FieldValidationException.class)
    ResponseEntity<Object> handleFieldValidation(FieldValidationException ex, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        body.setProperty("errors", ex.getErrors());
        return handleExceptionInternal(ex, body, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> handleDomain(DomainException ex, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        return handleExceptionInternal(ex, body, new HttpHeaders(), ex.getStatus(), request);
    }

    // REQ-AC-24: a full bounded pool rejects the task; the client may retry.
    @ExceptionHandler(TaskRejectedException.class)
    ResponseEntity<Object> handleRejected(TaskRejectedException ex, WebRequest request) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "The server is busy. Try again in a moment.");
        return handleExceptionInternal(ex, body, new HttpHeaders(), status, request);
    }

    // Method security (@PreAuthorize) failures must reach Spring Security's 401/403 handling, not the 500 below.
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void rethrowSecurity(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        logger.error("Unhandled exception", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, GENERIC_DETAIL);
        return handleExceptionInternal(ex, body, new HttpHeaders(), status, request);
    }

    // Every response passes here; Spring 7 leaves type null, but docs/api.md §3 promises the field.
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getType() == null) {
            problem.setType(ABOUT_BLANK);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static List<Map<String, String>> invalidFields(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add(Map.of("field", error.getField(), "message", messageOf(error)));
        }
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            errors.add(Map.of("field", error.getObjectName(), "message", messageOf(error)));
        }
        return errors;
    }

    private static String messageOf(ObjectError error) {
        return error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage();
    }
}
