package de.fhdortmund.nifty.assessment.service;

import de.fhdortmund.nifty.assessment.repository.AssessmentAttemptRepository;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decision 4.4 / REQ-AC-19: purges the attempts, ledger and BFI-10 rows of a deleted assessment. Synchronous and
 * MANDATORY, so it runs inside the deleting transaction that holds {@code SET LOCAL nifty.allow_purge = 'on'}.
 */
@Service
@Transactional(readOnly = true)
public class AssessmentCleanupListener {

    private final AssessmentAttemptRepository attempts;

    AssessmentCleanupListener(AssessmentAttemptRepository attempts) {
        this.attempts = attempts;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onAssessmentDeleted(AssessmentDeletedEvent event) {
        attempts.purgeByConfigId(event.configId());
    }
}
