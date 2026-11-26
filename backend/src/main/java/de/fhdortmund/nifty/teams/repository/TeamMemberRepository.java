package de.fhdortmund.nifty.teams.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Private to the {@code teams} slice (ArchUnit Decision 1.10). */
public interface TeamMemberRepository extends Repository<TeamMemberEntity, UUID> {

    <S extends TeamMemberEntity> List<S> saveAll(Iterable<S> members);

    List<TeamMemberEntity> findByProposalIdOrderByTeamNoAscInviteIdAsc(UUID proposalId);

    List<TeamMemberEntity> findByProposalIdIn(Collection<UUID> proposalIds);
}
