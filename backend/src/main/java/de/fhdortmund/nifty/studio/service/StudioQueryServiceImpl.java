package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.repository.AssessmentConfigEntity;
import de.fhdortmund.nifty.studio.repository.AssessmentConfigRepository;
import de.fhdortmund.nifty.studio.repository.CoreQuestionEntity;
import de.fhdortmund.nifty.studio.repository.CoreQuestionRepository;
import de.fhdortmund.nifty.studio.repository.SkillEntity;
import de.fhdortmund.nifty.studio.repository.SkillRepository;
import de.fhdortmund.nifty.studio.service.view.EvaluatorQuestion;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.InterviewerQuestion;
import de.fhdortmund.nifty.studio.service.view.InterviewerView;
import de.fhdortmund.nifty.studio.service.view.ScoringQuestion;
import de.fhdortmund.nifty.studio.service.view.ScoringView;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import de.fhdortmund.nifty.studio.service.view.TeamView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-IS-09 / Contract pack §3.4: narrow read-only hand-off views for downstream slices using
 * JOIN FETCH queries and zero Java Streams.
 */
@Service
@Transactional(readOnly = true)
class StudioQueryServiceImpl implements StudioQueryService {

    private static final String NOT_FOUND = "Assessment configuration not found.";

    private final AssessmentConfigRepository configRepository;
    private final SkillRepository skillRepository;
    private final CoreQuestionRepository questionRepository;

    StudioQueryServiceImpl(
            AssessmentConfigRepository configRepository,
            SkillRepository skillRepository,
            CoreQuestionRepository questionRepository) {
        this.configRepository = configRepository;
        this.skillRepository = skillRepository;
        this.questionRepository = questionRepository;
    }

    @Override
    public boolean exists(UUID configId) {
        return configId != null && configRepository.existsById(configId);
    }

    @Override
    public boolean ownedBy(UUID configId, UUID professorId) {
        return configId != null && professorId != null
                && configRepository.existsByIdAndProfessorId(configId, professorId);
    }

    @Override
    public InterviewerView interviewerView(UUID configId) {
        AssessmentConfigEntity config = loadConfig(configId);
        List<CoreQuestionEntity> entities = questionRepository.findWithSkillsByConfigIdOrderByPositionAsc(configId);
        List<InterviewerQuestion> questions = new ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            CoreQuestionEntity q = entities.get(i);
            List<SkillEntity> targetSkills = q.getSkills();
            List<UUID> skillIds = new ArrayList<>(targetSkills.size());
            List<String> skillNames = new ArrayList<>(targetSkills.size());
            for (int j = 0; j < targetSkills.size(); j++) {
                SkillEntity skill = targetSkills.get(j);
                skillIds.add(skill.getId());
                skillNames.add(skill.getName());
            }
            questions.add(new InterviewerQuestion(
                    q.getId(),
                    q.getPosition(),
                    q.getText(),
                    q.getMinTimeSec(),
                    q.getMaxTimeSec(),
                    q.getFollowUpLimit(),
                    List.copyOf(skillIds),
                    List.copyOf(skillNames)));
        }
        return new InterviewerView(
                config.getId(),
                "v" + config.getVersion(),
                config.getVision(),
                config.getMaxAttempts(),
                config.isBfi10Enabled(),
                GroupingMode.valueOf(config.getGroupingMode()),
                List.copyOf(questions));
    }

    @Override
    public EvaluatorView evaluatorView(UUID configId) {
        loadConfig(configId);
        List<SkillEntity> skillEntities = skillRepository.findByConfigIdOrderByNameAsc(configId);
        List<EvaluatorSkill> skills = new ArrayList<>(skillEntities.size());
        for (int i = 0; i < skillEntities.size(); i++) {
            SkillEntity s = skillEntities.get(i);
            skills.add(new EvaluatorSkill(s.getId(), s.getName(), List.copyOf(s.getRubricLevels())));
        }
        List<CoreQuestionEntity> questionEntities =
                questionRepository.findWithSkillsByConfigIdOrderByPositionAsc(configId);
        List<EvaluatorQuestion> questions = new ArrayList<>(questionEntities.size());
        for (int i = 0; i < questionEntities.size(); i++) {
            CoreQuestionEntity q = questionEntities.get(i);
            questions.add(new EvaluatorQuestion(q.getId(), q.getText(), extractSkillIds(q.getSkills())));
        }
        return new EvaluatorView(configId, List.copyOf(skills), List.copyOf(questions));
    }

    @Override
    public ScoringView scoringView(UUID configId) {
        AssessmentConfigEntity config = loadConfig(configId);
        List<CoreQuestionEntity> questionEntities =
                questionRepository.findWithSkillsByConfigIdOrderByPositionAsc(configId);
        List<ScoringQuestion> questions = new ArrayList<>(questionEntities.size());
        for (int i = 0; i < questionEntities.size(); i++) {
            CoreQuestionEntity q = questionEntities.get(i);
            questions.add(new ScoringQuestion(
                    q.getId(),
                    q.getWeight(),
                    q.getConfidenceThreshold(),
                    extractSkillIds(q.getSkills())));
        }
        return new ScoringView(configId, config.getMinCoverage(), List.copyOf(questions));
    }

    @Override
    public TeamView teamView(UUID configId) {
        AssessmentConfigEntity config = loadConfig(configId);
        return new TeamView(
                config.getId(),
                config.getTeamSize(),
                GroupingMode.valueOf(config.getGroupingMode()),
                SoftPreset.valueOf(config.getSoftPreset()),
                config.getCombinedHardWeight(),
                config.getAlpha(),
                Map.copyOf(config.getConstraintWeights()),
                config.isBfi10Enabled());
    }

    private AssessmentConfigEntity loadConfig(UUID configId) {
        if (configId == null) {
            throw new DomainException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return configRepository.findById(configId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private static List<UUID> extractSkillIds(List<SkillEntity> skills) {
        List<UUID> ids = new ArrayList<>(skills.size());
        for (int i = 0; i < skills.size(); i++) {
            ids.add(skills.get(i).getId());
        }
        return List.copyOf(ids);
    }
}
