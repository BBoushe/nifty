package de.fhdortmund.nifty.teams.service;

import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import de.fhdortmund.nifty.teams.repository.TeamProposalRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** REQ-TB-12: purge all proposals (members cascade) of a deleted assessment in the deleting transaction. */
@Component
public class TeamCleanupListener {

    private final TeamProposalRepository proposals;

    TeamCleanupListener(TeamProposalRepository proposals) {
        this.proposals = proposals;
    }

    // Decision 4.4: synchronous MANDATORY listener, so the purge joins the deletion transaction.
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onAssessmentDeleted(AssessmentDeletedEvent event) {
        proposals.deleteByConfigId(event.configId());
    }
}
