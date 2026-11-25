package de.fhdortmund.nifty.context.service;

import de.fhdortmund.nifty.context.service.dto.SanitizationReport;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decision 2.24, REQ-CC-03: turns untrusted candidate text into safe prompt data. It strips spoofing
 * characters, cuts the text at a ceiling and escapes reserved delimiter tags. Plain loops only (coding standard #3).
 * The ledger keeps the raw text; only the copy that goes into a prompt changes.
 */
final class PromptSanitizer {

    // shortcut: one fixed ceiling for every answer, move it into config when the professor sets answer limits.
    static final int MAX_CHARS = 4000;

    // shortcut: fixed tag list. `user`, `human` and `tool` stay out so List<User> in a Java answer stays untouched.
    // A quote that spans an escaped tag fails word-for-word verification and is discarded (fail-safe).
    static final String RESERVED_TAGS =
            "candidate_answer|transcript_entry|system|assistant|instructions?|developer";
    private static final Pattern RESERVED_TAG_START = Pattern.compile(
            "<(?=\\s*/?\\s*(?:" + RESERVED_TAGS + ")\\b|\\|)", Pattern.CASE_INSENSITIVE);

    static final SanitizationReport CLEAN = new SanitizationReport(false, List.of(), false);

    /** Text that is safe inside a prompt, plus what the checks found in the raw input. */
    record Sanitized(String text, SanitizationReport report) {
    }

    private PromptSanitizer() {
    }

    static Sanitized sanitize(String raw) {
        String stripped = stripSpoofing(raw);
        boolean truncated = stripped.length() > MAX_CHARS;
        String text = truncated ? stripped.substring(0, cutIndex(stripped)) : stripped;
        // Injection rules run on the full raw text, so padding past the ceiling cannot hide an attack.
        List<String> ruleIds = InjectionDetector.detect(raw);
        return new Sanitized(RESERVED_TAG_START.matcher(text).replaceAll("&lt;"),
                new SanitizationReport(!ruleIds.isEmpty(), ruleIds, truncated));
    }

    /** Removes control, bidi and zero-width characters. Keeps tab, CR and LF. */
    static String stripSpoofing(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!isSpoofing(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Union of rule IDs (first-seen order, no duplicates) and OR of the flags. */
    static SanitizationReport merge(SanitizationReport first, SanitizationReport second) {
        List<String> ids = new ArrayList<>(first.matchedRuleIds());
        for (int i = 0; i < second.matchedRuleIds().size(); i++) {
            String id = second.matchedRuleIds().get(i);
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return new SanitizationReport(first.injectionSuspected() || second.injectionSuspected(), ids,
                first.truncated() || second.truncated());
    }

    private static int cutIndex(String text) {
        return Character.isHighSurrogate(text.charAt(MAX_CHARS - 1)) ? MAX_CHARS - 1 : MAX_CHARS;
    }

    private static boolean isSpoofing(char c) {
        if (c < 0x20) {
            return c != '\n' && c != '\r' && c != '\t';
        }
        return (c >= 0x7F && c <= 0x9F) || c == 0x00AD || c == 0x061C || c == 0x180E
                || (c >= 0x200B && c <= 0x200F) || (c >= 0x202A && c <= 0x202E)
                || (c >= 0x2060 && c <= 0x2064) || (c >= 0x2066 && c <= 0x2069) || c == 0xFEFF;
    }
}
