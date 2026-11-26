package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryEntity;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionEntity;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationEntity;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreEntity;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.studio.service.view.EvaluatorSkill;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import de.fhdortmund.nifty.studio.service.view.ScoringQuestion;
import de.fhdortmund.nifty.studio.service.view.ScoringView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Shared deterministic Formula B recomputation and enum-mapping helper (Coding Standards #1, #3, #5).
 * Used by {@link EvaluationReviewService} and formula-preset recalculation with zero LLM calls.
 */
@Component
class FormulaRecalculationHelper {

    private final SkillFormulaCalculator formulaCalculator;
    private final SkillScoreRepository skillScoreRepository;

    FormulaRecalculationHelper(
            SkillFormulaCalculator formulaCalculator,
            SkillScoreRepository skillScoreRepository) {
        this.formulaCalculator = formulaCalculator;
        this.skillScoreRepository = skillScoreRepository;
    }

    SkillFormulaCalculator.FormulaResult computeForResponses(
            EvaluatorView evaluatorView,
            ScoringView scoringView,
            List<ResponseEvaluationEntity> responses,
            BigDecimal maxEntryShare) {

        List<UUID> configuredSkillIds = new ArrayList<>(evaluatorView.skills().size());
        for (int i = 0; i < evaluatorView.skills().size(); i++) {
            configuredSkillIds.add(evaluatorView.skills().get(i).skillId());
        }

        List<SkillFormulaCalculator.QuestionWeight> questionWeights = new ArrayList<>(scoringView.questions().size());
        Map<UUID, BigDecimal> weightByQuestion = new HashMap<>(scoringView.questions().size());
        for (int i = 0; i < scoringView.questions().size(); i++) {
            ScoringQuestion q = scoringView.questions().get(i);
            questionWeights.add(new SkillFormulaCalculator.QuestionWeight(q.coreQuestionId(), q.weight()));
            weightByQuestion.put(q.coreQuestionId(), q.weight());
        }

        List<SkillFormulaCalculator.ScoredEntryInput> inputs = new ArrayList<>(responses.size());
        Set<UUID> answeredCoreQuestions = new HashSet<>();
        for (int i = 0; i < responses.size(); i++) {
            ResponseEvaluationEntity r = responses.get(i);
            answeredCoreQuestions.add(r.getCoreQuestionId());
            BigDecimal weight = weightByQuestion.getOrDefault(r.getCoreQuestionId(), r.getQuestionWeight());
            boolean overridden = ReleaseStatus.OVERRIDDEN.literal().equals(r.getReviewStatus());
            inputs.add(new SkillFormulaCalculator.ScoredEntryInput(
                    r.getEntryId(),
                    r.getCoreQuestionId(),
                    r.getSkillId(),
                    parseOrigin(r.getQuestionOrigin()),
                    weight,
                    r.getEffectiveLevel(),
                    r.getCalculatedConfidence(),
                    r.isQuoteVerified(),
                    overridden,
                    r.getReviewStatus()));
        }

        return formulaCalculator.calculate(
                configuredSkillIds,
                questionWeights,
                inputs,
                answeredCoreQuestions,
                maxEntryShare);
    }

    void replaceSkillScores(
            CandidateEvaluationSummaryEntity summary,
            FormulaVersionEntity formulaVersion,
            SkillFormulaCalculator.FormulaResult result,
            String overrideReviewStatus) {
        skillScoreRepository.deleteBySummaryId(summary.getId());
        List<SkillScoreEntity> entities = new ArrayList<>(result.skillScores().size());
        for (int i = 0; i < result.skillScores().size(); i++) {
            SkillFormulaCalculator.CalculatedSkillScore sc = result.skillScores().get(i);
            String reviewStatus = overrideReviewStatus != null ? overrideReviewStatus : sc.reviewStatus();
            entities.add(new SkillScoreEntity(
                    summary,
                    formulaVersion,
                    sc.skillId(),
                    sc.status().literal(),
                    sc.score(),
                    sc.avgConfidence(),
                    sc.verifiedEvidenceCount(),
                    reviewStatus));
        }
        skillScoreRepository.saveAll(entities);
    }

    static List<EvaluationDtos.MatrixCell> toMatrixCells(
            List<EvaluatorSkill> configuredSkills,
            List<SkillScoreEntity> persistedScores) {
        Map<UUID, SkillScoreEntity> bySkill = new HashMap<>(persistedScores.size());
        for (int i = 0; i < persistedScores.size(); i++) {
            SkillScoreEntity sc = persistedScores.get(i);
            bySkill.put(sc.getSkillId(), sc);
        }
        List<EvaluationDtos.MatrixCell> cells = new ArrayList<>(configuredSkills.size());
        for (int i = 0; i < configuredSkills.size(); i++) {
            EvaluatorSkill skill = configuredSkills.get(i);
            SkillScoreEntity sc = bySkill.get(skill.skillId());
            if (sc == null) {
                cells.add(new EvaluationDtos.MatrixCell(
                        skill.skillId(), skill.name(), ScoreStatus.NOT_ASSESSED, null, null, 0, ReleaseStatus.PENDING));
            } else {
                cells.add(new EvaluationDtos.MatrixCell(
                        skill.skillId(),
                        skill.name(),
                        parseScoreStatus(sc.getStatus()),
                        sc.getScore(),
                        sc.getAvgConfidence(),
                        sc.getVerifiedEvidenceCount(),
                        parseReleaseStatus(sc.getReviewStatus())));
            }
        }
        return List.copyOf(cells);
    }

    static List<EvaluationDtos.MatrixCell> toMatrixCellsFromCalculated(
            List<EvaluatorSkill> configuredSkills,
            List<SkillFormulaCalculator.CalculatedSkillScore> calculated) {
        Map<UUID, SkillFormulaCalculator.CalculatedSkillScore> bySkill = new HashMap<>(calculated.size());
        for (int i = 0; i < calculated.size(); i++) {
            SkillFormulaCalculator.CalculatedSkillScore sc = calculated.get(i);
            bySkill.put(sc.skillId(), sc);
        }
        List<EvaluationDtos.MatrixCell> cells = new ArrayList<>(configuredSkills.size());
        for (int i = 0; i < configuredSkills.size(); i++) {
            EvaluatorSkill skill = configuredSkills.get(i);
            SkillFormulaCalculator.CalculatedSkillScore sc = bySkill.get(skill.skillId());
            if (sc == null) {
                cells.add(new EvaluationDtos.MatrixCell(
                        skill.skillId(), skill.name(), ScoreStatus.NOT_ASSESSED, null, null, 0, ReleaseStatus.PENDING));
            } else {
                cells.add(new EvaluationDtos.MatrixCell(
                        skill.skillId(),
                        skill.name(),
                        sc.status(),
                        sc.score(),
                        sc.avgConfidence(),
                        sc.verifiedEvidenceCount(),
                        parseReleaseStatus(sc.reviewStatus())));
            }
        }
        return List.copyOf(cells);
    }

    static boolean isReleased(String releaseStatusLiteral) {
        return ReleaseStatus.AUTO_RELEASED.literal().equals(releaseStatusLiteral)
                || ReleaseStatus.APPROVED.literal().equals(releaseStatusLiteral)
                || ReleaseStatus.OVERRIDDEN.literal().equals(releaseStatusLiteral);
    }

    static ReleaseStatus parseReleaseStatus(String literal) {
        if (literal == null) {
            return ReleaseStatus.PENDING;
        }
        for (ReleaseStatus rs : ReleaseStatus.values()) {
            if (rs.literal().equals(literal)) {
                return rs;
            }
        }
        return ReleaseStatus.PENDING;
    }

    static ScoreStatus parseScoreStatus(String literal) {
        if (ScoreStatus.SCORED.literal().equals(literal)) {
            return ScoreStatus.SCORED;
        }
        return ScoreStatus.NOT_ASSESSED;
    }

    static EntryOrigin parseOrigin(String literal) {
        if (literal == null) {
            return EntryOrigin.CORE;
        }
        for (EntryOrigin origin : EntryOrigin.values()) {
            if (origin.literal().equals(literal)) {
                return origin;
            }
        }
        return EntryOrigin.CORE;
    }

    static List<String> splitFlagReasons(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split(",");
        List<String> list = new ArrayList<>(parts.length);
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (!p.isEmpty()) {
                list.add(p);
            }
        }
        return List.copyOf(list);
    }
}
