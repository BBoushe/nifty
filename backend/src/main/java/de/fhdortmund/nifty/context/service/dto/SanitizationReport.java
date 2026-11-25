package de.fhdortmund.nifty.context.service.dto;

import java.util.List;

public record SanitizationReport(boolean injectionSuspected, List<String> matchedRuleIds, boolean truncated) {
}
