package de.fhdortmund.nifty.evaluation.service;

import java.util.UUID;
import org.springframework.stereotype.Service;

/** Compile-only placeholder. evaluation-service deletes this class when the real bean lands. */
@Service
class EvaluationQueryServicePlaceholder implements EvaluationQueryService {

    @Override
    public CandidateProfilesView getCandidateProfilesForConfig(UUID configId) {
        throw new UnsupportedOperationException(
                "EvaluationQueryService is not implemented yet: owner evaluation-service");
    }
}
