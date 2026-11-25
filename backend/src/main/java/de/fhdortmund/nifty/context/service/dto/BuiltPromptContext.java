package de.fhdortmund.nifty.context.service.dto;

import java.util.List;

public record BuiltPromptContext(
        AgentRole agentRole,
        String templateVersion,
        String systemPrompt,
        List<PromptMessage> messages,
        String promptSha256,
        SanitizationReport sanitizationReport
) {
}
