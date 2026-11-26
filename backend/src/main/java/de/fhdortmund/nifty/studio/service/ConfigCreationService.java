package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.audit.service.AuditActorRole;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.repository.AssessmentConfigEntity;
import de.fhdortmund.nifty.studio.repository.AssessmentConfigRepository;
import de.fhdortmund.nifty.studio.repository.CoreQuestionEntity;
import de.fhdortmund.nifty.studio.repository.CoreQuestionRepository;
import de.fhdortmund.nifty.studio.repository.SkillEntity;
import de.fhdortmund.nifty.studio.repository.SkillRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQ-IS-01, REQ-IS-12, Decision 2.21: one-shot immutable assessment creation with per-professor
 * idempotency replay and single {@code CONFIG_CREATED} audit row.
 */
@Service
@Transactional(readOnly = true)
public class ConfigCreationService {

    private final AssessmentConfigRepository configRepository;
    private final SkillRepository skillRepository;
    private final CoreQuestionRepository questionRepository;
    private final ConfigValidator validator;
    private final AuthContextService authContext;
    private final AuditService auditService;
    private final TransactionTemplate tx;

    ConfigCreationService(
            AssessmentConfigRepository configRepository,
            SkillRepository skillRepository,
            CoreQuestionRepository questionRepository,
            ConfigValidator validator,
            AuthContextService authContext,
            AuditService auditService,
            TransactionTemplate tx) {
        this.tx = tx;
        this.configRepository = configRepository;
        this.skillRepository = skillRepository;
        this.questionRepository = questionRepository;
        this.validator = validator;
        this.authContext = authContext;
        this.auditService = auditService;
    }

    // Not transactional itself: a lost race fails at commit, and the replay needs a fresh transaction.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreatedAssessment create(UUID idempotencyKey, CreateAssessmentRequest request) {
        if (idempotencyKey == null) {
            throw new FieldValidationException(List.of(
                    new FieldErrorDetail("Idempotency-Key", "must be a valid UUID header", "REQUIRED")));
        }

        UUID professorId = authContext.currentProfessorId();
        try {
            return tx.execute(status -> createOnce(professorId, idempotencyKey, request));
        } catch (DataIntegrityViolationException ex) {
            // Decision 2.21, REQ-IS-14: a parallel create with the same key won the unique constraint. Replay it.
            // Any other violation finds no winner and is rethrown.
            return tx.execute(status -> replayExisting(professorId, idempotencyKey).orElseThrow(() -> ex));
        }
    }

    /** REQ-IS-12: replays the professor's own create, answers 400 for another professor's key. */
    private Optional<CreatedAssessment> replayExisting(UUID professorId, UUID idempotencyKey) {
        Optional<AssessmentConfigEntity> existing = configRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        AssessmentConfigEntity found = existing.get();
        if (found.getProfessorId().equals(professorId)) {
            return Optional.of(new CreatedAssessment(found.getId(), false));
        }
        throw new DomainException(HttpStatus.BAD_REQUEST, "Idempotency-Key is not available.");
    }

    private CreatedAssessment createOnce(UUID professorId, UUID idempotencyKey, CreateAssessmentRequest request) {
        Optional<CreatedAssessment> replay = replayExisting(professorId, idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }

        validator.validateOrThrow(request);

        SettingsInput s = request.settings();
        AssessmentConfigEntity config = configRepository.save(new AssessmentConfigEntity(
                professorId,
                s.title().trim(),
                s.vision().trim(),
                s.minCoverage(),
                s.maxAttempts(),
                false, // Decision 2.32: column kept, setting removed
                Boolean.TRUE.equals(s.bfi10Enabled()),
                s.teamSize(),
                s.groupingMode().name(),
                s.softPreset().name(),
                s.combinedHardWeight(),
                s.alpha(),
                s.constraintWeights() == null ? Map.of() : s.constraintWeights(),
                idempotencyKey));

        Map<String, SkillEntity> skillsByKey = persistSkills(config, request.skills());
        persistQuestions(config, request.questions(), skillsByKey);

        auditService.record(
                professorId.toString(),
                AuditActorRole.PROFESSOR,
                "ASSESSMENT_CONFIG",
                config.getId(),
                "CONFIG_CREATED",
                null,
                "v" + config.getVersion(),
                null);

        return new CreatedAssessment(config.getId(), true);
    }

    private Map<String, SkillEntity> persistSkills(AssessmentConfigEntity config, List<SkillInput> skills) {
        Map<String, SkillEntity> saved = new LinkedHashMap<>();
        if (skills == null) {
            return saved;
        }
        for (int i = 0; i < skills.size(); i++) {
            SkillInput input = skills.get(i);
            List<String> trimmedRubric = new ArrayList<>(SkillRules.RUBRIC_LEVEL_COUNT);
            for (int level = 0; level < input.rubricLevels().size(); level++) {
                trimmedRubric.add(input.rubricLevels().get(level).trim());
            }
            SkillEntity entity = skillRepository.save(new SkillEntity(config, input.name().trim(), trimmedRubric));
            saved.put(SkillRules.normalizeSkillName(input.name()), entity);
        }
        return saved;
    }

    private void persistQuestions(
            AssessmentConfigEntity config,
            List<QuestionInput> questions,
            Map<String, SkillEntity> skillsByKey) {
        if (questions == null) {
            return;
        }
        for (int i = 0; i < questions.size(); i++) {
            QuestionInput q = questions.get(i);
            List<SkillEntity> targets = resolveSkills(q.targetSkillNames(), skillsByKey);
            questionRepository.save(new CoreQuestionEntity(
                    config,
                    q.position(),
                    q.text().trim(),
                    q.weight(),
                    q.confidenceThreshold(),
                    q.minTimeSec(),
                    q.maxTimeSec(),
                    q.followUpLimit(),
                    targets));
        }
    }

    private static List<SkillEntity> resolveSkills(
            List<String> names, Map<String, SkillEntity> skillsByKey) {
        List<SkillEntity> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < names.size(); i++) {
            SkillEntity skill = skillsByKey.get(SkillRules.normalizeSkillName(names.get(i)));
            if (skill != null && seen.add(skill.getId())) {
                result.add(skill);
            }
        }
        return result;
    }
}
