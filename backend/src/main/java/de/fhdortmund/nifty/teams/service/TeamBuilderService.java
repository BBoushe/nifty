package de.fhdortmund.nifty.teams.service;

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
import de.fhdortmund.nifty.evaluation.service.ScoreStatus;
import de.fhdortmund.nifty.evaluation.service.SkillScoreView;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import de.fhdortmund.nifty.studio.service.view.TeamView;
import de.fhdortmund.nifty.teams.repository.TeamMemberEntity;
import de.fhdortmund.nifty.teams.repository.TeamMemberRepository;
import de.fhdortmund.nifty.teams.repository.TeamProposalEntity;
import de.fhdortmund.nifty.teams.repository.TeamProposalRepository;
import de.fhdortmund.nifty.teams.service.RecalculateTeamsRequest.Assignment;
import de.fhdortmund.nifty.teams.service.TeamObjectiveCalculator.Params;
import de.fhdortmund.nifty.teams.service.TeamObjectiveCalculator.TeamDiagnostics;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team generation, live recalculation and audited publish (REQ-TB-03, -04, -05, -08, -09, -10, -11). All numbers come
 * from {@link TeamObjectiveCalculator} and {@link SimulatedAnnealingTeamOptimizer}: this slice calls no LLM
 * (Decision 1.20). Only released scores of each candidate's latest attempt feed the teams (Decisions 2.10, 2.13).
 */
@Service
@Transactional(readOnly = true)
public class TeamBuilderService {

    static final String CONFIG_NOT_FOUND = "Assessment not found.";
    static final String PROPOSAL_NOT_FOUND = "Team proposal not found.";
    private static final String WEIGHT_PREFIX = "traitWeight.";

    private final StudioQueryService studio;
    private final EvaluationQueryService evaluation;
    private final AssessmentQueryService assessment;
    private final AuditService audit;
    private final AuthContextService authContext;
    private final InviteService invites;
    private final TeamProposalRepository proposals;
    private final TeamMemberRepository members;

    TeamBuilderService(StudioQueryService studio, EvaluationQueryService evaluation, AssessmentQueryService assessment,
            AuditService audit, AuthContextService authContext, InviteService invites,
            TeamProposalRepository proposals, TeamMemberRepository members) {
        this.studio = studio;
        this.evaluation = evaluation;
        this.assessment = assessment;
        this.audit = audit;
        this.authContext = authContext;
        this.invites = invites;
        this.proposals = proposals;
        this.members = members;
    }

    @Transactional
    public TeamProposalResponse generate(UUID configId, GenerateTeamsRequest request) {
        UUID professorId = requireOwnedConfig(configId);
        Upstream upstream = loadUpstream(configId);
        List<UUID> inviteIds = new ArrayList<>(upstream.released.keySet());
        Collections.sort(inviteIds); // seed determinism must not depend on the order of the upstream service
        GroupingMode mode = request.groupingMode() != null ? request.groupingMode() : upstream.view.groupingMode();
        double alpha = request.alpha() != null ? request.alpha() : upstream.view.alpha().doubleValue();
        double hardWeight = request.combinedHardWeight() != null
                ? request.combinedHardWeight() : upstream.view.combinedHardWeight().doubleValue();
        int numTeams = request.numTeams() != null ? request.numTeams()
                : Math.max(1, (int) Math.ceil((double) inviteIds.size() / upstream.view.teamSize()));
        double[] weights = validate(request, upstream.view, mode, numTeams, inviteIds.size());
        long seed = request.seed() != null ? request.seed() : randomSeed();

        Cohort cohort = Cohort.of(inviteIds, upstream);
        Params params = new Params(mode, upstream.view.bfi10Enabled(), hardWeight, alpha, weights);
        TeamObjectiveCalculator calculator = new TeamObjectiveCalculator(
                cohort.values, cohort.has, cohort.hardCount, numTeams, params);
        int[] teamOf = SimulatedAnnealingTeamOptimizer.optimize(calculator, inviteIds.size(), numTeams, seed);

        TeamDiagnostics diagnostics = calculator.diagnostics(teamOf);
        TeamProposalEntity proposal = proposals.save(new TeamProposalEntity(configId, numTeams, mode.name(),
                unit(alpha), unit(hardWeight), seed, metrics(diagnostics.imbalance(), diagnostics.diversity(), diagnostics.cost(),
                weights), professorId.toString()));
        List<TeamMemberEntity> rows = new ArrayList<>(inviteIds.size());
        for (int i = 0; i < inviteIds.size(); i++) {
            rows.add(new TeamMemberEntity(proposal.getId(), teamOf[i] + 1, inviteIds.get(i)));
        }
        members.saveAll(rows);
        return respond(proposal, rows, upstream);
    }

    @Transactional
    public TeamProposalResponse recalculate(UUID proposalId, RecalculateTeamsRequest request) {
        TeamProposalEntity proposal = requireOwnedProposal(proposalId);
        requireDraft(proposal);
        List<TeamMemberEntity> rows = members.findByProposalIdOrderByTeamNoAscInviteIdAsc(proposalId);
        validatePlacement(request.assignments(), rows, proposal.getNumTeams());
        Map<UUID, Integer> teamNos = new HashMap<>();
        for (Assignment assignment : request.assignments()) {
            teamNos.put(assignment.inviteId(), assignment.teamNo());
        }
        for (TeamMemberEntity row : rows) {
            row.moveTo(teamNos.get(row.getInviteId()));
        }
        TeamProposalResponse response = respond(proposal, rows, loadUpstream(proposal.getConfigId()));
        // Only generate and recalculate store cost_metrics; reads and publish leave the stored snapshot alone.
        TeamProposalResponse.Diagnostics d = response.diagnostics();
        proposal.updateCostMetrics(metrics(d.imbalance(), d.diversity(), d.cost(), weightsOf(proposal.getCostMetrics())));
        return response;
    }

    @Transactional
    public TeamProposalResponse publish(UUID proposalId) {
        TeamProposalEntity proposal = requireOwnedProposal(proposalId);
        requireDraft(proposal);
        proposal.publish();
        // REQ-TB-04: one audit row per publish; it joins this transaction.
        audit.record(authContext.currentProfessorId().toString(), AuditActorRole.PROFESSOR, "TEAM_PROPOSAL",
                proposalId, "TEAMS_PUBLISHED", TeamProposalEntity.DRAFT, TeamProposalEntity.PUBLISHED, null);
        return respond(proposal, members.findByProposalIdOrderByTeamNoAscInviteIdAsc(proposalId),
                loadUpstream(proposal.getConfigId()));
    }

    public List<TeamProposalResponse> list(UUID configId) {
        requireOwnedConfig(configId);
        List<TeamProposalEntity> found = proposals.findByConfigIdOrderByCreatedAtDesc(configId);
        List<TeamProposalResponse> responses = new ArrayList<>(found.size());
        if (found.isEmpty()) {
            return responses;
        }
        List<UUID> ids = new ArrayList<>(found.size());
        for (TeamProposalEntity proposal : found) {
            ids.add(proposal.getId());
        }
        Map<UUID, List<TeamMemberEntity>> byProposal = new HashMap<>();
        for (TeamMemberEntity row : members.findByProposalIdIn(ids)) {
            byProposal.computeIfAbsent(row.getProposalId(), key -> new ArrayList<>()).add(row);
        }
        Upstream upstream = loadUpstream(configId);
        for (TeamProposalEntity proposal : found) {
            responses.add(respond(proposal, byProposal.getOrDefault(proposal.getId(), List.of()), upstream));
        }
        return responses;
    }

    // ---- validation (REQ-TB-08) ----

    /** Checks the data-dependent rules; range rules of the request fields are bean validation. Returns the weights. */
    private double[] validate(GenerateTeamsRequest request, TeamView view, GroupingMode mode, int numTeams,
            int candidateCount) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (numTeams < 1 || numTeams > candidateCount) {
            errors.add(new FieldErrorDetail("numTeams",
                    "must be within 1 and the number of released candidates (" + candidateCount + ")", "OUT_OF_RANGE"));
        }
        if (mode != GroupingMode.HARD_SKILLS && !view.bfi10Enabled()) {
            // Decision 2.27: no COMBINED fallback; the grouping mode needs BFI-10.
            errors.add(new FieldErrorDetail("groupingMode", mode + " requires BFI-10, which is off for this assessment",
                    "BFI10_REQUIRED"));
        }
        double[] weights = null;
        if (request.traitWeights() != null) {
            weights = customWeights(request.traitWeights(), errors);
        } else if (mode != GroupingMode.HARD_SKILLS && view.bfi10Enabled()) {
            weights = TeamObjectiveCalculator.traitWeights(view.softPreset(), view.constraintWeights());
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return weights != null ? weights : TeamObjectiveCalculator.traitWeights(SoftPreset.EQUAL_WEIGHTS, Map.of());
    }

    private static double[] customWeights(Map<String, Double> given, List<FieldErrorDetail> errors) {
        Set<String> known = new HashSet<>(Arrays.asList(TeamObjectiveCalculator.TRAITS));
        double sum = 0;
        boolean valid = true;
        for (Map.Entry<String, Double> entry : given.entrySet()) {
            Double weight = entry.getValue();
            if (!known.contains(entry.getKey()) || weight == null || weight < 0) {
                valid = false;
            } else {
                sum += weight;
            }
        }
        if (!valid || sum <= 0) {
            errors.add(new FieldErrorDetail("traitWeights",
                    "keys must be the five Big Five names, weights must not be negative and must not all be zero",
                    "BAD_WEIGHTS"));
            return null;
        }
        return TeamObjectiveCalculator.traitWeights(SoftPreset.CUSTOM, given);
    }

    private static void validatePlacement(List<Assignment> assignments, List<TeamMemberEntity> rows, int numTeams) {
        Set<UUID> expected = new HashSet<>();
        for (TeamMemberEntity row : rows) {
            expected.add(row.getInviteId());
        }
        Set<UUID> seen = new HashSet<>();
        boolean valid = assignments.size() == rows.size();
        for (Assignment assignment : assignments) {
            valid &= expected.contains(assignment.inviteId()) && seen.add(assignment.inviteId())
                    && assignment.teamNo() <= numTeams;
        }
        if (!valid) {
            throw new FieldValidationException(List.of(new FieldErrorDetail("assignments",
                    "must place every candidate of the proposal exactly once in a team from 1 to " + numTeams,
                    "BAD_PLACEMENT")));
        }
    }

    // ---- loading ----

    private UUID requireOwnedConfig(UUID configId) {
        UUID professorId = authContext.currentProfessorId();
        if (configId == null || !studio.ownedBy(configId, professorId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, CONFIG_NOT_FOUND);
        }
        return professorId;
    }

    /** Loads the proposal under a row lock, so a publish and a recalculate or a second publish run one after the other. */
    private TeamProposalEntity requireOwnedProposal(UUID proposalId) {
        TeamProposalEntity proposal = proposals.findByIdForUpdate(proposalId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, PROPOSAL_NOT_FOUND));
        if (!studio.ownedBy(proposal.getConfigId(), authContext.currentProfessorId())) {
            throw new DomainException(HttpStatus.NOT_FOUND, PROPOSAL_NOT_FOUND);
        }
        return proposal;
    }

    private static void requireDraft(TeamProposalEntity proposal) {
        if (!TeamProposalEntity.DRAFT.equals(proposal.getStatus())) {
            throw new DomainException(HttpStatus.CONFLICT, "The team proposal is already published.");
        }
    }

    private Upstream loadUpstream(UUID configId) {
        TeamView view = studio.teamView(configId);
        CandidateProfilesView profiles = evaluation.getCandidateProfilesForConfig(configId);
        Map<UUID, CandidateHardSkillProfile> released = new LinkedHashMap<>();
        for (CandidateHardSkillProfile profile : profiles.released()) {
            released.put(profile.inviteId(), profile);
        }
        Map<UUID, StudentSoftSkillProfile> soft = new HashMap<>();
        if (view.bfi10Enabled()) { // Decision 4.3: BFI-10 means arrive reverse-scored; they are only read here.
            for (StudentSoftSkillProfile profile : assessment.getSoftSkillProfilesForConfig(configId)) {
                // Traits count only for the attempt whose hard scores were released; otherwise they are N/A.
                CandidateHardSkillProfile hard = released.get(profile.inviteId());
                if (hard != null && hard.attemptId() != null && hard.attemptId().equals(profile.attemptId())) {
                    soft.put(profile.inviteId(), profile);
                }
            }
        }
        Map<UUID, String> refs = new HashMap<>();
        for (InviteSummary invite : invites.listInvites(configId)) {
            refs.put(invite.inviteId(), invite.candidateRef());
        }
        return new Upstream(view, released, profiles.pendingInviteIds(), soft, refs);
    }

    // ---- response ----

    private TeamProposalResponse respond(TeamProposalEntity proposal, List<TeamMemberEntity> rows, Upstream upstream) {
        int teams = proposal.getNumTeams();
        Map<UUID, Integer> teamNos = new HashMap<>();
        for (TeamMemberEntity row : rows) {
            teamNos.put(row.getInviteId(), row.getTeamNo());
        }
        List<UUID> inviteIds = new ArrayList<>(teamNos.keySet());
        Collections.sort(inviteIds);
        Cohort cohort = Cohort.of(inviteIds, upstream);
        int[] teamOf = new int[inviteIds.size()];
        for (int i = 0; i < teamOf.length; i++) {
            teamOf[i] = teamNos.get(inviteIds.get(i)) - 1;
        }
        double[] weights = weightsOf(proposal.getCostMetrics());
        GroupingMode mode = GroupingMode.valueOf(proposal.getGroupingMode());
        TeamObjectiveCalculator calculator = new TeamObjectiveCalculator(cohort.values, cohort.has, cohort.hardCount,
                teams, new Params(mode, upstream.view.bfi10Enabled(), proposal.getCombinedHardWeight().doubleValue(),
                proposal.getAlpha().doubleValue(), weights));
        TeamDiagnostics diagnostics = calculator.diagnostics(teamOf);

        int naTotal = 0;
        List<TeamProposalResponse.TeamCard> cards = new ArrayList<>(teams);
        for (int g = 0; g < teams; g++) {
            for (int d = 0; d < cohort.hardCount; d++) {
                naTotal += diagnostics.naCounts()[g][d];
            }
            cards.add(card(g, teamOf, cohort, diagnostics, upstream));
        }
        return new TeamProposalResponse(proposal.getId(), proposal.getConfigId(), proposal.getStatus(), teams, mode,
                proposal.getAlpha().doubleValue(), proposal.getCombinedHardWeight().doubleValue(), proposal.getSeed(),
                proposal.getCreatedAt(), proposal.getPublishedAt(), new TeamProposalResponse.Diagnostics(
                        round4(diagnostics.imbalance()), round4(diagnostics.diversity()), round4(diagnostics.cost()),
                        naTotal), cards, upstream.pending);
    }

    private static TeamProposalResponse.TeamCard card(int g, int[] teamOf, Cohort cohort, TeamDiagnostics diagnostics,
            Upstream upstream) {
        List<TeamProposalResponse.TeamMember> teamMembers = new ArrayList<>();
        for (int i = 0; i < teamOf.length; i++) {
            if (teamOf[i] == g) {
                teamMembers.add(member(i, cohort, upstream));
            }
        }
        Map<String, Double> skillAverages = new LinkedHashMap<>();
        Map<String, Integer> naCounts = new LinkedHashMap<>();
        for (int d = 0; d < cohort.hardCount; d++) {
            skillAverages.put(cohort.skillNames.get(d), rawMean(diagnostics.teamMeans()[g][d], 0.0));
            naCounts.put(cohort.skillNames.get(d), diagnostics.naCounts()[g][d]);
        }
        Map<String, Double> traitAverages = new LinkedHashMap<>();
        if (upstream.view.bfi10Enabled()) {
            for (int t = 0; t < TeamObjectiveCalculator.TRAITS.length; t++) {
                traitAverages.put(TeamObjectiveCalculator.TRAITS[t],
                        rawMean(diagnostics.teamMeans()[g][cohort.hardCount + t], 1.0));
            }
        }
        return new TeamProposalResponse.TeamCard(g + 1, teamMembers.size(), teamMembers, skillAverages, naCounts,
                traitAverages);
    }

    private static TeamProposalResponse.TeamMember member(int i, Cohort cohort, Upstream upstream) {
        UUID inviteId = cohort.inviteIds.get(i);
        Map<String, Double> skills = new LinkedHashMap<>();
        for (int d = 0; d < cohort.hardCount; d++) {
            skills.put(cohort.skillNames.get(d), Double.isNaN(cohort.raw[i][d]) ? null : cohort.raw[i][d]);
        }
        Map<String, Double> traits = new LinkedHashMap<>();
        for (int t = 0; t < TeamObjectiveCalculator.TRAITS.length; t++) {
            double value = cohort.raw[i][cohort.hardCount + t];
            if (!Double.isNaN(value)) {
                traits.put(TeamObjectiveCalculator.TRAITS[t], value);
            }
        }
        return new TeamProposalResponse.TeamMember(inviteId, upstream.refs.getOrDefault(inviteId, inviteId.toString()),
                skills, traits);
    }

    /** Normalised 0..1 team mean back to the display scale; {@code NaN} (no scored member) becomes N/A = null. */
    private static Double rawMean(double normalised, double offset) {
        return Double.isNaN(normalised) ? null : round4(normalised * 4.0 + offset);
    }

    private static double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    /** The seed is shown in JSON, so it stays within JavaScript's safe integer range. */
    private static long randomSeed() {
        long max = GenerateTeamsRequest.MAX_SEED;
        return ThreadLocalRandom.current().nextLong(-max, max + 1);
    }

    private static BigDecimal unit(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    // The run-time trait weights are not a column of V8, so they travel in cost_metrics (Decision 1.20).
    private static Map<String, Double> metrics(double imbalance, double diversity, double cost, double[] weights) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("imbalance", imbalance);
        metrics.put("diversity", diversity);
        metrics.put("cost", cost);
        for (int t = 0; t < weights.length; t++) {
            metrics.put(WEIGHT_PREFIX + TeamObjectiveCalculator.TRAITS[t], weights[t]);
        }
        return metrics;
    }

    private static double[] weightsOf(Map<String, Double> metrics) {
        double[] weights = new double[TeamObjectiveCalculator.TRAITS.length];
        for (int t = 0; t < weights.length; t++) {
            Double weight = metrics.get(WEIGHT_PREFIX + TeamObjectiveCalculator.TRAITS[t]);
            weights[t] = weight == null ? 0.2 : weight;
        }
        return weights;
    }

    // ---- value holders ----

    /** What the other slices say right now. {@code released} holds only AUTO_RELEASED, APPROVED or OVERRIDDEN. */
    private record Upstream(TeamView view, Map<UUID, CandidateHardSkillProfile> released, List<UUID> pending,
            Map<UUID, StudentSoftSkillProfile> soft, Map<UUID, String> refs) {
    }

    /**
     * Primitive-array view of the candidates for the calculator: columns are the hard skills, then the five traits.
     * {@code raw} keeps the original scale ({@code NaN} = N/A); {@code values} is the 0..1 scale.
     */
    private static final class Cohort {
        final List<UUID> inviteIds;
        final List<String> skillNames = new ArrayList<>();
        int hardCount;
        double[][] values;
        boolean[][] has;
        double[][] raw;

        private Cohort(List<UUID> inviteIds) {
            this.inviteIds = inviteIds;
        }

        static Cohort of(List<UUID> inviteIds, Upstream upstream) {
            Cohort cohort = new Cohort(inviteIds);
            // Columns sorted by skillId: the order of the upstream skill list must not change the teams for one seed.
            Map<UUID, String> skills = new TreeMap<>();
            for (UUID inviteId : inviteIds) {
                CandidateHardSkillProfile profile = upstream.released.get(inviteId);
                if (profile == null) {
                    continue; // lost its release since the proposal was made: all N/A, never rejected
                }
                for (SkillScoreView skill : profile.skillScores()) {
                    skills.putIfAbsent(skill.skillId(), skill.skillName());
                }
            }
            Map<UUID, Integer> columns = new HashMap<>();
            Map<String, Integer> nameCount = new HashMap<>();
            for (String name : skills.values()) {
                nameCount.merge(name, 1, Integer::sum);
            }
            for (Map.Entry<UUID, String> skill : skills.entrySet()) {
                columns.put(skill.getKey(), cohort.skillNames.size());
                // Two skills with one name keep separate map entries: the key gets a short skillId suffix.
                String name = skill.getValue();
                cohort.skillNames.add(nameCount.get(name) > 1 ? name + " [" + skill.getKey().toString().substring(0, 8) + "]" : name);
            }
            cohort.hardCount = cohort.skillNames.size();
            int dims = cohort.hardCount + TeamObjectiveCalculator.TRAITS.length;
            int n = inviteIds.size();
            cohort.values = new double[n][dims];
            cohort.has = new boolean[n][dims];
            cohort.raw = new double[n][dims];
            for (int i = 0; i < n; i++) {
                Arrays.fill(cohort.raw[i], Double.NaN);
                cohort.fillSkills(i, upstream.released.get(inviteIds.get(i)), columns);
                cohort.fillTraits(i, upstream.soft.get(inviteIds.get(i)));
            }
            return cohort;
        }

        private void fillSkills(int i, CandidateHardSkillProfile profile, Map<UUID, Integer> columns) {
            if (profile == null) {
                return;
            }
            for (SkillScoreView skill : profile.skillScores()) {
                // N/A is simply not marked as present: it is never read as 0 (REQ-TB-11).
                if (skill.status() == ScoreStatus.SCORED && skill.score() != null) {
                    set(i, columns.get(skill.skillId()), skill.score().doubleValue(),
                            TeamObjectiveCalculator.normalizeSkill(skill.score().doubleValue()));
                }
            }
        }

        private void fillTraits(int i, StudentSoftSkillProfile profile) {
            if (profile == null || profile.softSkillScores() == null) {
                return;
            }
            for (int t = 0; t < TeamObjectiveCalculator.TRAITS.length; t++) {
                Double mean = profile.softSkillScores().get(TeamObjectiveCalculator.TRAITS[t]);
                if (mean != null) {
                    set(i, hardCount + t, mean, TeamObjectiveCalculator.normalizeTrait(mean));
                }
            }
        }

        private void set(int i, int column, double rawValue, double normalised) {
            raw[i][column] = rawValue;
            values[i][column] = normalised;
            has[i][column] = true;
        }
    }
}
