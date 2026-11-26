package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.repository.AssessmentConfigEntity;
import de.fhdortmund.nifty.studio.repository.AssessmentConfigRepository;
import de.fhdortmund.nifty.studio.repository.CoreQuestionEntity;
import de.fhdortmund.nifty.studio.repository.CoreQuestionRepository;
import de.fhdortmund.nifty.studio.repository.SkillEntity;
import de.fhdortmund.nifty.studio.repository.SkillRepository;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** REQ-IS-10: professor-scoped read operations returning own assessments newest first and 404 otherwise. */
@Service
@Transactional(readOnly = true)
public class AssessmentReadService {

    private static final String NOT_FOUND = "Assessment configuration not found.";

    private final AssessmentConfigRepository configRepository;
    private final SkillRepository skillRepository;
    private final CoreQuestionRepository questionRepository;
    private final AuthContextService authContext;

    AssessmentReadService(
            AssessmentConfigRepository configRepository,
            SkillRepository skillRepository,
            CoreQuestionRepository questionRepository,
            AuthContextService authContext) {
        this.configRepository = configRepository;
        this.skillRepository = skillRepository;
        this.questionRepository = questionRepository;
        this.authContext = authContext;
    }

    public List<AssessmentSummaryResponse> listForCurrentProfessor() {
        UUID professorId = authContext.currentProfessorId();
        List<AssessmentConfigEntity> configs = configRepository.findByProfessorIdOrderByCreatedAtDesc(professorId);
        List<AssessmentSummaryResponse> summaries = new ArrayList<>(configs.size());
        for (int i = 0; i < configs.size(); i++) {
            AssessmentConfigEntity c = configs.get(i);
            int skillCount = Math.toIntExact(skillRepository.countByConfigId(c.getId()));
            int questionCount = Math.toIntExact(questionRepository.countByConfigId(c.getId()));
            summaries.add(new AssessmentSummaryResponse(
                    c.getId(),
                    c.getTitle(),
                    GroupingMode.valueOf(c.getGroupingMode()),
                    c.isBfi10Enabled(),
                    c.getTeamSize(),
                    skillCount,
                    questionCount,
                    c.getStatus(),
                    c.getVersion(),
                    c.getCreatedAt()));
        }
        return List.copyOf(summaries);
    }

    public AssessmentDetailResponse getForCurrentProfessor(UUID configId) {
        UUID professorId = authContext.currentProfessorId();
        AssessmentConfigEntity config = configRepository.findByIdAndProfessorId(configId, professorId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, NOT_FOUND));

        List<SkillEntity> skillEntities = skillRepository.findByConfigIdOrderByNameAsc(configId);
        List<AssessmentDetailResponse.SkillDetail> skills = new ArrayList<>(skillEntities.size());
        for (int i = 0; i < skillEntities.size(); i++) {
            SkillEntity s = skillEntities.get(i);
            skills.add(new AssessmentDetailResponse.SkillDetail(
                    s.getId(), s.getName(), List.copyOf(s.getRubricLevels())));
        }

        List<CoreQuestionEntity> questionEntities =
                questionRepository.findWithSkillsByConfigIdOrderByPositionAsc(configId);
        List<AssessmentDetailResponse.QuestionDetail> questions = new ArrayList<>(questionEntities.size());
        for (int i = 0; i < questionEntities.size(); i++) {
            CoreQuestionEntity q = questionEntities.get(i);
            List<SkillEntity> targetSkills = q.getSkills();
            List<UUID> skillIds = new ArrayList<>(targetSkills.size());
            List<String> skillNames = new ArrayList<>(targetSkills.size());
            for (int j = 0; j < targetSkills.size(); j++) {
                SkillEntity target = targetSkills.get(j);
                skillIds.add(target.getId());
                skillNames.add(target.getName());
            }
            questions.add(new AssessmentDetailResponse.QuestionDetail(
                    q.getId(),
                    q.getPosition(),
                    q.getText(),
                    q.getWeight(),
                    q.getConfidenceThreshold(),
                    q.getMinTimeSec(),
                    q.getMaxTimeSec(),
                    q.getFollowUpLimit(),
                    List.copyOf(skillIds),
                    List.copyOf(skillNames)));
        }

        return new AssessmentDetailResponse(
                config.getId(),
                config.getTitle(),
                config.getVision(),
                config.getMinCoverage(),
                config.getMaxAttempts(),
                config.isInventedQuestions(),
                config.isBfi10Enabled(),
                config.getTeamSize(),
                GroupingMode.valueOf(config.getGroupingMode()),
                SoftPreset.valueOf(config.getSoftPreset()),
                config.getCombinedHardWeight(),
                config.getAlpha(),
                Map.copyOf(config.getConstraintWeights()),
                config.getStatus(),
                config.getVersion(),
                config.getCreatedAt(),
                List.copyOf(skills),
                List.copyOf(questions));
    }
}
