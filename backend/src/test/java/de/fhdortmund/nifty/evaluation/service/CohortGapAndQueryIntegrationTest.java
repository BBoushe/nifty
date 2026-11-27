package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunEntity;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunRepository;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionRepository;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreEntity;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * US-EV-04 / REQ-EV-01, REQ-SM-04, REQ-SM-07 / Decisions 2.10, 2.13:
 * Verifies SQL-only Cohort Gap Overview and EvaluationQueryService over PostgreSQL Testcontainers:
 * latest completed attempt per invite only, released attempts only, explicit N/A (never 0),
 * and unreleased candidates isolated in pendingInviteIds with no scores.
 */
@SpringBootTest
@ActiveProfiles("test")
class CohortGapAndQueryIntegrationTest extends PostgresContainerBase {

    private static final List<String> RUBRIC = List.of("None", "Basic", "Developing", "Proficient", "Expert");

    @Autowired
    private EvaluationQueryService evaluationQueryService;
    @Autowired
    private CohortGapQueryService cohortGapQueryService;
    @Autowired
    private FormulaVersionRepository formulaVersionRepository;
    @Autowired
    private EvaluationRunRepository evaluationRunRepository;
    @Autowired
    private CandidateEvaluationSummaryRepository summaryRepository;
    @Autowired
    private SkillScoreRepository skillScoreRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private StudioQueryService studioQueryService;
    @MockitoBean
    private AuthContextService authContextService;

    @Test
    void queryServiceAndCohortGapsUseLatestReleasedAttemptOnlyAndKeepNaExplicit() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID skillJava = UUID.randomUUID();
        UUID skillDocker = UUID.randomUUID();
        UUID inviteA = UUID.randomUUID();
        UUID inviteB = UUID.randomUUID();
        UUID attemptA1 = UUID.randomUUID();
        UUID attemptA2 = UUID.randomUUID();
        UUID attemptB1 = UUID.randomUUID();

        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(
                        new EvaluatorSkill(skillJava, "Java", RUBRIC),
                        new EvaluatorSkill(skillDocker, "Docker", RUBRIC)),
                List.of()));

        transactionTemplate.executeWithoutResult(tx -> {
            FormulaVersionEntity formula = formulaVersionRepository.save(new FormulaVersionEntity(
                    configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), professorId));

            // Candidate A Attempt 1 (older, released, low score 1.00) - MUST be superseded by Attempt 2!
            EvaluationRunEntity runA1 = evaluationRunRepository.save(new EvaluationRunEntity(
                    attemptA1, configId, formula, "v1", "COMPLETED", attemptA1 + ":v1"));
            CandidateEvaluationSummaryEntity sumA1 = summaryRepository.save(new CandidateEvaluationSummaryEntity(
                    attemptA1, configId, inviteA, 1, formula, runA1, "COMPLETED",
                    new BigDecimal("1.00"), BigDecimal.ONE, 0, "auto_released", Instant.now().minusSeconds(300)));
            skillScoreRepository.save(new SkillScoreEntity(
                    sumA1, formula, skillJava, "SCORED", new BigDecimal("1.00"), new BigDecimal("0.9000"), 1, "auto_released"));
            skillScoreRepository.save(new SkillScoreEntity(
                    sumA1, formula, skillDocker, "SCORED", new BigDecimal("1.00"), new BigDecimal("0.9000"), 1, "auto_released"));

            // Candidate A Attempt 2 (latest, released, Java = 3.00, Docker = N/A)
            EvaluationRunEntity runA2 = evaluationRunRepository.save(new EvaluationRunEntity(
                    attemptA2, configId, formula, "v1", "COMPLETED", attemptA2 + ":v1"));
            CandidateEvaluationSummaryEntity sumA2 = summaryRepository.save(new CandidateEvaluationSummaryEntity(
                    attemptA2, configId, inviteA, 2, formula, runA2, "COMPLETED",
                    new BigDecimal("3.00"), new BigDecimal("0.8000"), 0, "approved", Instant.now()));
            skillScoreRepository.save(new SkillScoreEntity(
                    sumA2, formula, skillJava, "SCORED", new BigDecimal("3.00"), new BigDecimal("0.9200"), 2, "approved"));
            skillScoreRepository.save(new SkillScoreEntity(
                    sumA2, formula, skillDocker, "N/A", null, null, 0, "approved"));

            // Candidate B Attempt 1 (flagged/unreleased) - MUST stay out of released & cohort gaps!
            EvaluationRunEntity runB1 = evaluationRunRepository.save(new EvaluationRunEntity(
                    attemptB1, configId, formula, "v1", "COMPLETED", attemptB1 + ":v1"));
            CandidateEvaluationSummaryEntity sumB1 = summaryRepository.save(new CandidateEvaluationSummaryEntity(
                    attemptB1, configId, inviteB, 1, formula, runB1, "COMPLETED",
                    new BigDecimal("4.00"), BigDecimal.ONE, 1, "flagged", null));
            skillScoreRepository.save(new SkillScoreEntity(
                    sumB1, formula, skillJava, "SCORED", new BigDecimal("4.00"), new BigDecimal("0.5000"), 1, "flagged"));
            skillScoreRepository.save(new SkillScoreEntity(
                    sumB1, formula, skillDocker, "SCORED", new BigDecimal("4.00"), new BigDecimal("0.5000"), 1, "flagged"));
        });

        try {
            CandidateProfilesView profiles = evaluationQueryService.getCandidateProfilesForConfig(configId);
            assertThat(profiles.released()).hasSize(1);
            assertThat(profiles.pendingInviteIds()).containsExactly(inviteB);

            CandidateHardSkillProfile profileA = profiles.released().getFirst();
            assertThat(profileA.inviteId()).isEqualTo(inviteA);
            assertThat(profileA.attemptId()).isEqualTo(attemptA2);
            assertThat(profileA.releaseStatus()).isEqualTo(ReleaseStatus.APPROVED);
            assertThat(profileA.fitScore()).isEqualByComparingTo("3.00");
            assertThat(profileA.skillScores()).hasSize(2);
            assertThat(profileA.skillScores().get(0).status()).isEqualTo(ScoreStatus.SCORED);
            assertThat(profileA.skillScores().get(0).score()).isEqualByComparingTo("3.00");
            assertThat(profileA.skillScores().get(1).status()).isEqualTo(ScoreStatus.NOT_ASSESSED);
            assertThat(profileA.skillScores().get(1).score()).isNull();

            // SQL-only Cohort Gaps over released latest attempts ONLY (attemptA2 only; attemptA1 & attemptB1 excluded!)
            EvaluationDtos.CohortGapsResponse gaps = cohortGapQueryService.getCohortGaps(configId);
            assertThat(gaps.releasedCandidateCount()).isEqualTo(1);
            assertThat(gaps.skills()).hasSize(2);

            EvaluationDtos.SkillGapStat javaStat = gaps.skills().stream()
                    .filter(s -> s.skillId().equals(skillJava)).findFirst().orElseThrow();
            assertThat(javaStat.scoredCount()).isEqualTo(1);
            assertThat(javaStat.naCount()).isZero();
            assertThat(javaStat.meanScore()).isEqualByComparingTo("3.00");
            assertThat(javaStat.levelDistribution().get(3)).isEqualTo(1);

            EvaluationDtos.SkillGapStat dockerStat = gaps.skills().stream()
                    .filter(s -> s.skillId().equals(skillDocker)).findFirst().orElseThrow();
            assertThat(dockerStat.scoredCount()).isZero();
            assertThat(dockerStat.naCount()).isEqualTo(1);
            assertThat(dockerStat.naShare()).isEqualByComparingTo("1.0000");
            assertThat(dockerStat.meanScore()).isNull();
        } finally {
            transactionTemplate.executeWithoutResult(tx ->
                    eventPublisher.publishEvent(new AssessmentDeletedEvent(configId, "test-cleanup")));
        }
    }
}
