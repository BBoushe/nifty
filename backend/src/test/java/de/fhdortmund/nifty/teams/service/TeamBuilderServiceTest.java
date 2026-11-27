package de.fhdortmund.nifty.teams.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.assessment.service.StudentSoftSkillProfile;
import de.fhdortmund.nifty.audit.service.AuditActorRole;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.auth.service.InviteService;
import de.fhdortmund.nifty.auth.service.InviteSummary;
import de.fhdortmund.nifty.evaluation.service.CandidateHardSkillProfile;
import de.fhdortmund.nifty.evaluation.service.CandidateProfilesView;
import de.fhdortmund.nifty.evaluation.service.EvaluationQueryService;
import de.fhdortmund.nifty.evaluation.service.ReleaseStatus;
import de.fhdortmund.nifty.evaluation.service.ScoreStatus;
import de.fhdortmund.nifty.evaluation.service.SkillScoreView;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import de.fhdortmund.nifty.studio.service.view.TeamView;
import de.fhdortmund.nifty.teams.repository.TeamProposalRepository;
import de.fhdortmund.nifty.teams.service.RecalculateTeamsRequest.Assignment;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * US-TB-04, REQ-TB-03, REQ-TB-04, REQ-TB-05, REQ-TB-08, REQ-TB-11: the cross-slice contracts are mocked, the
 * repositories are real (PostgreSQL Testcontainers). No LLM exists in this slice, so recalculation has none.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TeamBuilderService.class)
class TeamBuilderServiceTest extends PostgresContainerBase {

    private static final UUID PROFESSOR = UUID.randomUUID();

    @Autowired
    private TeamBuilderService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private TeamProposalRepository proposalRepository;
    @MockitoBean
    private StudioQueryService studio;
    @MockitoBean
    private EvaluationQueryService evaluation;
    @MockitoBean
    private AssessmentQueryService assessment;
    @MockitoBean
    private AuditService audit;
    @MockitoBean
    private AuthContextService authContext;
    @MockitoBean
    private InviteService inviteService;

    private static final Map<String, Double> TRAITS = Map.of("conscientiousness", 3.0, "agreeableness", 3.0,
            "neuroticism", 4.0, "extraversion", 2.0, "openness", 3.0);
    private static final long MAX_SAFE_SEED = 9_007_199_254_740_991L;

    private final UUID configId = UUID.randomUUID();
    private final UUID gitSkill = UUID.randomUUID();
    private final UUID dockerSkill = UUID.randomUUID();
    private final List<UUID> invites = new ArrayList<>();

    @BeforeEach
    void ownedConfig() {
        when(authContext.currentProfessorId()).thenReturn(PROFESSOR);
        when(studio.ownedBy(configId, PROFESSOR)).thenReturn(true);
        when(studio.teamView(configId)).thenReturn(view(GroupingMode.HARD_SKILLS, false));
    }

    private TeamView view(GroupingMode mode, boolean bfi10) {
        return new TeamView(configId, 3, mode, SoftPreset.ENGINEERING_DEFAULT, new BigDecimal("0.6000"),
                new BigDecimal("0.5000"), Map.of(), bfi10);
    }

    private static SkillScoreView scored(UUID skill, String name, double score) {
        return new SkillScoreView(skill, name, ScoreStatus.SCORED, BigDecimal.valueOf(score));
    }

    private static SkillScoreView notAssessed(UUID skill, String name) {
        return new SkillScoreView(skill, name, ScoreStatus.NOT_ASSESSED, null);
    }

    /** Releases {@code count} candidates; candidate i has git = i % 5 and docker = (i + 2) % 5 (candidate 0: docker N/A). */
    private List<CandidateHardSkillProfile> release(int count) {
        List<CandidateHardSkillProfile> profiles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID invite = UUID.randomUUID();
            invites.add(invite);
            SkillScoreView docker = i == 0 ? notAssessed(dockerSkill, "docker")
                    : scored(dockerSkill, "docker", (i + 2) % 5);
            profiles.add(new CandidateHardSkillProfile(invite, UUID.randomUUID(), ReleaseStatus.APPROVED,
                    new BigDecimal("2.00"), new BigDecimal("1.0000"),
                    List.of(scored(gitSkill, "git", i % 5), docker)));
        }
        return profiles;
    }

    private void evaluationReturns(List<CandidateHardSkillProfile> released, List<UUID> pending) {
        when(evaluation.getCandidateProfilesForConfig(configId)).thenReturn(new CandidateProfilesView(released, pending));
    }

    private int count(String table) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static Set<UUID> memberIds(TeamProposalResponse response) {
        Set<UUID> ids = new HashSet<>();
        response.teams().forEach(team -> team.members().forEach(member -> ids.add(member.inviteId())));
        return ids;
    }

    @Test
    void generateUsesOnlyReleasedCandidatesSavesDraftAndListsPendingAsWaiting() {
        UUID pending = UUID.randomUUID();
        evaluationReturns(release(7), List.of(pending));

        TeamProposalResponse response = service.generate(configId, GenerateTeamsRequest.defaults());

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.numTeams()).isEqualTo(3); // ceil(7 / teamSize 3), REQ-TB-05
        assertThat(response.teams()).extracting(TeamProposalResponse.TeamCard::size).containsExactlyInAnyOrder(3, 2, 2);
        assertThat(memberIds(response)).containsExactlyInAnyOrderElementsOf(invites).doesNotContain(pending);
        assertThat(response.pendingInviteIds()).containsExactly(pending);
        assertThat(count("team_proposal")).isEqualTo(1);
        assertThat(count("team_member")).isEqualTo(7);
        verifyNoInteractions(assessment); // BFI-10 off: no soft profile read
    }

    @Test
    void generateKeepsCandidateWithNaSkillReportsNaCountAndNeverCountsItAsZero() {
        evaluationReturns(release(6), List.of());

        TeamProposalResponse response = service.generate(configId,
                new GenerateTeamsRequest(2, null, null, null, 1L, null));

        assertThat(response.diagnostics().naCount()).isEqualTo(1);
        TeamProposalResponse.TeamMember naMember = response.teams().stream()
                .flatMap(team -> team.members().stream()).filter(m -> m.inviteId().equals(invites.get(0)))
                .findFirst().orElseThrow();
        assertThat(naMember.skillScores()).containsEntry("docker", null).containsEntry("git", 0.0);
        TeamProposalResponse.TeamCard naTeam = response.teams().stream()
                .filter(team -> team.members().contains(naMember)).findFirst().orElseThrow();
        assertThat(naTeam.skillNaCounts()).containsEntry("docker", 1);
    }

    @Test
    void sameSeedGivesTheSameTeams() {
        evaluationReturns(release(12), List.of());
        GenerateTeamsRequest request = new GenerateTeamsRequest(4, null, null, null, 42L, null);

        TeamProposalResponse first = service.generate(configId, request);
        TeamProposalResponse second = service.generate(configId, request);

        assertThat(second.teams()).usingRecursiveComparison().isEqualTo(first.teams());
        assertThat(second.diagnostics()).isEqualTo(first.diagnostics());
        assertThat(first.seed()).isEqualTo(42L);
    }

    @Test
    void generateRejectsTooManyTeamsAndPersistsNothing() {
        evaluationReturns(release(12), List.of());

        assertThatThrownBy(() -> service.generate(configId, new GenerateTeamsRequest(20, null, null, null, 1L, null)))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        ex -> assertThat(ex.getErrors()).extracting("field").containsExactly("numTeams"));
        assertThat(count("team_proposal")).isZero();
    }

    @Test
    void generateRejectsSoftAndCombinedModesWhenBfi10IsOffWithoutFallback() {
        evaluationReturns(release(6), List.of());

        for (GroupingMode mode : List.of(GroupingMode.SOFT_SKILLS, GroupingMode.COMBINED)) {
            assertThatThrownBy(() -> service.generate(configId, new GenerateTeamsRequest(null, mode, null, null, 1L, null)))
                    .isInstanceOfSatisfying(FieldValidationException.class,
                            ex -> assertThat(ex.getErrors()).extracting("field").containsExactly("groupingMode"));
        }
        assertThat(count("team_proposal")).isZero();
    }

    @Test
    void generateRejectsNegativeAllZeroOrUnknownTraitWeights() {
        when(studio.teamView(configId)).thenReturn(view(GroupingMode.COMBINED, true));
        evaluationReturns(release(6), List.of());
        when(assessment.getSoftSkillProfilesForConfig(configId)).thenReturn(List.of());

        for (Map<String, Double> weights : List.of(
                Map.of("conscientiousness", -1.0, "agreeableness", 2.0),
                Map.of("conscientiousness", 0.0, "agreeableness", 0.0),
                Map.of("sparkle", 1.0))) {
            assertThatThrownBy(() -> service.generate(configId, new GenerateTeamsRequest(null, null, null, null, 1L, weights)))
                    .isInstanceOfSatisfying(FieldValidationException.class,
                            ex -> assertThat(ex.getErrors()).extracting("field").containsExactly("traitWeights"));
        }
        assertThat(count("team_proposal")).isZero();
    }

    @Test
    void combinedModeShowsTraitsUnchangedToTheProfessorAndBalancesThem() {
        when(studio.teamView(configId)).thenReturn(view(GroupingMode.COMBINED, true));
        List<CandidateHardSkillProfile> released = release(6);
        evaluationReturns(released, List.of());
        List<StudentSoftSkillProfile> soft = new ArrayList<>();
        for (CandidateHardSkillProfile profile : released) {
            soft.add(new StudentSoftSkillProfile(profile.inviteId(), profile.attemptId(), TRAITS));
        }
        when(assessment.getSoftSkillProfilesForConfig(configId)).thenReturn(soft);

        TeamProposalResponse response = service.generate(configId,
                new GenerateTeamsRequest(2, null, null, null, 3L, null));

        TeamProposalResponse.TeamMember member = response.teams().get(0).members().get(0);
        assertThat(member.traits()).containsEntry("neuroticism", 4.0).containsEntry("extraversion", 2.0); // Decision 4.3
        assertThat(response.teams().get(0).traitAverages()).containsEntry("neuroticism", 4.0);
    }

    @Test
    void anotherProfessorsConfigIsNotFound() {
        when(studio.ownedBy(configId, PROFESSOR)).thenReturn(false);

        assertThatThrownBy(() -> service.generate(configId, GenerateTeamsRequest.defaults()))
                .isInstanceOfSatisfying(DomainException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.list(configId)).isInstanceOf(DomainException.class);
        verify(evaluation, never()).getCandidateProfilesForConfig(any());
    }

    @Test
    void recalculateMovesAMemberUpdatesDiagnosticsAndRequiresEveryCandidateExactlyOnce() {
        TeamProposalResponse draft = generateDraft(6, 9L);
        List<Assignment> placement = placementOf(draft);
        UUID moved = placement.get(0).inviteId();
        int from = placement.get(0).teamNo();
        placement.set(0, new Assignment(moved, from == 1 ? 2 : 1));

        TeamProposalResponse response = service.recalculate(draft.proposalId(), new RecalculateTeamsRequest(placement));

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.teams().get(from == 1 ? 1 : 0).members())
                .extracting(TeamProposalResponse.TeamMember::inviteId).contains(moved);
        assertThat(response.teams()).extracting(TeamProposalResponse.TeamCard::size).doesNotContain(0);
        assertThat(response.teams().stream().mapToInt(TeamProposalResponse.TeamCard::size).sum()).isEqualTo(6);

        List<Assignment> missingOne = placement.subList(1, placement.size());
        assertThatThrownBy(() -> service.recalculate(draft.proposalId(), new RecalculateTeamsRequest(missingOne)))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        ex -> assertThat(ex.getErrors()).extracting("field").containsExactly("assignments"));
        List<Assignment> unknownTeam = new ArrayList<>(placement);
        unknownTeam.set(1, new Assignment(placement.get(1).inviteId(), 3));
        assertThatThrownBy(() -> service.recalculate(draft.proposalId(), new RecalculateTeamsRequest(unknownTeam)))
                .isInstanceOf(FieldValidationException.class);
        List<Assignment> duplicate = new ArrayList<>(placement);
        duplicate.set(1, placement.get(0));
        assertThatThrownBy(() -> service.recalculate(draft.proposalId(), new RecalculateTeamsRequest(duplicate)))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void publishFlipsDraftToPublishedAuditsOnceAndLocksTheProposal() {
        TeamProposalResponse draft = generateDraft(6, 9L);

        TeamProposalResponse published = service.publish(draft.proposalId());

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedAt()).isBefore(Instant.now().plusSeconds(5)).isNotNull();
        verify(proposalRepository).findByIdForUpdate(draft.proposalId()); // REQ-TB-04: publish locks the row
        verify(audit).record(eq(PROFESSOR.toString()), eq(AuditActorRole.PROFESSOR), eq("TEAM_PROPOSAL"),
                eq(draft.proposalId()), eq("TEAMS_PUBLISHED"), eq("DRAFT"), eq("PUBLISHED"), eq(null));
        assertThatThrownBy(() -> service.publish(draft.proposalId())).isInstanceOfSatisfying(DomainException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.recalculate(draft.proposalId(), new RecalculateTeamsRequest(
                List.of(new Assignment(invites.get(0), 1))))).isInstanceOfSatisfying(DomainException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void proposalOfAnotherProfessorIsNotFound() {
        evaluationReturns(release(4), List.of());
        TeamProposalResponse draft = service.generate(configId, new GenerateTeamsRequest(2, null, null, null, 1L, null));
        when(studio.ownedBy(configId, PROFESSOR)).thenReturn(false);

        assertThatThrownBy(() -> service.publish(draft.proposalId())).isInstanceOfSatisfying(DomainException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.publish(UUID.randomUUID())).isInstanceOf(DomainException.class);
    }

    @Test
    void listReturnsProposalsNewestFirstWithCandidateRefs() {
        evaluationReturns(release(4), List.of());
        when(inviteService.listInvites(configId)).thenReturn(List.of(
                new InviteSummary(invites.get(0), "cand_001", false, Instant.now())));
        TeamProposalResponse first = service.generate(configId, new GenerateTeamsRequest(2, null, null, null, 1L, null));
        TeamProposalResponse second = service.generate(configId, new GenerateTeamsRequest(2, null, null, null, 2L, null));

        List<TeamProposalResponse> listed = service.list(configId);

        assertThat(listed).extracting(TeamProposalResponse::proposalId).containsExactly(second.proposalId(), first.proposalId());
        assertThat(listed.get(0).teams().stream().flatMap(t -> t.members().stream())
                .filter(m -> m.inviteId().equals(invites.get(0))).findFirst().orElseThrow().candidateRef())
                .isEqualTo("cand_001");
    }

    private TeamProposalResponse generateDraft(int count, long seed) {
        evaluationReturns(release(count), List.of());
        return service.generate(configId, new GenerateTeamsRequest(2, null, null, null, seed, null));
    }

    private static List<Assignment> placementOf(TeamProposalResponse draft) {
        List<Assignment> placement = new ArrayList<>();
        for (TeamProposalResponse.TeamCard team : draft.teams()) {
            for (TeamProposalResponse.TeamMember member : team.members()) {
                placement.add(new Assignment(member.inviteId(), team.teamNo()));
            }
        }
        return placement;
    }

    private String costMetrics(UUID proposalId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT cost_metrics::text FROM team_proposal WHERE id = ?", String.class, proposalId);
    }

    private static List<CandidateHardSkillProfile> withSkills(List<CandidateHardSkillProfile> profiles,
            java.util.function.BiFunction<Integer, List<SkillScoreView>, List<SkillScoreView>> change) {
        List<CandidateHardSkillProfile> result = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i++) {
            CandidateHardSkillProfile p = profiles.get(i);
            result.add(new CandidateHardSkillProfile(p.inviteId(), p.attemptId(), p.releaseStatus(), p.fitScore(),
                    p.coverage(), change.apply(i, p.skillScores())));
        }
        return result;
    }

    @Test
    void generatedSeedFitsTheJavaScriptSafeIntegerRange() {
        evaluationReturns(release(4), List.of());
        for (int i = 0; i < 30; i++) {
            long seed = service.generate(configId, GenerateTeamsRequest.defaults()).seed();
            assertThat(Math.abs(seed)).isLessThanOrEqualTo(MAX_SAFE_SEED);
        }
    }

    @Test
    void skillColumnsAreSortedSoUpstreamOrderCannotChangeTheTeams() {
        List<CandidateHardSkillProfile> profiles = release(10);
        evaluationReturns(profiles, List.of());
        GenerateTeamsRequest request = new GenerateTeamsRequest(3, null, null, null, 5L, null);
        TeamProposalResponse first = service.generate(configId, request);

        evaluationReturns(withSkills(profiles, (i, skills) -> {
            List<SkillScoreView> reversed = new ArrayList<>(skills);
            java.util.Collections.reverse(reversed);
            return reversed;
        }), List.of());
        TeamProposalResponse second = service.generate(configId, request);

        assertThat(new ArrayList<>(second.teams().get(0).skillAverages().keySet()))
                .isEqualTo(new ArrayList<>(first.teams().get(0).skillAverages().keySet()));
        assertThat(second.teams()).usingRecursiveComparison().isEqualTo(first.teams());
        assertThat(second.diagnostics()).isEqualTo(first.diagnostics());
    }

    @Test
    void twoSkillsWithTheSameNameKeepSeparateEntries() {
        UUID other = UUID.randomUUID();
        evaluationReturns(withSkills(release(4), (i, skills) -> List.of(scored(gitSkill, "git", i),
                scored(other, "git", 4 - i))), List.of());

        TeamProposalResponse response = service.generate(configId, new GenerateTeamsRequest(2, null, null, null, 1L, null));

        TeamProposalResponse.TeamCard card = response.teams().get(0);
        assertThat(card.skillAverages()).hasSize(2);
        assertThat(card.skillNaCounts()).hasSize(2);
        assertThat(card.members().get(0).skillScores()).hasSize(2);
        assertThat(card.skillAverages().keySet()).allMatch(key -> key.startsWith("git"));
    }

    @Test
    void traitsOfAnAttemptOtherThanTheReleasedOneAreNotUsed() {
        when(studio.teamView(configId)).thenReturn(view(GroupingMode.COMBINED, true));
        List<CandidateHardSkillProfile> released = release(4);
        evaluationReturns(released, List.of());
        List<StudentSoftSkillProfile> soft = new ArrayList<>();
        soft.add(new StudentSoftSkillProfile(released.get(0).inviteId(), released.get(0).attemptId(), TRAITS));
        for (int i = 1; i < 4; i++) {
            soft.add(new StudentSoftSkillProfile(released.get(i).inviteId(), UUID.randomUUID(), TRAITS));
        }
        when(assessment.getSoftSkillProfilesForConfig(configId)).thenReturn(soft);

        TeamProposalResponse response = service.generate(configId, new GenerateTeamsRequest(2, null, null, null, 3L, null));

        for (TeamProposalResponse.TeamCard card : response.teams()) {
            for (TeamProposalResponse.TeamMember member : card.members()) {
                if (member.inviteId().equals(released.get(0).inviteId())) {
                    assertThat(member.traits()).containsEntry("neuroticism", 4.0);
                } else {
                    assertThat(member.traits()).isEmpty();
                }
            }
        }
    }

    @Test
    void recalculateStoresTheSameFullPrecisionDiagnosticsAsAFreshGenerate() { // REQ-TB-13
        TeamProposalResponse draft = generateDraft(7, 4L);
        String generated = costMetrics(draft.proposalId());

        TeamProposalResponse recalculated = service.recalculate(draft.proposalId(),
                new RecalculateTeamsRequest(placementOf(draft)));

        assertThat(costMetrics(draft.proposalId())).isEqualTo(generated);
        assertThat(recalculated.diagnostics()).isEqualTo(draft.diagnostics());
        verify(proposalRepository).findByIdForUpdate(draft.proposalId()); // REQ-TB-03: recalculate locks the row
    }

    @Test
    void onlyGenerateAndRecalculateWriteCostMetrics() {
        List<CandidateHardSkillProfile> profiles = release(6);
        evaluationReturns(profiles, List.of());
        TeamProposalResponse draft = service.generate(configId, new GenerateTeamsRequest(2, null, null, null, 9L, null));
        String stored = costMetrics(draft.proposalId());
        List<Assignment> placement = placementOf(draft);
        // The released scores change after generation: reads must show them but must not store them.
        evaluationReturns(withSkills(profiles, (i, skills) -> List.of(scored(gitSkill, "git", (i * i) % 5),
                scored(dockerSkill, "docker", (i + 2) % 5))), List.of());

        service.list(configId);
        assertThat(costMetrics(draft.proposalId())).isEqualTo(stored);

        service.recalculate(draft.proposalId(), new RecalculateTeamsRequest(placement));
        String afterRecalculate = costMetrics(draft.proposalId());
        assertThat(afterRecalculate).isNotEqualTo(stored);

        service.publish(draft.proposalId());
        assertThat(costMetrics(draft.proposalId())).isEqualTo(afterRecalculate);
    }
}
