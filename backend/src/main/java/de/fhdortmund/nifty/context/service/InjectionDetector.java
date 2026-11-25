package de.fhdortmund.nifty.context.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decision 2.24, REQ-CC-04: deterministic prompt-injection rules, no LLM call. A match only flags the answer for
 * professor review; it never blocks the candidate. Plain loops only (coding standard #3).
 */
final class InjectionDetector {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    // Parallel arrays in report order. Rules need an imperative shape ("ignore previous instructions") so a
    // technical sentence such as "Spring Security ignores CSRF" stays clean.
    private static final String[] RULE_IDS = {
        "ROLE_OVERRIDE", "TEST_SKIP", "GRADE_MANIPULATION", "PROMPT_EXFILTRATION", "DELIMITER_BREAKOUT"};
    private static final Pattern[] RULES = {
        compile("(?:ignore|disregard|forget)\\s+(?:(?:all|any|the|your|every)\\s+)*(?:previous|prior|above|earlier|preceding)\\s+"
                + "(?:instructions?|rules|prompts?|messages?)\\b"
                + "|\\byou\\s+are\\s+now\\s+(?:a|an|the|my)\\b|\\bpretend\\s+(?:to\\s+be|you\\s+are|you're)\\b"
                + "|\\bnew\\s+instructions\\s*:|\\bact\\s+as\\s+(?:a\\s+)?(?:different|new)\\b"),
        compile("\\bskip\\s+(?:this|the)\\s+(?:test|question|assessment|interview)\\b"
                + "|\\bend\\s+(?:this|the)\\s+(?:test|interview|assessment)\\b"
                + "|\\b(?:mark|consider)\\s+(?:this|the)\\s+(?:test|interview|assessment|question)\\s+(?:as\\s+)?"
                + "(?:complete|completed|done|finished|passed)\\b"),
        compile("\\b(?:give|grant|award|assign)\\s+me\\s+(?:the\\s+|a\\s+)?(?:best|highest|top|full|maximum|perfect|"
                + "level\\s*\\d|\\d)"
                + "|\\b(?:best|highest|perfect|maximum|top)\\s+(?:grade|score|mark)\\b|\\b4\\s*/\\s*4\\b"),
        compile("\\b(?:reveal|show|print|repeat|output|leak|display|tell)\\s+(?:me\\s+)?(?:your|the)\\s+"
                + "(?:(?:system|hidden|initial|original|secret)\\s+)*(?:prompt|instructions|rubric|rules|answer\\s+key)\\b"
                + "|\\bwhat\\s+(?:is|are)\\s+your\\s+(?:(?:system|hidden|initial)\\s+)*(?:prompt|instructions|rubric)\\b"),
        compile("<\\s*/?\\s*(?:" + PromptSanitizer.RESERVED_TAGS + ")\\b|<\\|[a-z_]+\\|>|\\[/?inst\\]"),
    };

    private InjectionDetector() {
    }

    /** Matched rule IDs in fixed rule order. Empty means clean. */
    static List<String> detect(String raw) {
        List<String> ids = new ArrayList<>();
        // Strip invisible characters first, so "ig<ZWSP>nore previous instructions" still matches.
        String text = PromptSanitizer.stripSpoofing(raw);
        for (int i = 0; i < RULES.length; i++) {
            if (RULES[i].matcher(text).find()) {
                ids.add(RULE_IDS[i]);
            }
        }
        return ids;
    }

    private static Pattern compile(String regex) {
        return Pattern.compile(regex, FLAGS);
    }
}
