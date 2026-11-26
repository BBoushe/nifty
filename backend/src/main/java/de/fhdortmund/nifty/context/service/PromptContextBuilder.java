package de.fhdortmund.nifty.context.service;

import de.fhdortmund.nifty.context.service.dto.AgentRole;
import de.fhdortmund.nifty.context.service.dto.BuiltPromptContext;

/**
 * Decision 1.18, REQ-CC-09: one builder per agent role. A later fresh-context builder (UX-10 advice, team Mode B)
 * implements this and needs no change in the existing builders.
 */
interface PromptContextBuilder<I> {

    AgentRole role();

    BuiltPromptContext build(I input);
}
