package de.fhdortmund.nifty.shared.exception;

/** One invalid field in a {@code 400} response: {@code field} is a path such as {@code questions[0].minTimeSec}. */
public record FieldErrorDetail(String field, String message, String code) {
}
