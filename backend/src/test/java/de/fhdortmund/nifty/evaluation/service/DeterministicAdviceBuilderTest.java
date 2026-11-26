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

    @Test
    void sortsStrengthsDescendingAndImprovementsAscendingWithAlphabeticalTieBreakAndSkipsMidRange() {
        List<EvaluationDtos.MatrixCell> cells = List.of(
                cell("spring", ScoreStatus.SCORED, "3.00"),
                cell("git", ScoreStatus.SCORED, "4.00"),
                cell("api", ScoreStatus.SCORED, "3.00"),
                cell("mid", ScoreStatus.SCORED, "2.50"),
                cell("sql", ScoreStatus.SCORED, "2.00"),
                cell("docker", ScoreStatus.SCORED, "0.50"),
                cell("bash", ScoreStatus.SCORED, "2.00"),
                cell("k8s", ScoreStatus.NOT_ASSESSED, null));

        EvaluationDtos.CandidateAdvice advice = builder.build(cells);

        assertThat(advice.strengths()).containsExactly("git", "api", "spring");
        assertThat(advice.improvements()).containsExactly("docker", "bash", "sql");
        assertThat(advice.notAssessed()).containsExactly("k8s");

        EvaluationDtos.CandidateAdvice empty = builder.build(List.of());
        assertThat(empty.strengths()).isEmpty();
        assertThat(empty.improvements()).isEmpty();
        assertThat(empty.notAssessed()).isEmpty();

        EvaluationDtos.CandidateAdvice fromNull = builder.build(null);
        assertThat(fromNull.strengths()).isEmpty();
        assertThat(fromNull.improvements()).isEmpty();
        assertThat(fromNull.notAssessed()).isEmpty();
    }

    private static EvaluationDtos.MatrixCell cell(String name, ScoreStatus status, String score) {
        return new EvaluationDtos.MatrixCell(
                UUID.randomUUID(), name, status, score == null ? null : new BigDecimal(score),
                score == null ? null : new BigDecimal("0.9000"), score == null ? 0 : 1, ReleaseStatus.APPROVED);
    }
}
