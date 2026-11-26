package de.fhdortmund.nifty.evaluation.service;

import java.util.List;
import java.util.UUID;

public record CandidateProfilesView(
        List<CandidateHardSkillProfile> released, // releaseStatus AUTO_RELEASED | APPROVED | OVERRIDDEN only
        List<UUID> pendingInviteIds               // latest attempt queued, running, failed, PENDING or FLAGGED; no scores
) {
}
