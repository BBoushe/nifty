package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * REQ-EP-01: deterministic verbatim quote verification (>= 8 chars, whitespace-normalized,
 * exact case/punctuation match, ledger text unchanged).
 */
class VerbatimQuoteVerifierTest {

    private final VerbatimQuoteVerifier verifier = new VerbatimQuoteVerifier();

    @Test
    void acceptsExactSubstringOfAtLeastEightCharacters() {
        String answer = "I would wrap it in a @Transactional boundary so rollback keeps state consistent.";
        assertThat(verifier.isVerified(answer, "wrap it in a @Transactional")).isTrue();
    }

    @Test
    void normalizesLineBreaksAndRunsOfWhitespaceWithoutChangingOtherCharacters() {
        String answer = "I would wrap it in a\n\t  transaction to avoid partial writes.";
        assertThat(verifier.isVerified(answer, "wrap it in a transaction")).isTrue();
        // Original answer string stays untouched (Java String is immutable, also verify case sensitivity)
        assertThat(verifier.isVerified(answer, "Wrap it in a transaction")).isFalse();
    }

    @Test
    void rejectsHallucinatedQuoteOrShortQuoteUnderEightChars() {
        String answer = "I add a database index on the email column.";
        assertThat(verifier.isVerified(answer, "I use Redis cache for emails")).isFalse();
        assertThat(verifier.isVerified(answer, "index")).isFalse();
        assertThat(verifier.isVerified(answer, "   a   b   ")).isFalse();
        assertThat(verifier.isVerified(null, "some quote")).isFalse();
        assertThat(verifier.isVerified(answer, null)).isFalse();
    }

    @Test
    void identifiesSubstantiveAnswersAtEightCharactersThreshold() {
        assertThat(verifier.isSubstantiveAnswer("short")).isFalse();
        assertThat(verifier.isSubstantiveAnswer("   1234567   ")).isFalse();
        assertThat(verifier.isSubstantiveAnswer("12345678")).isTrue();
        assertThat(verifier.isSubstantiveAnswer("  a  b  c  d  ")).isFalse();
        assertThat(verifier.isSubstantiveAnswer(null)).isFalse();
    }
}
