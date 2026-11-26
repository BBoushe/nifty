package de.fhdortmund.nifty.evaluation.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * REQ-CR-05 / Decision 2.16: deterministic selection of candidate strengths, improvement areas,
 * and not-assessed skills from released scores only. N/A skills are never marked as weak.
 * Zero Java Streams (Coding Standard #3).
 */
@Component
public class DeterministicAdviceBuilder {

    private static final BigDecimal STRENGTH_MIN = new BigDecimal("3.00");
    private static final BigDecimal IMPROVEMENT_MAX = new BigDecimal("2.00");

    public EvaluationDtos.CandidateAdvice build(List<EvaluationDtos.MatrixCell> skillScores) {
        if (skillScores == null || skillScores.isEmpty()) {
            return new EvaluationDtos.CandidateAdvice(List.of(), List.of(), List.of());
        }

        List<EvaluationDtos.MatrixCell> scored = new ArrayList<>();
        List<String> notAssessed = new ArrayList<>();

        for (int i = 0; i < skillScores.size(); i++) {
            EvaluationDtos.MatrixCell cell = skillScores.get(i);
            if (cell.status() == ScoreStatus.NOT_ASSESSED || cell.value() == null) {
                notAssessed.add(cell.skillName());
            } else {
                scored.add(cell);
            }
        }

        scored.sort(Comparator.comparing(EvaluationDtos.MatrixCell::value).reversed()
                .thenComparing(EvaluationDtos.MatrixCell::skillName));

        List<String> strengths = new ArrayList<>();
        List<String> improvements = new ArrayList<>();

        for (int i = 0; i < scored.size(); i++) {
            EvaluationDtos.MatrixCell cell = scored.get(i);
            if (cell.value().compareTo(STRENGTH_MIN) >= 0) {
                strengths.add(cell.skillName());
            } else if (cell.value().compareTo(IMPROVEMENT_MAX) <= 0) {
                improvements.add(cell.skillName());
            }
        }

        // Order improvement areas from lowest score upward so biggest gaps appear first.
        if (improvements.size() > 1) {
            List<EvaluationDtos.MatrixCell> lowCells = new ArrayList<>(improvements.size());
            for (int i = 0; i < scored.size(); i++) {
                EvaluationDtos.MatrixCell cell = scored.get(i);
                if (cell.value().compareTo(IMPROVEMENT_MAX) <= 0) {
                    lowCells.add(cell);
                }
            }
            lowCells.sort(Comparator.comparing(EvaluationDtos.MatrixCell::value)
                    .thenComparing(EvaluationDtos.MatrixCell::skillName));
            improvements.clear();
            for (int i = 0; i < lowCells.size(); i++) {
                improvements.add(lowCells.get(i).skillName());
            }
        }

        return new EvaluationDtos.CandidateAdvice(
                List.copyOf(strengths),
                List.copyOf(improvements),
                List.copyOf(notAssessed));
    }
}
