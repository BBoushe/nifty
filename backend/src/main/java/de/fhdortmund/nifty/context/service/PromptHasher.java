package de.fhdortmund.nifty.context.service;

import de.fhdortmund.nifty.context.service.dto.PromptMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Decision 1.18, REQ-CC-07: SHA-256 fingerprint of the full prompt, so identical input gives an identical hash. */
final class PromptHasher {

    private static final byte SEPARATOR = 0; // the sanitizer strips NUL, so text cannot fake a part boundary

    private PromptHasher() {
    }

    static String sha256(String templateVersion, String systemPrompt, List<PromptMessage> messages) {
        MessageDigest digest = newDigest();
        update(digest, templateVersion);
        update(digest, systemPrompt);
        for (int i = 0; i < messages.size(); i++) {
            update(digest, messages.get(i).role());
            update(digest, messages.get(i).content());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String part) {
        digest.update(part.getBytes(StandardCharsets.UTF_8));
        digest.update(SEPARATOR);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM spec", e);
        }
    }
}
