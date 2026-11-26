package de.fhdortmund.nifty.teams.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import de.fhdortmund.nifty.teams.service.TeamCleanupListener;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** US-TB-01, REQ-TB-12, Decision 4.4: V8 applies, a candidate is placed once per proposal, deletion purges. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TeamCleanupListener.class)
class TeamSchemaAndCleanupTest extends PostgresContainerBase {

    @Autowired
    private TeamProposalRepository proposals;
    @Autowired
    private TeamMemberRepository members;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEventPublisher publisher;

    private TeamProposalEntity draft(UUID configId) {
        return proposals.save(new TeamProposalEntity(configId, 2, "COMBINED", new BigDecimal("0.5000"),
                new BigDecimal("0.6000"), 42L, Map.of("cost", 0.25), "prof-1"));
    }

    @Test
    void persistsProposalWithJsonbMetricsAndMembers() {
        TeamProposalEntity proposal = draft(UUID.randomUUID());
        UUID invite = UUID.randomUUID();
        members.saveAll(List.of(new TeamMemberEntity(proposal.getId(), 1, invite)));
        entityManager.flush();
        entityManager.clear();

        TeamProposalEntity loaded = proposals.findById(proposal.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo("DRAFT");
        assertThat(loaded.getCostMetrics()).containsEntry("cost", 0.25);
        assertThat(loaded.getSeed()).isEqualTo(42L);
        assertThat(members.findByProposalIdOrderByTeamNoAscInviteIdAsc(proposal.getId()))
                .extracting(TeamMemberEntity::getInviteId).containsExactly(invite);
    }

    @Test
    void rejectsSameCandidateTwiceInOneProposalButAllowsItInAnother() {
        TeamProposalEntity first = draft(UUID.randomUUID());
        TeamProposalEntity second = draft(first.getConfigId());
        UUID invite = UUID.randomUUID();
        members.saveAll(List.of(new TeamMemberEntity(second.getId(), 1, invite)));
        members.saveAll(List.of(new TeamMemberEntity(first.getId(), 1, invite)));
        entityManager.flush();

        members.saveAll(List.of(new TeamMemberEntity(first.getId(), 2, invite)));
        assertThatThrownBy(() -> entityManager.flush()).hasMessageContaining("team_member_proposal_id_invite_id_key");
    }

    @Test
    void assessmentDeletedEventPurgesProposalsAndMembersOfThatConfigOnly() {
        UUID configId = UUID.randomUUID();
        TeamProposalEntity a = draft(configId);
        TeamProposalEntity b = draft(configId);
        TeamProposalEntity other = draft(UUID.randomUUID());
        members.saveAll(List.of(
                new TeamMemberEntity(a.getId(), 1, UUID.randomUUID()),
                new TeamMemberEntity(b.getId(), 1, UUID.randomUUID()),
                new TeamMemberEntity(other.getId(), 1, UUID.randomUUID())));
        entityManager.flush();

        publisher.publishEvent(new AssessmentDeletedEvent(configId, "prof-1"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM team_proposal WHERE config_id = ?", Integer.class, configId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM team_member WHERE proposal_id IN (?, ?)",
                Integer.class, a.getId(), b.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM team_member WHERE proposal_id = ?",
                Integer.class, other.getId())).isEqualTo(1);
    }

    @Test
    void checkConstraintRejectsUnknownStatus() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO team_proposal (id, config_id, status, num_teams, grouping_mode, alpha,
                    combined_hard_weight, seed, cost_metrics, created_by)
                VALUES (?, ?, 'LIVE', 1, 'COMBINED', 0.5, 0.5, 1, '{}'::jsonb, 'p')""",
                UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }
}
