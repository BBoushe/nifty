package de.fhdortmund.nifty.context.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.fhdortmund.nifty.context.service.dto.PromptMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** REQ-CC-07: identical input gives an identical 64-character SHA-256. */
class PromptHasherTest {

    private static final List<PromptMessage> MESSAGES = List.of(new PromptMessage("user", "hello"));

    @Test
    void sameInputGivesSameLowercaseHexHash() {
        String first = PromptHasher.sha256("interviewer-v1", "system", MESSAGES);
        String second = PromptHasher.sha256("interviewer-v1", "system", List.of(new PromptMessage("user", "hello")));

        assertThat(first).isEqualTo(second).matches("[0-9a-f]{64}");
    }

    @Test
    void everyPartChangesTheHash() {
        String base = PromptHasher.sha256("interviewer-v1", "system", MESSAGES);

        assertThat(PromptHasher.sha256("interviewer-v2", "system", MESSAGES)).isNotEqualTo(base);
        assertThat(PromptHasher.sha256("interviewer-v1", "system2", MESSAGES)).isNotEqualTo(base);
        assertThat(PromptHasher.sha256("interviewer-v1", "system", List.of(new PromptMessage("assistant", "hello"))))
                .isNotEqualTo(base);
        assertThat(PromptHasher.sha256("interviewer-v1", "system", List.of(new PromptMessage("user", "hello!"))))
                .isNotEqualTo(base);
    }

    @Test
    void movingTextAcrossPartBoundariesChangesTheHash() {
        String joined = PromptHasher.sha256("v", "ab", List.of(new PromptMessage("user", "c")));
        String shifted = PromptHasher.sha256("v", "a", List.of(new PromptMessage("user", "bc")));

        assertThat(joined).isNotEqualTo(shifted);
    }
}
