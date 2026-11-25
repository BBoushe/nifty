package de.fhdortmund.nifty.assessment.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImmutableTranscriptView(
        UUID attemptId,
        UUID configId,
        UUID inviteId,
        String configVersion,
        int attemptNo,
        Instant startedAt,
        Instant completedAt,
        List<TranscriptEntryView> entries
) {
}
