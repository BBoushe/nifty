package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreEntity;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-EV-01 / Decisions 1.18, 2.10, 2.13, REQ-TB-11:
 * Cross-slice read-only implementation of {@link EvaluationQueryService} for Team Builder.
 * Returns released latest attempts with explicit N/A scores and lists unreleased invites
 * separately with zero scores.
 */
@Service
@Transactional(readOnly = true)
class EvaluationQueryServiceImpl implements EvaluationQueryService {

    private final StudioQueryService studioQueryService;
    private final CandidateEvaluationSummaryRepository summaryRepository;
    private final SkillScoreRepository skillScoreRepository;

    EvaluationQueryServiceImpl(
            StudioQueryService studioQueryService,
            CandidateEvaluationSummaryRepository summaryRepository,
            SkillScoreRepository skillScoreRepository) {
        this.studioQueryService = studioQueryService;
        this.summaryRepository = summaryRepository;
        this.skillScoreRepository = skillScoreRepository;
    }

    @Override
    public CandidateProfilesView getCandidateProfilesForConfig(UUID configId) {
        EvaluatorView evaluatorView = studioQueryService.evaluatorView(configId);
        List<CandidateEvaluationSummaryEntity> latest = summaryRepository.findLatestCompletedPerInvite(configId);

        List<CandidateEvaluationSummaryEntity> releasedSummaries = new ArrayList<>();
        List<UUID> pendingInviteIds = new ArrayList<>();
        List<UUID> releasedSummaryIds = new ArrayList<>();

        for (int i = 0; i < latest.size(); i++) {
            CandidateEvaluationSummaryEntity summary = latest.get(i);
            if (FormulaRecalculationHelper.isReleased(summary.getReleaseStatus())) {
                releasedSummaries.add(summary);
                releasedSummaryIds.add(summary.getId());
            } else {
                pendingInviteIds.add(summary.getInviteId());
            }
        }

        Map<UUID, Map<UUID, SkillScoreEntity>> scoresBySummaryAndSkill = new HashMap<>();
        if (!releasedSummaryIds.isEmpty()) {
            List<SkillScoreEntity> allScores = skillScoreRepository.findBySummaryIdIn(releasedSummaryIds);
            for (int i = 0; i < allScores.size(); i++) {
                SkillScoreEntity sc = allScores.get(i);
                scoresBySummaryAndSkill
                        .computeIfAbsent(sc.getSummary().getId(), ignored -> new HashMap<>())
                        .put(sc.getSkillId(), sc);
            }
        }

        List<CandidateHardSkillProfile> releasedProfiles = new ArrayList<>(releasedSummaries.size());
        for (int i = 0; i < releasedSummaries.size(); i++) {
            CandidateEvaluationSummaryEntity summary = releasedSummaries.get(i);
            Map<UUID, SkillScoreEntity> scoreMap = scoresBySummaryAndSkill.getOrDefault(summary.getId(), Map.of());
            List<SkillScoreView> skillViews = new ArrayList<>(evaluatorView.skills().size());
            for (int s = 0; s < evaluatorView.skills().size(); s++) {
                EvaluatorSkill skill = evaluatorView.skills().get(s);
                SkillScoreEntity sc = scoreMap.get(skill.skillId());
                if (sc == null || ScoreStatus.NOT_ASSESSED.literal().equals(sc.getStatus())) {
                    skillViews.add(new SkillScoreView(skill.skillId(), skill.name(), ScoreStatus.NOT_ASSESSED, null));
                } else {
                    skillViews.add(new SkillScoreView(skill.skillId(), skill.name(), ScoreStatus.SCORED, sc.getScore()));
                }
            }
            releasedProfiles.add(new CandidateHardSkillProfile(
                    summary.getInviteId(),
                    summary.getAttemptId(),
                    FormulaRecalculationHelper.parseReleaseStatus(summary.getReleaseStatus()),
                    summary.getFitScore(),
                    summary.getCoverage(),
                    List.copyOf(skillViews)));
        }

        return new CandidateProfilesView(List.copyOf(releasedProfiles), List.copyOf(pendingInviteIds));
    }
}
