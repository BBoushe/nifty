package de.fhdortmund.nifty.evaluation.service;

import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunRepository;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionRepository;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationRepository;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-EV-02 / Decision 4.4: synchronous GDPR purge of all five evaluation tables inside the
 * deleting transaction when an assessment configuration is removed.
 */
@Service
@Transactional(readOnly = true)
public class EvaluationCleanupListener {

    private final SkillScoreRepository skillScoreRepository;
    private final ResponseEvaluationRepository responseEvaluationRepository;
    private final CandidateEvaluationSummaryRepository summaryRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final FormulaVersionRepository formulaVersionRepository;

    EvaluationCleanupListener(
            SkillScoreRepository skillScoreRepository,
            ResponseEvaluationRepository responseEvaluationRepository,
            CandidateEvaluationSummaryRepository summaryRepository,
            EvaluationRunRepository evaluationRunRepository,
            FormulaVersionRepository formulaVersionRepository) {
        this.skillScoreRepository = skillScoreRepository;
        this.responseEvaluationRepository = responseEvaluationRepository;
        this.summaryRepository = summaryRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.formulaVersionRepository = formulaVersionRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onAssessmentDeleted(AssessmentDeletedEvent event) {
        UUID configId = event.configId();
        skillScoreRepository.deleteByConfigId(configId);
        responseEvaluationRepository.deleteByConfigId(configId);
        summaryRepository.deleteByConfigId(configId);
        evaluationRunRepository.deleteByConfigId(configId);
        formulaVersionRepository.deleteByConfigId(configId);
    }
}
