package de.fhdortmund.nifty.evaluation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.evaluation.service.EvaluationCleanupListener;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * US-EV-01 / REQ-EV-02 / Decisions 1.19, 2.13, 4.4: verifies V7__evaluation.sql UUID schema,
 * chk_na_score_null constraint, multi-attempt coexistence via UNIQUE(attempt_id), and synchronous
 * GDPR purge across all 5 evaluation tables on AssessmentDeletedEvent.
 */
@DataJpaTest
@Import(EvaluationCleanupListener.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EvaluationSchemaAndCleanupTest extends PostgresContainerBase {

    @Autowired
    private FormulaVersionRepository formulaVersionRepository;
    @Autowired
    private EvaluationRunRepository evaluationRunRepository;
    @Autowired
    private CandidateEvaluationSummaryRepository summaryRepository;
    @Autowired
    private ResponseEvaluationRepository responseEvaluationRepository;
    @Autowired
    private SkillScoreRepository skillScoreRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void multipleAttemptsPerCandidateCoexistWithUniqueAttemptId() {
        UUID configId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID coreQuestionId = UUID.randomUUID();

        FormulaVersionEntity formula = formulaVersionRepository.save(new FormulaVersionEntity(
                configId,
                1,
                "WEIGHTED_MEAN",
                new BigDecimal("0.5000"),
                Map.of(coreQuestionId.toString(), new BigDecimal("2.00")),
                professorId));

        UUID attempt1Id = UUID.randomUUID();
        EvaluationRunEntity run1 = evaluationRunRepository.save(new EvaluationRunEntity(
                attempt1Id, configId, formula, "evaluator-judge-v1", "COMPLETED",
                attempt1Id + ":" + formula.getId()));
        CandidateEvaluationSummaryEntity summary1 = summaryRepository.save(new CandidateEvaluationSummaryEntity(
                attempt1Id, configId, inviteId, 1, formula, run1, "COMPLETED",
                new BigDecimal("2.50"), new BigDecimal("0.8000"), 0, "auto_released", Instant.now()));
        skillScoreRepository.save(new SkillScoreEntity(
                summary1, formula, skillId, "SCORED", new BigDecimal("2.50"),
                new BigDecimal("0.9000"), 2, "auto_released"));

        UUID attempt2Id = UUID.randomUUID();
        EvaluationRunEntity run2 = evaluationRunRepository.save(new EvaluationRunEntity(
                attempt2Id, configId, formula, "evaluator-judge-v1", "COMPLETED",
                attempt2Id + ":" + formula.getId()));
        CandidateEvaluationSummaryEntity summary2 = summaryRepository.save(new CandidateEvaluationSummaryEntity(
                attempt2Id, configId, inviteId, 2, formula, run2, "COMPLETED",
                new BigDecimal("3.50"), new BigDecimal("1.0000"), 0, "auto_released", Instant.now()));
        skillScoreRepository.save(new SkillScoreEntity(
                summary2, formula, skillId, "N/A", null, null, 0, "auto_released"));

        entityManager.flush();
        entityManager.clear();

        assertThat(summaryRepository.findByAttemptId(attempt1Id)).isPresent();
        assertThat(summaryRepository.findByAttemptId(attempt2Id)).isPresent();
        assertThat(summaryRepository.findByConfigIdOrderByCreatedAtDesc(configId)).hasSize(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void chkNaScoreNullBlocksNaWithNonNullScoreAndScoredWithNullScore() {
        UUID configId = UUID.randomUUID();
        UUID summaryId = transactionTemplate.execute(status -> {
            FormulaVersionEntity formula = formulaVersionRepository.save(new FormulaVersionEntity(
                    configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null));
            UUID attemptId = UUID.randomUUID();
            EvaluationRunEntity run = evaluationRunRepository.save(new EvaluationRunEntity(
                    attemptId, configId, formula, "evaluator-judge-v1", "COMPLETED",
                    attemptId + ":" + formula.getId()));
            CandidateEvaluationSummaryEntity summary = summaryRepository.save(new CandidateEvaluationSummaryEntity(
                    attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                    null, BigDecimal.ZERO, 0, "pending", null));
            entityManager.flush();
            return summary.getId();
        });
        UUID formulaId = jdbc.queryForObject(
                "SELECT id FROM formula_version WHERE config_id = ?", UUID.class, configId);

        try {
            // N/A with non-null score MUST fail chk_na_score_null
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO skill_score (id, summary_id, formula_version_id, skill_id, status, score, verified_evidence_count, review_status) "
                            + "VALUES (?, ?, ?, ?, 'N/A', 0.00, 0, 'pending')",
                    UUID.randomUUID(), summaryId, formulaId, UUID.randomUUID()))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("chk_na_score_null");

            // SCORED with null score MUST fail chk_na_score_null
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO skill_score (id, summary_id, formula_version_id, skill_id, status, score, verified_evidence_count, review_status) "
                            + "VALUES (?, ?, ?, ?, 'SCORED', NULL, 1, 'pending')",
                    UUID.randomUUID(), summaryId, formulaId, UUID.randomUUID()))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("chk_na_score_null");
        } finally {
            transactionTemplate.executeWithoutResult(status ->
                    eventPublisher.publishEvent(new AssessmentDeletedEvent(configId, "cleanup")));
        }
    }

    @Test
    void assessmentDeletedEventPurgesAllFiveTablesOnlyForTargetConfig() {
        UUID deletedConfigId = UUID.randomUUID();
        UUID keptConfigId = UUID.randomUUID();

        seedFullGraph(deletedConfigId);
        seedFullGraph(keptConfigId);
        entityManager.flush();
        entityManager.clear();

        eventPublisher.publishEvent(new AssessmentDeletedEvent(deletedConfigId, UUID.randomUUID().toString()));
        entityManager.flush();
        entityManager.clear();

        assertThat(countRows("skill_score", deletedConfigId)).isZero();
        assertThat(countRows("response_evaluation", deletedConfigId)).isZero();
        assertThat(countRows("candidate_evaluation_summary", deletedConfigId)).isZero();
        assertThat(countRows("evaluation_run", deletedConfigId)).isZero();
        assertThat(countRows("formula_version", deletedConfigId)).isZero();

        assertThat(countRows("skill_score", keptConfigId)).isEqualTo(1);
        assertThat(countRows("response_evaluation", keptConfigId)).isEqualTo(1);
        assertThat(countRows("candidate_evaluation_summary", keptConfigId)).isEqualTo(1);
        assertThat(countRows("evaluation_run", keptConfigId)).isEqualTo(1);
        assertThat(countRows("formula_version", keptConfigId)).isEqualTo(1);
    }

    private void seedFullGraph(UUID configId) {
        UUID coreQuestionId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        FormulaVersionEntity formula = formulaVersionRepository.save(new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"),
                Map.of(coreQuestionId.toString(), new BigDecimal("1.00")), null));
        EvaluationRunEntity run = evaluationRunRepository.save(new EvaluationRunEntity(
                attemptId, configId, formula, "evaluator-judge-v1", "COMPLETED",
                attemptId + ":" + formula.getId()));
        CandidateEvaluationSummaryEntity summary = summaryRepository.save(new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, formula, run, "COMPLETED",
                new BigDecimal("3.00"), BigDecimal.ONE, 0, "auto_released", Instant.now()));
        responseEvaluationRepository.save(new ResponseEvaluationEntity(
                run, summary, UUID.randomUUID(), coreQuestionId, skillId,
                "core", new BigDecimal("1.00"), 3, 3,
                "wrapped write in transaction", "wrapped write in transaction",
                true, "wrapped write in transaction", "Shows solid grasp",
                3, new BigDecimal("0.9200"), null, "auto_released", 3));
        skillScoreRepository.save(new SkillScoreEntity(
                summary, formula, skillId, "SCORED", new BigDecimal("3.00"),
                new BigDecimal("0.9200"), 1, "auto_released"));
    }

    private int countRows(String table, UUID configId) {
        String sql = switch (table) {
            case "skill_score" ->
                    "SELECT COUNT(*) FROM skill_score ss JOIN candidate_evaluation_summary s ON ss.summary_id = s.id WHERE s.config_id = ?";
            case "response_evaluation" ->
                    "SELECT COUNT(*) FROM response_evaluation re JOIN candidate_evaluation_summary s ON re.summary_id = s.id WHERE s.config_id = ?";
            default -> "SELECT COUNT(*) FROM " + table + " WHERE config_id = ?";
        };
        Integer count = jdbc.queryForObject(sql, Integer.class, configId);
        return count == null ? 0 : count;
    }
}
