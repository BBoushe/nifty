package de.fhdortmund.nifty.assessment.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Compile-only placeholder. assessment-chat deletes this class when the real bean lands. */
@Service
class AssessmentQueryServicePlaceholder implements AssessmentQueryService {

    private static final String MESSAGE = "AssessmentQueryService is not implemented yet: owner assessment-chat";

    @Override
    public ImmutableTranscriptView getTranscriptForAttempt(UUID attemptId) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public Optional<UUID> getLatestCompletedAttemptId(UUID configId, UUID inviteId) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public List<StudentSoftSkillProfile> getSoftSkillProfilesForConfig(UUID configId) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public Optional<StudentSoftSkillProfile> getSoftSkillProfileForAttempt(UUID attemptId) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
