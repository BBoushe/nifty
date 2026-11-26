package de.fhdortmund.nifty.evaluation.service;

import org.springframework.stereotype.Component;

/**
 * REQ-EP-01: deterministic verbatim quote verification.
 * Collapses whitespace runs without mutating any other character or the persisted ledger text.
 * Zero Java Streams (Coding Standard #3).
 */
@Component
public class VerbatimQuoteVerifier {

    static final int MIN_QUOTE_LENGTH = 8;

    public boolean isVerified(String answerText, String quote) {
        if (answerText == null || quote == null) {
            return false;
        }
        String normalizedQuote = normalizeWhitespace(quote);
        if (normalizedQuote.length() < MIN_QUOTE_LENGTH) {
            return false;
        }
        String normalizedAnswer = normalizeWhitespace(answerText);
        if (normalizedAnswer.length() < normalizedQuote.length()) {
            return false;
        }
        return normalizedAnswer.contains(normalizedQuote);
    }

    public boolean isSubstantiveAnswer(String answerText) {
        if (answerText == null) {
            return false;
        }
        return normalizeWhitespace(answerText).length() >= MIN_QUOTE_LENGTH;
    }

    static String normalizeWhitespace(String input) {
        int len = input.length();
        StringBuilder out = new StringBuilder(len);
        boolean inWhitespace = true;
        for (int i = 0; i < len; i++) {
            char ch = input.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!inWhitespace) {
                    out.append(' ');
                    inWhitespace = true;
                }
            } else {
                out.append(ch);
                inWhitespace = false;
            }
        }
        int end = out.length();
        if (end > 0 && out.charAt(end - 1) == ' ') {
            out.setLength(end - 1);
        }
        return out.toString();
    }
}
