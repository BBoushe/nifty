package de.fhdortmund.nifty.context.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decision 2.24, REQ-CC-04: deterministic prompt-injection rules, no LLM call. A match only flags the answer for
 * professor review; it never blocks the candidate. Plain loops only (coding standard #3).
 */
final class InjectionDetector {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    // Review round 2: every quantifier is possessive (++, *+) or bounded ({0,4}+), so the scan stays linear and
    // never recurses deeply on 100k-character input (no ReDoS, no StackOverflowError).
    // Rules need an imperative shape aimed at the system, so technical sentences such as "Spring Security ignores
    // CSRF", "Use @Disabled to skip the test" or "the maximum score path" stay clean (Decision 2.10 review cost).
    private static final String QUALIFIER = "(?:system|hidden|initial|original|secret)\\s++";
    private static final String[] RULE_IDS = {
        "ROLE_OVERRIDE", "TEST_SKIP", "GRADE_MANIPULATION", "PROMPT_EXFILTRATION", "DELIMITER_BREAKOUT"};
    private static final Pattern[] RULES = {
        compile("\\b(?:ignore|disregard|forget)\\s++(?:(?:all|any|the|your|every)\\s++){0,4}+"
                + "(?:previous|prior|above|earlier|preceding)\\s++(?:instructions?|rules|prompts?|messages?)\\b"
                + "|\\byou\\s++are\\s++now\\s++(?:a|an|the|my)\\b|\\bpretend\\s++(?:to\\s++be|you\\s++are|you're)\\b"
                + "|\\bnew\\s++instructions\\s*+:"
                + "|\\bact\\s++as\\s++(?:an?\\s++)?(?:different|new|unrestricted)\\s++"
                + "(?:assistant|ai|model|bot|persona|person|character|interviewer|examiner|professor)\\b"),
        compile("\\b(?:skip|end)\\s++(?:this\\s++(?:test|question|assessment|interview)|the\\s++(?:assessment|interview))\\b"
                + "|\\bskip\\s++the\\s++questions?\\b"
                + "|\\b(?:mark|consider)\\s++(?:this|the)\\s++(?:test|interview|assessment|question)\\s++(?:as\\s++)?"
                + "(?:complete|completed|done|finished|passed)\\b"),
        // "score" alone stays out ("the best score wins" is technical); "grade" and an explicit "4/4" stay in.
        compile("\\b(?:give|grant|award|assign|rate)\\s++me\\s++(?:the\\s++|a\\s++)?"
                + "(?:best|highest|top|full|maximum|perfect|level\\s*+\\d|\\d)"
                + "|\\b(?:give|grant|award|assign|rate)\\s++(?:me\\s++)?(?:a\\s++|the\\s++)?\\d\\s*+/\\s*+\\d"
                + "|\\b(?:deserves?|should\\s++get)\\s++(?:a\\s++|the\\s++)?(?:(?:best|highest|top|full|maximum|perfect)"
                + "\\s++(?:grade|score|marks?)|level\\s*+\\d|\\d\\s*+/\\s*+\\d)"
                + "|\\b(?:perfect|full)\\s++(?:grade|score|marks?)\\b|\\b(?:best|highest|top|maximum)\\s++grades?\\b"),
        // "print the rules" stays clean (a linter does that); "show me the prompt" or "reveal the rules" is aimed at us.
        compile("\\b(?:reveal|show|print|repeat|output|leak|display|tell)\\s++(?:me\\s++)?"
                + "(?:your\\s++(?:" + QUALIFIER + "){0,3}+(?:prompt|instructions|rubric|rules|answer\\s++key)"
                + "|the\\s++(?:(?:" + QUALIFIER + "){1,3}+(?:prompt|instructions|rules|rubric)|rubric|answer\\s++key))\\b"
                + "|\\b(?:show|tell|give)\\s++me\\s++the\\s++(?:prompt|instructions|rules)\\b"
                + "|\\b(?:reveal|repeat|leak)\\s++the\\s++(?:prompt|instructions|rules)\\b"
                + "|\\bwhat\\s++(?:is|are)\\s++your\\s++(?:(?:system|hidden|initial)\\s++){0,3}+"
                + "(?:prompt|instructions|rubric)\\b"),
        // NFKC already folds full-width letters and "＜"; the look-alike brackets (‹, 〈, ⟨, ᐸ) survive NFKC.
        compile("[" + PromptSanitizer.TAG_OPENERS + "]\\s*+/?+\\s*+(?:" + PromptSanitizer.RESERVED_TAGS + ")\\b"
                + "|<\\|[a-z_]++\\|>|\\[/?inst\\]"),
    };

    private InjectionDetector() {
    }

    /** Matched rule IDs in fixed rule order. Empty means clean. */
    static List<String> detect(String raw) {
        List<String> ids = new ArrayList<>();
        String text = normalize(raw);
        for (int i = 0; i < RULES.length; i++) {
            if (RULES[i].matcher(text).find()) {
                ids.add(RULE_IDS[i]);
            }
        }
        return ids;
    }

    /**
     * Folds obfuscation before matching: any Unicode space becomes ' ' (so NBSP or U+000B cannot split a phrase),
     * invisible characters go (so "ig<U+034F>nore" matches), and NFKC maps full-width letters and "＜" to ASCII.
     * shortcut: no homoglyph folding (Cyrillic "і" still evades), add a confusables map if attacks show up.
     */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                out.append(' ');
            } else if (!PromptSanitizer.isSpoofing(cp)) {
                out.appendCodePoint(cp);
            }
        }
        return Normalizer.normalize(out, Normalizer.Form.NFKC);
    }

    private static Pattern compile(String regex) {
        return Pattern.compile(regex, FLAGS);
    }
}
