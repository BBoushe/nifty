package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import de.fhdortmund.nifty.assessment.service.ImmutableTranscriptView;
import de.fhdortmund.nifty.assessment.service.StudentSoftSkillProfile;
import de.fhdortmund.nifty.assessment.service.TranscriptEntryView;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.auth.service.CandidateSessionPrincipal;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionEntity;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationEntity;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationRepository;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreEntity;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * REQ-CR-01..06 / Decisions 2.10, 2.15, 2.16, 4.3:
 * Candidate results gate before release, strict candidate isolation (403 on other config),
 * and full post-release transparency including quotes, weights, conversation log, advice, and BFI-10 traits.
 */
class CandidateResultsServiceTest {

    private static final List<String> RUBRIC = List.of("None", "Basic", "Developing", "Proficient", "Expert");

    private AuthContextService authContextService;
    private StudioQueryService studioQueryService;
    private AssessmentQueryService assessmentQueryService;
    private CandidateEvaluationSummaryRepository summaryRepository;
    private ResponseEvaluationRepository responseEvaluationRepository;
    private SkillScoreRepository skillScoreRepository;
    private CandidateResultsService service;

    @BeforeEach
    void setUp() {
        authContextService = mock(AuthContextService.class);
        studioQueryService = mock(StudioQueryService.class);
        assessmentQueryService = mock(AssessmentQueryService.class);
        summaryRepository = mock(CandidateEvaluationSummaryRepository.class);
        responseEvaluationRepository = mock(ResponseEvaluationRepository.class);
        skillScoreRepository = mock(SkillScoreRepository.class);

        service = new CandidateResultsService(
                authContextService,
                studioQueryService,
                assessmentQueryService,
                summaryRepository,
                responseEvaluationRepository,
                skillScoreRepository,
                new DeterministicAdviceBuilder());
    }

    @Test
    void forbidsCandidateRequestingAnotherAssessmentConfig() {
        UUID ownConfig = UUID.randomUUID();
        UUID otherConfig = UUID.randomUUID();
        when(authContextService.currentCandidate())
                .thenReturn(new CandidateSessionPrincipal(UUID.randomUUID(), ownConfig, "cand_1", "hash"));

        assertThatThrownBy(() -> service.getResultsForCurrentCandidate(otherConfig))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void withholdsScoresWeightsFormulaAndTraitsBeforeRelease() {
        UUID configId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        when(authContextService.currentCandidate())
                .thenReturn(new CandidateSessionPrincipal(inviteId, configId, "cand_1", "hash"));

        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k1");
        CandidateEvaluationSummaryEntity flagged = new CandidateEvaluationSummaryEntity(
                attemptId, configId, inviteId, 1, formula, run, "COMPLETED",
                new BigDecimal("3.00"), BigDecimal.ONE, 1, "flagged", null);
        when(summaryRepository.findTopByConfigIdAndInviteIdOrderByAttemptNoDescCreatedAtDesc(configId, inviteId))
                .thenReturn(Optional.of(flagged));

        EvaluationDtos.CandidateResultsResponse response = service.getResultsForCurrentCandidate(configId);

        assertThat(response.released()).isFalse();
        assertThat(response.releaseStatus()).isEqualTo(ReleaseStatus.PENDING);
        assertThat(response.fitScore()).isNull();
        assertThat(response.coverage()).isNull();
        assertThat(response.formulaPreset()).isNull();
        assertThat(response.skillScores()).isNull();
        assertThat(response.evidence()).isNull();
        assertThat(response.questionWeights()).isNull();
        assertThat(response.conversationLog()).isNull();
        assertThat(response.softSkillTraits()).isNull();
        verify(assessmentQueryService, never()).getSoftSkillProfileForAttempt(attemptId);
    }

    @Test
    void withholdsResultsWhenLatestAttemptIsNotYetCompleted() {
        UUID configId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        when(authContextService.currentCandidate())
                .thenReturn(new CandidateSessionPrincipal(inviteId, configId, "cand_2", "hash"));

        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "RUNNING", "k-run");
        CandidateEvaluationSummaryEntity running = new CandidateEvaluationSummaryEntity(
                attemptId, configId, inviteId, 1, formula, run, "RUNNING",
                new BigDecimal("3.00"), BigDecimal.ONE, 0, "auto_released", Instant.now());
        when(summaryRepository.findTopByConfigIdAndInviteIdOrderByAttemptNoDescCreatedAtDesc(configId, inviteId))
                .thenReturn(Optional.of(running));

        EvaluationDtos.CandidateResultsResponse response = service.getResultsForCurrentCandidate(configId);

        assertThat(response.released()).isFalse();
        assertThat(response.fitScore()).isNull();
        assertThat(response.skillScores()).isNull();
        assertThat(response.evidence()).isNull();
        verify(assessmentQueryService, never()).getSoftSkillProfileForAttempt(attemptId);
    }

    @Test
    void returnsFullTransparentProfileAfterRelease() {
        UUID configId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID skillTesting = UUID.randomUUID();
        UUID skillDocker = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();

        when(authContextService.currentCandidate())
                .thenReturn(new CandidateSessionPrincipal(inviteId, configId, "cand_42", "hash"));

        FormulaVersionEntity formula = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"),
                Map.of(q1.toString(), new BigDecimal("2.00")), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, formula, "v1", "COMPLETED", "k2");
        CandidateEvaluationSummaryEntity released = new CandidateEvaluationSummaryEntity(
                attemptId, configId, inviteId, 1, formula, run, "COMPLETED",
                new BigDecimal("4.00"), BigDecimal.ONE, 0, "auto_released", Instant.now());

        when(summaryRepository.findTopByConfigIdAndInviteIdOrderByAttemptNoDescCreatedAtDesc(configId, inviteId))
                .thenReturn(Optional.of(released));
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(
                        new EvaluatorSkill(skillTesting, "testing", RUBRIC),
                        new EvaluatorSkill(skillDocker, "docker", RUBRIC)),
                List.of()));
        when(skillScoreRepository.findBySummaryId(released.getId())).thenReturn(List.of(
                new SkillScoreEntity(released, formula, skillTesting, "SCORED", new BigDecimal("4.00"),
                        new BigDecimal("0.9500"), 1, "auto_released"),
                new SkillScoreEntity(released, formula, skillDocker, "N/A", null, null, 0, "auto_released")));
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(released.getId())).thenReturn(List.of(
                new ResponseEvaluationEntity(
                        run, released, entryId, q1, skillTesting, "core", new BigDecimal("2.00"),
                        4, 4, "write unit tests first", "write unit tests first", true, "write unit tests first",
                        "Strong testing discipline", 4, new BigDecimal("0.9500"), null, "auto_released", 4)));
        when(assessmentQueryService.getTranscriptForAttempt(attemptId)).thenReturn(new ImmutableTranscriptView(
                attemptId, configId, inviteId, "v1", 1, Instant.now().minusSeconds(60), Instant.now(),
                List.of(new TranscriptEntryView(
                        entryId, 1, q1, EntryOrigin.CORE, "How do you test?", "I write unit tests first.",
                        List.of(skillTesting), Instant.now().minusSeconds(60), Instant.now(), Instant.now(),
                        40, false, false, false))));
        when(assessmentQueryService.getSoftSkillProfileForAttempt(attemptId)).thenReturn(Optional.of(
                new StudentSoftSkillProfile(inviteId, attemptId, Map.of(
                        "conscientiousness", 4.5,
                        "agreeableness", 4.0,
                        "neuroticism", 2.0,
                        "extraversion", 3.5,
                        "openness", 4.0))));

        EvaluationDtos.CandidateResultsResponse res = service.getResultsForCurrentCandidate(configId);

        assertThat(res.released()).isTrue();
        assertThat(res.releaseStatus()).isEqualTo(ReleaseStatus.AUTO_RELEASED);
        assertThat(res.fitScore()).isEqualByComparingTo("4.00");
        assertThat(res.formulaPreset()).isEqualTo("WEIGHTED_MEAN");
        assertThat(res.evidence()).hasSize(1);
        assertThat(res.evidence().getFirst().verifiedQuote()).isEqualTo("write unit tests first");
        assertThat(res.conversationLog()).hasSize(1);
        assertThat(res.advice().strengths()).containsExactly("testing");
        assertThat(res.advice().notAssessed()).containsExactly("docker");
        assertThat(res.softSkillTraits()).hasSize(5);
    }
}
