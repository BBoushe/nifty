package de.fhdortmund.nifty.assessment.service;

import de.fhdortmund.nifty.shared.config.AttemptCompletedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cross-slice contract (00-parallel-waves-and-contracts.md §3.6). Owner: assessment-chat. */
public interface AssessmentQueryService {

    ImmutableTranscriptView getTranscriptForAttempt(UUID attemptId);           // zero BFI-10 data!

    Optional<UUID> getLatestCompletedAttemptId(UUID configId, UUID inviteId);

    List<StudentSoftSkillProfile> getSoftSkillProfilesForConfig(UUID configId); // latest completed attempt per invite

    Optional<StudentSoftSkillProfile> getSoftSkillProfileForAttempt(UUID attemptId);

    /** REQ-EP-13: completed attempts since {@code since}, as the event evaluation would have received. */
    List<AttemptCompletedEvent> completedAttemptsSince(Instant since);
}
