package de.fhdortmund.nifty.context.service;

import de.fhdortmund.nifty.context.service.dto.SanitizationReport;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decision 2.24, REQ-CC-03: turns untrusted candidate text into safe prompt data. It strips spoofing
 * characters, escapes reserved delimiter tags and cuts the result at a ceiling. Plain loops only (coding standard #3).
 * The ledger keeps the raw text; only the copy that goes into a prompt changes.
 */
final class PromptSanitizer {

    // Decision 2.26: equals the assessment API answer limit (REQ-AC-20), so the ledger never holds text the models
    // cannot see. The cut stays as defence in depth. Change both together.
    static final int MAX_CHARS = 4000;

    // shortcut: fixed tag list. `user`, `human` and `tool` stay out so List<User> in a Java answer stays untouched.
    // A quote that spans an escaped tag fails word-for-word verification and is discarded (fail-safe).
    static final String RESERVED_TAGS =
            "candidate_answer|transcript_entry|system|assistant|instructions?|developer";
    // Signs a model may read as "<": ASCII, full-width U+FF1C, small U+FE64, single angle quote U+2039,
    // CJK angle bracket U+3008, angle brackets U+2329 and U+27E8, Canadian syllabics PA U+1438.
    static final String TAG_OPENERS = "<＜﹤‹〈〈⟨ᐸ";
    // Unicode-aware word end: since JDK 19, \b is ASCII-only and never ends a full-width word such as "ｓｙｓｔｅｍ".
    private static final String WORD_END = "(?![\\p{L}\\p{N}_＿])";
    // Possessive quantifiers keep the scan linear: "<" + 100k spaces must not backtrack (review round 2).
    // [\s\p{Z}] also covers NBSP, em space and other Unicode spaces that Java's ASCII \s misses.
    private static final Pattern RESERVED_TAG_START = Pattern.compile(
            "[" + TAG_OPENERS + "](?=[\\s\\p{Z}]*+[/／]?+[\\s\\p{Z}]*+(?:" + widen(RESERVED_TAGS) + ")" + WORD_END
                    + "|[|｜])",
            Pattern.CASE_INSENSITIVE);

    static final SanitizationReport CLEAN = new SanitizationReport(false, List.of(), false);

    /** Text that is safe inside a prompt, plus what the checks found in the raw input. */
    record Sanitized(String text, SanitizationReport report) {
    }

    private PromptSanitizer() {
    }

    static Sanitized sanitize(String raw) {
        // Escape before the cut, so "&lt;" can never push the text over the ceiling.
        String escaped = RESERVED_TAG_START.matcher(stripSpoofing(raw)).replaceAll("&lt;");
        boolean truncated = escaped.length() > MAX_CHARS;
        String text = truncated ? cutWithoutTag(escaped) : escaped;
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

    /**
     * Cuts at the ceiling without splitting a surrogate pair. A cut can turn "<systemx" into a real "<system" at the
     * end, so any tag start that is still live after the cut sits in the tail: cut again just before it.
     */
    private static String cutWithoutTag(String escaped) {
        String text = escaped.substring(0, Character.isHighSurrogate(escaped.charAt(MAX_CHARS - 1)) ? MAX_CHARS - 1
                : MAX_CHARS);
        Matcher live = RESERVED_TAG_START.matcher(text);
        while (live.find()) {
            text = text.substring(0, live.start()); // a BMP "<" sign, so no surrogate pair splits here
            live = RESERVED_TAG_START.matcher(text);
        }
        return text;
    }

    /** Lets each ASCII letter and "_" of a tag alternation also match its full-width form ("ｓｙｓｔｅｍ"). */
    static String widen(String alternation) {
        StringBuilder sb = new StringBuilder(alternation.length() * 6);
        for (int i = 0; i < alternation.length(); i++) {
            char c = alternation.charAt(i);
            char lower = Character.toLowerCase(c);
            if (lower >= 'a' && lower <= 'z') {
                sb.append('[').append(lower).append(Character.toUpperCase(lower))
                        .append((char) ('ａ' + (lower - 'a'))).append((char) ('Ａ' + (lower - 'a'))).append(']');
            } else if (c == '_') {
                sb.append("[_＿]");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
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
