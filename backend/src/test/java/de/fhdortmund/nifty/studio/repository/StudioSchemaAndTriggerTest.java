package de.fhdortmund.nifty.studio.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.PostgresContainerBase;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQ-IS-06 / REQ-IS-08 / Decision 2.21 / Decision 4.4: verifies V3__studio.sql CHECK constraints,
 * JPA persistence in studio/repository, trigger rejection on UPDATE/unflagged DELETE, and purge under
 * SET LOCAL nifty.allow_purge = 'on'.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class StudioSchemaAndTriggerTest extends PostgresContainerBase {

    private static final List<String> FIVE_RUBRIC_LEVELS = List.of("None", "Basic", "Developing", "Proficient", "Expert");

    @Autowired
    private AssessmentConfigRepository configRepository;
    @Autowired
    private SkillRepository skillRepository;
    @Autowired
    private CoreQuestionRepository questionRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void persistsConfigSkillsAndQuestionsWithJsonbAndJoinTable() {
        UUID professorId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        AssessmentConfigEntity config = configRepository.save(new AssessmentConfigEntity(
                professorId,
                "SE Block Week",
                "Evaluate backend craft",
                new BigDecimal("0.7500"),
                2,
                true,
                true,
                4,
                "COMBINED",
                "ENGINEERING_DEFAULT",
                new BigDecimal("0.6000"),
                new BigDecimal("0.5000"),
                Map.of("balance", new BigDecimal("1.0000")),
                idempotencyKey));
        SkillEntity skill = skillRepository.save(new SkillEntity(config, "Java", FIVE_RUBRIC_LEVELS));
        CoreQuestionEntity question = questionRepository.save(new CoreQuestionEntity(
                config,
                1,
                "Explain transactions.",
                new BigDecimal("2.00"),
                new BigDecimal("0.7000"),
                30,
                180,
                2,
                List.of(skill)));
        entityManager.flush();
        entityManager.clear();

        AssessmentConfigEntity reloaded = configRepository.findById(config.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("PUBLISHED");
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(reloaded.getConstraintWeights()).containsEntry("balance", new BigDecimal("1.0000"));
        assertThat(reloaded.getCreatedAt()).isNotNull();

        List<SkillEntity> skills = skillRepository.findByConfigIdOrderByNameAsc(config.getId());
        assertThat(skills).hasSize(1);
        assertThat(skills.getFirst().getRubricLevels()).containsExactlyElementsOf(FIVE_RUBRIC_LEVELS);

        List<CoreQuestionEntity> questions = questionRepository.findWithSkillsByConfigIdOrderByPositionAsc(config.getId());
        assertThat(questions).hasSize(1);
        assertThat(questions.getFirst().getId()).isEqualTo(question.getId());
        assertThat(questions.getFirst().getSkills()).extracting(SkillEntity::getId).containsExactly(skill.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseChecksRejectOutOfRangeCoverageBadRubricLengthBadFollowUpAndInvertedTimes() {
        UUID configId = insertValidConfig();
        try {
            UUID skillId = insertValidSkill(configId, "SQL");

            // min_coverage > 1
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO assessment_config (id, professor_id, title, vision, min_coverage, team_size, "
                            + "grouping_mode, soft_preset, combined_hard_weight, alpha, idempotency_key) "
                            + "VALUES (?, ?, 't', 'v', 1.5000, 3, 'HARD_SKILLS', 'EQUAL_WEIGHTS', 0.5000, 0.5000, ?)",
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(DataAccessException.class);

            // rubric_levels length != 5
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO skill (id, config_id, name, rubric_levels) VALUES (?, ?, 'Git', '[\"a\",\"b\",\"c\"]'::jsonb)",
                    UUID.randomUUID(), configId))
                    .isInstanceOf(DataAccessException.class);

            // follow_up_limit = 4 (> 3)
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO core_question (id, config_id, position, text, weight, confidence_threshold, "
                            + "min_time_sec, max_time_sec, follow_up_limit) VALUES (?, ?, 1, 'q', 1.00, 0.5000, 30, 120, 4)",
                    UUID.randomUUID(), configId))
                    .isInstanceOf(DataAccessException.class);

            // min_time_sec >= max_time_sec
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO core_question (id, config_id, position, text, weight, confidence_threshold, "
                            + "min_time_sec, max_time_sec, follow_up_limit) VALUES (?, ?, 2, 'q', 1.00, 0.5000, 120, 60, 1)",
                    UUID.randomUUID(), configId))
                    .isInstanceOf(DataAccessException.class);

            assertThat(skillId).isNotNull();
        } finally {
            purgeConfig(configId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void triggersBlockUpdateAndUnflaggedDeleteButAllowPurgeWhenFlagged() {
        UUID configId = insertValidConfig();
        UUID skillId = insertValidSkill(configId, "Testing");
        UUID questionId = insertValidQuestion(configId, 1);
        jdbc.update("INSERT INTO core_question_skill (core_question_id, skill_id) VALUES (?, ?)", questionId, skillId);

        // UPDATE blocked on core_question (REQ-IS-08)
        assertThatThrownBy(() -> jdbc.update("UPDATE core_question SET text = 'tampered' WHERE id = ?", questionId))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT text FROM core_question WHERE id = ?", String.class, questionId))
                .isEqualTo("Original question");

        // Unflagged DELETE blocked on assessment_config, skill, core_question, core_question_skill
        assertThatThrownBy(() -> jdbc.update("DELETE FROM core_question_skill WHERE core_question_id = ?", questionId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM core_question WHERE id = ?", questionId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM skill WHERE id = ?", skillId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM assessment_config WHERE id = ?", configId))
                .isInstanceOf(DataAccessException.class);

        // Flagged purge succeeds in a transaction (Decision 4.4)
        purgeConfig(configId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM assessment_config WHERE id = ?", Long.class, configId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM skill WHERE config_id = ?", Long.class, configId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core_question WHERE config_id = ?", Long.class, configId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core_question_skill WHERE core_question_id = ?",
                Long.class, questionId)).isZero();
    }

    private void purgeConfig(UUID configId) {
        transactionTemplate.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL nifty.allow_purge = 'on'");
            jdbc.update("DELETE FROM assessment_config WHERE id = ?", configId);
        });
    }

    private UUID insertValidConfig() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO assessment_config (id, professor_id, title, vision, min_coverage, team_size, "
                        + "grouping_mode, soft_preset, combined_hard_weight, alpha, idempotency_key) "
                        + "VALUES (?, ?, 'Title', 'Vision', 0.5000, 3, 'HARD_SKILLS', 'EQUAL_WEIGHTS', 0.5000, 0.5000, ?)",
                id, UUID.randomUUID(), UUID.randomUUID());
        return id;
    }

    private UUID insertValidSkill(UUID configId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO skill (id, config_id, name, rubric_levels) "
                        + "VALUES (?, ?, ?, '[\"0\",\"1\",\"2\",\"3\",\"4\"]'::jsonb)",
                id, configId, name);
        return id;
    }

    private UUID insertValidQuestion(UUID configId, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO core_question (id, config_id, position, text, weight, confidence_threshold, "
                        + "min_time_sec, max_time_sec, follow_up_limit) "
                        + "VALUES (?, ?, ?, 'Original question', 1.00, 0.6000, 30, 120, 2)",
                id, configId, position);
        return id;
    }
}
