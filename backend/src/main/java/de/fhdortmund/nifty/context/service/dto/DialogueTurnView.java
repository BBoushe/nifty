package de.fhdortmund.nifty.context.service.dto;

/**
 * String values: context must not import assessment (assessment calls context).
 * author: "interviewer" | "candidate"; origin: "core" | "follow_up" | "invented".
 */
public record DialogueTurnView(int seq, String author, String origin, String content) {
}
