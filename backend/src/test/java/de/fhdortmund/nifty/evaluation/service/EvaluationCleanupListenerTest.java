package de.fhdortmund.nifty.evaluation.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import de.fhdortmund.nifty.evaluation.repository.CandidateEvaluationSummaryRepository;
import de.fhdortmund.nifty.evaluation.repository.EvaluationRunRepository;
import de.fhdortmund.nifty.evaluation.repository.FormulaVersionRepository;
import de.fhdortmund.nifty.evaluation.repository.ResponseEvaluationRepository;
import de.fhdortmund.nifty.evaluation.repository.SkillScoreRepository;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** REQ-SM-14: the purge locks the summary rows of the assessment before it deletes any response evaluation. */
class EvaluationCleanupListenerTest {

    @Test
    void purgeLocksTheSummaryRowsBeforeAnyDelete() {
        SkillScoreRepository scores = mock(SkillScoreRepository.class);
        ResponseEvaluationRepository responses = mock(ResponseEvaluationRepository.class);
        CandidateEvaluationSummaryRepository summaries = mock(CandidateEvaluationSummaryRepository.class);
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        FormulaVersionRepository formulas = mock(FormulaVersionRepository.class);
        UUID configId = UUID.randomUUID();

        new EvaluationCleanupListener(scores, responses, summaries, runs, formulas)
                .onAssessmentDeleted(new AssessmentDeletedEvent(configId, "test"));

        InOrder order = inOrder(summaries, scores, responses);
        order.verify(summaries).findByConfigIdForUpdate(configId);
        order.verify(scores).deleteByConfigId(configId);
        order.verify(responses).deleteByConfigId(configId);
        order.verify(summaries).deleteByConfigId(configId);
    }
}
