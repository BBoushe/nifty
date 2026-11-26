package de.fhdortmund.nifty.teams.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** One candidate (by invite) in one team of a proposal; unique per proposal (V8). */
@Entity
@Table(name = "team_member")
public class TeamMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "proposal_id", nullable = false, updatable = false)
    private UUID proposalId;

    @Column(name = "team_no", nullable = false)
    private int teamNo;

    @Column(name = "invite_id", nullable = false, updatable = false)
    private UUID inviteId;

    protected TeamMemberEntity() {
    }

    public TeamMemberEntity(UUID proposalId, int teamNo, UUID inviteId) {
        this.proposalId = proposalId;
        this.teamNo = teamNo;
        this.inviteId = inviteId;
    }

    public void moveTo(int newTeamNo) {
        this.teamNo = newTeamNo;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProposalId() {
        return proposalId;
    }

    public int getTeamNo() {
        return teamNo;
    }

    public UUID getInviteId() {
        return inviteId;
    }
}
