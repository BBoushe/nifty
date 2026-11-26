package de.fhdortmund.nifty.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * REQ-CR-05 / Decision 2.16: deterministic strengths and improvement areas from released scores,
 * explicitly excluding N/A skills from improvement areas.
 */
class DeterministicAdviceBuilderTest {

    private final DeterministicAdviceBuilder builder = new DeterministicAdviceBuilder();

    @Test
    void separatesStrengthsImprovementsAndNotAssessedIgnoringNa() {
        List<EvaluationDtos.MatrixCell> cells = List.of(
                new EvaluationDtos.MatrixCell(
                        UUID.randomUUID(), "testing", ScoreStatus.SCORED, new BigDecimal("4.00"),
                        new BigDecimal("0.9500"), 2, ReleaseStatus.APPROVED),
                new EvaluationDtos.MatrixCell(
                        UUID.randomUUID(), "sql", ScoreStatus.SCORED, new BigDecimal("1.00"),
                        new BigDecimal("0.8500"), 1, ReleaseStatus.APPROVED),
                new EvaluationDtos.MatrixCell(
                        UUID.randomUUID(), "docker", ScoreStatus.NOT_ASSESSED, null,
                        null, 0, ReleaseStatus.APPROVED));

        EvaluationDtos.CandidateAdvice advice = builder.build(cells);

        assertThat(advice.strengths()).containsExactly("testing");
        assertThat(advice.improvements()).containsExactly("sql");
        assertThat(advice.notAssessed()).containsExactly("docker");
    }
}
