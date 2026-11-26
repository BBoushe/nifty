package de.fhdortmund.nifty.context.web;

import de.fhdortmund.nifty.context.service.PromptTemplates;
import de.fhdortmund.nifty.context.service.dto.BuiltPromptContext;
import de.fhdortmund.nifty.context.service.dto.PromptMessage;

public record ContextPreviewResponse(
        String templateVersion,
        String systemPrompt,
        String promptSha256,
        boolean rubricLeakCheckPassed
) {

    static ContextPreviewResponse from(BuiltPromptContext context) {
        boolean blind = PromptTemplates.isRubricBlind(context.systemPrompt());
        for (int i = 0; blind && i < context.messages().size(); i++) {
            PromptMessage message = context.messages().get(i);
            blind = PromptTemplates.isRubricBlind(message.content());
        }
        return new ContextPreviewResponse(context.templateVersion(), context.systemPrompt(), context.promptSha256(),
                blind);
    }
}
