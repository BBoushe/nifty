package de.fhdortmund.nifty.context.service;

import de.fhdortmund.nifty.context.service.dto.SanitizationReport;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decision 2.24, REQ-CC-03: turns untrusted candidate text into safe prompt data. It strips spoofing
 * characters, escapes reserved delimiter tags and cuts the result at a ceiling. Plain loops only (coding standard #3).
 * The ledger keeps the raw text; only the copy that goes into a prompt changes.
 */
final class PromptSanitizer {

    // shortcut: one fixed ceiling for every answer, move it into config when the professor sets answer limits.
    static final int MAX_CHARS = 4000;

    // shortcut: fixed tag list. `user`, `human` and `tool` stay out so List<User> in a Java answer stays untouched.
    // A quote that spans an escaped tag fails word-for-word verification and is discarded (fail-safe).
    static final String RESERVED_TAGS =
            "candidate_answer|transcript_entry|system|assistant|instructions?|developer";
    // Possessive quantifiers keep the scan linear: "<" + 100k spaces must not backtrack (review round 2).
    // Full-width (U+FF1C) and small (U+FE64) less-than signs look like "<" to a model, so they are escaped too.
    private static final Pattern RESERVED_TAG_START = Pattern.compile(
            "[<\uFF1C\uFE64](?=\\s*+/?+\\s*+(?:" + RESERVED_TAGS + ")\\b|\\|)", Pattern.CASE_INSENSITIVE);

    static final SanitizationReport CLEAN = new SanitizationReport(false, List.of(), false);

    /** Text that is safe inside a prompt, plus what the checks found in the raw input. */
    record Sanitized(String text, SanitizationReport report) {
    }

    private PromptSanitizer() {
    }

    static Sanitized sanitize(String raw) {
        // Escape before the cut, so "&lt;" can never push the text over the ceiling. A cut never creates a tag.
        String escaped = RESERVED_TAG_START.matcher(stripSpoofing(raw)).replaceAll("&lt;");
        boolean truncated = escaped.length() > MAX_CHARS;
        String text = truncated ? escaped.substring(0, cutIndex(escaped)) : escaped;
        // Injection rules run on the full raw text, so padding past the ceiling cannot hide an attack.
        List<String> ruleIds = InjectionDetector.detect(raw);
        return new Sanitized(text, new SanitizationReport(!ruleIds.isEmpty(), ruleIds, truncated));
    }

    /** Removes control, format (bidi, zero-width, tag), lone surrogate and other invisible characters. Keeps tab, CR, LF. */
    static String stripSpoofing(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (!isSpoofing(cp)) {
                out.appendCodePoint(cp);
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

    static boolean isSpoofing(int cp) {
        if (cp == '\n' || cp == '\r' || cp == '\t') {
            return false;
        }
        int type = Character.getType(cp);
        if (type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE) {
            return true; // Cc, Cf (bidi, zero-width, soft hyphen, tag characters) and lone surrogates
        }
        // Invisible characters outside Cc/Cf: grapheme joiner, Hangul fillers, braille blank, variation selectors,
        // and the unassigned rest of the tag block.
        return cp == 0x034F || cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 || cp == 0x2800
                || (cp >= 0x180B && cp <= 0x180F) || (cp >= 0xFE00 && cp <= 0xFE0F)
                || (cp >= 0xE0000 && cp <= 0xE007F) || (cp >= 0xE0100 && cp <= 0xE01EF);
    }
}
