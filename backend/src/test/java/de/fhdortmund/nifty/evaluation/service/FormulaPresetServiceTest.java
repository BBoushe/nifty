package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionRepository;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationEntity;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationRepository;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.EvaluatorQuestion;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import de.fhdortmund.nifty.studio.service.view.ScoringQuestion;
import de.fhdortmund.nifty.studio.service.view.ScoringView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * US-EV-04b / REQ-FE-02, REQ-FE-05 / Decision 2.19:
 * Formula presets list WEIGHTED_MEAN, applying saves a new immutable formula_version and
 * recomputes scores without LLM calls, preview stores nothing, and unknown preset or
 * free-form expression raises 400 FieldValidationException without storing a version.
 */
class FormulaPresetServiceTest {

    private static final List<String> RUBRIC = List.of("None", "Basic", "Developing", "Proficient", "Expert");

    private StudioQueryService studioQueryService;
    private AuthContextService authContextService;
    private AuditService auditService;
    private FormulaVersionRepository formulaVersionRepository;
    private CandidateEvaluationSummaryRepository summaryRepository;
    private ResponseEvaluationRepository responseEvaluationRepository;
    private SkillScoreRepository skillScoreRepository;
    private FormulaPresetService service;

    @BeforeEach
    void setUp() {
        studioQueryService = mock(StudioQueryService.class);
        authContextService = mock(AuthContextService.class);
        auditService = mock(AuditService.class);
        formulaVersionRepository = mock(FormulaVersionRepository.class);
        summaryRepository = mock(CandidateEvaluationSummaryRepository.class);
        responseEvaluationRepository = mock(ResponseEvaluationRepository.class);
        skillScoreRepository = mock(SkillScoreRepository.class);

        FormulaRecalculationHelper recalc = new FormulaRecalculationHelper(
                new SkillFormulaCalculator(), skillScoreRepository);

        service = new FormulaPresetService(
                studioQueryService,
                authContextService,
                auditService,
                formulaVersionRepository,
                summaryRepository,
                responseEvaluationRepository,
                recalc);
    }

    @Test
    void listsWeightedMeanPreset() {
        List<EvaluationDtos.FormulaPresetInfo> presets = service.listPresets();
        assertThat(presets).extracting(EvaluationDtos.FormulaPresetInfo::preset).contains("WEIGHTED_MEAN");
    }

    @Test
    void applyPresetSavesNewImmutableVersionAndRecomputesWithoutLlm() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();

        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain indexes.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.5000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("2.00"), new BigDecimal("0.6000"), List.of(skillId)))));

        FormulaVersionEntity v1 = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        when(formulaVersionRepository.findTopByConfigIdOrderByVersionNumberDesc(configId)).thenReturn(Optional.of(v1));
        when(formulaVersionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, v1, "v1", "COMPLETED", "k1");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, v1, run, "COMPLETED",
                new BigDecimal("2.00"), BigDecimal.ONE, 0, "auto_released", null);
        ResponseEvaluationEntity response = new ResponseEvaluationEntity(
                run, summary, UUID.randomUUID(), q1, skillId, "core", new BigDecimal("2.00"),
                3, 3, "index on email column", "index on email column", true, "index on email column", "good",
                3, new BigDecimal("0.9200"), null, "auto_released", 3);

        when(summaryRepository.findLatestCompletedPerInvite(configId)).thenReturn(List.of(summary));
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(any())).thenReturn(List.of(response));

        EvaluationDtos.FormulaVersionResponse result = service.applyPreset(
                configId, new FormulaPresetService.FormulaPresetRequest("WEIGHTED_MEAN", null));

        assertThat(result.versionNumber()).isEqualTo(2);
        assertThat(result.preset()).isEqualTo("WEIGHTED_MEAN");
        assertThat(result.recomputedCandidates()).isEqualTo(1);
        assertThat(summary.getFormulaVersion().getVersionNumber()).isEqualTo(2);
        assertThat(summary.getFitScore()).isEqualByComparingTo("3.00");
        verify(formulaVersionRepository).saveAndFlush(any());
        verify(skillScoreRepository).saveAll(any());
    }

    @Test
    void concurrentVersionCreationMapsTheUniqueViolationToConflict() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId, List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)), List.of()));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.5000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("2.00"), new BigDecimal("0.6000"), List.of(skillId)))));
        when(formulaVersionRepository.findTopByConfigIdOrderByVersionNumberDesc(configId)).thenReturn(Optional.empty());
        when(formulaVersionRepository.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq_formula_version"));

        assertThatThrownBy(() -> service.applyPreset(
                configId, new FormulaPresetService.FormulaPresetRequest("WEIGHTED_MEAN", null)))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void previewReturnsWhatIfScoresAndStoresNothing() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();

        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        when(studioQueryService.evaluatorView(configId)).thenReturn(new EvaluatorView(
                configId,
                List.of(new EvaluatorSkill(skillId, "SQL", RUBRIC)),
                List.of(new EvaluatorQuestion(q1, "Explain indexes.", List.of(skillId)))));
        when(studioQueryService.scoringView(configId)).thenReturn(new ScoringView(
                configId, new BigDecimal("0.5000"),
                List.of(new ScoringQuestion(q1, new BigDecimal("2.00"), new BigDecimal("0.6000"), List.of(skillId)))));

        FormulaVersionEntity v1 = new FormulaVersionEntity(
                configId, 1, "WEIGHTED_MEAN", new BigDecimal("0.5000"), Map.of(), null);
        EvaluationRunEntity run = new EvaluationRunEntity(attemptId, configId, v1, "v1", "COMPLETED", "k1");
        CandidateEvaluationSummaryEntity summary = new CandidateEvaluationSummaryEntity(
                attemptId, configId, UUID.randomUUID(), 1, v1, run, "COMPLETED",
                new BigDecimal("3.00"), BigDecimal.ONE, 0, "auto_released", null);
        ResponseEvaluationEntity response = new ResponseEvaluationEntity(
                run, summary, UUID.randomUUID(), q1, skillId, "core", new BigDecimal("2.00"),
                4, 4, "index on email column", "index on email column", true, "index on email column", "great",
                4, new BigDecimal("0.9500"), null, "auto_released", 4);

        when(summaryRepository.findLatestCompletedPerInvite(configId)).thenReturn(List.of(summary));
        when(responseEvaluationRepository.findBySummaryIdOrderByIdAsc(any())).thenReturn(List.of(response));

        EvaluationDtos.FormulaPreviewResponse preview = service.previewPreset(
                configId, new FormulaPresetService.FormulaPresetRequest("WEIGHTED_MEAN", null));

        assertThat(preview.candidates()).hasSize(1);
        assertThat(preview.candidates().getFirst().fitScore()).isEqualByComparingTo("4.00");
        verify(formulaVersionRepository, never()).save(any());
        verify(summaryRepository, never()).save(any());
        verify(skillScoreRepository, never()).saveAll(any());
    }

    @Test
    void rejectsUnknownPresetOrFreeFormExpressionWithoutSaving() {
        UUID configId = UUID.randomUUID();

        assertThatThrownBy(() -> service.applyPreset(
                configId, new FormulaPresetService.FormulaPresetRequest("CUSTOM_UNKNOWN", null)))
                .isInstanceOf(FieldValidationException.class);

        assertThatThrownBy(() -> service.applyPreset(
                configId, new FormulaPresetService.FormulaPresetRequest("WEIGHTED_MEAN", "score * 2")))
                .isInstanceOf(FieldValidationException.class);

        verify(formulaVersionRepository, never()).saveAndFlush(any());
    }
}
