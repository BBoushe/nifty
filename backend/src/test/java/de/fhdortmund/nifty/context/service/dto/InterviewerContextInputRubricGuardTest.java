package de.fhdortmund.nifty.context.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** REQ-CC-01, Decision 1.12: the interviewer input has no field that could carry rubric data. */
class InterviewerContextInputRubricGuardTest {

    private static final Pattern FORBIDDEN =
            Pattern.compile("rubric|level|weight|threshold|score|bfi|confidence|evaluat", Pattern.CASE_INSENSITIVE);

    @Test
    void interviewerInputAndNestedRecordsDeclareNoRubricFields() {
        Set<Class<?>> visited = new HashSet<>();
        List<String> violations = new ArrayList<>();

        collectViolations(InterviewerContextInput.class, visited, violations);

        assertThat(visited).contains(InterviewerContextInput.class, CoreQuestionPromptView.class,
                DialogueTurnView.class);
        assertThat(violations).isEmpty();
    }

    private static void collectViolations(Class<?> type, Set<Class<?>> visited, List<String> violations) {
        if (!type.getPackageName().equals(InterviewerContextInput.class.getPackageName()) || !visited.add(type)) {
            return;
        }
        for (Field field : type.getDeclaredFields()) {
            if (FORBIDDEN.matcher(field.getName()).find()) {
                violations.add(type.getSimpleName() + "." + field.getName());
            }
            visitType(field.getGenericType(), visited, violations);
        }
    }

    private static void visitType(Type type, Set<Class<?>> visited, List<String> violations) {
        if (type instanceof Class<?> clazz) {
            collectViolations(clazz, visited, violations);
        } else if (type instanceof ParameterizedType generic) {
            for (Type argument : generic.getActualTypeArguments()) {
                visitType(argument, visited, violations);
            }
        }
    }
}
