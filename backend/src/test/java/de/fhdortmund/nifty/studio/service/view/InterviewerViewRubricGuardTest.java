package de.fhdortmund.nifty.studio.service.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * REQ-IS-09 / Decision 1.12 / Decision 1.19: InterviewerView and InterviewerQuestion must never leak
 * rubric levels, question weights, confidence thresholds, or coverage to Agent 1.
 */
class InterviewerViewRubricGuardTest {

    private static final List<String> FORBIDDEN_TOKENS = List.of("rubric", "weight", "threshold", "coverage");

    @Test
    void interviewerViewAndQuestionDeclareNoRubricWeightThresholdOrCoverageFields() {
        for (Class<?> viewType : new Class<?>[] {InterviewerView.class, InterviewerQuestion.class}) {
            assertThat(viewType.isRecord()).as(viewType.getSimpleName()).isTrue();
            for (RecordComponent component : viewType.getRecordComponents()) {
                String lowerName = component.getName().toLowerCase(Locale.ROOT);
                for (String forbidden : FORBIDDEN_TOKENS) {
                    assertThat(lowerName)
                            .as("%s.%s must not contain '%s'", viewType.getSimpleName(), component.getName(), forbidden)
                            .doesNotContain(forbidden);
                }
            }
        }
    }

    @Test
    void evaluatorSkillExposesRubricLevelsForAgent2() {
        boolean hasRubricLevels = false;
        for (RecordComponent component : EvaluatorSkill.class.getRecordComponents()) {
            if ("rubricLevels".equals(component.getName())) {
                hasRubricLevels = true;
            }
        }
        assertThat(hasRubricLevels).isTrue();
    }
}
