package de.fhdortmund.nifty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.assessment.service.EntryOrigin;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.context.service.ContextCreationService;
import de.fhdortmund.nifty.evaluation.service.EvaluationQueryService;
import de.fhdortmund.nifty.evaluation.service.ReleaseStatus;
import de.fhdortmund.nifty.evaluation.service.ScoreStatus;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-BF-03: the context starts with one bean per shared interface, and each placeholder throws.
 * An owner deletes the placeholder when the real bean lands; the throw checks then skip that interface.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContractStubsTest extends PostgresContainerBase {

    private static final UUID ID = UUID.randomUUID();

    @Autowired
    private ApplicationContext context;
    @Autowired
    private JsonMapper json;
    @Autowired
    private AuditService audit;
    @Autowired
    private AuthContextService auth;
    @Autowired
    private StudioQueryService studio;
    @Autowired
    private ContextCreationService contextCreation;
    @Autowired
    private AssessmentQueryService assessment;
    @Autowired
    private EvaluationQueryService evaluation;

    @Test
    void contextHoldsExactlyOneBeanPerSharedInterface() {
        for (Class<?> type : new Class<?>[] {AuditService.class, AuthContextService.class, StudioQueryService.class,
                ContextCreationService.class, AssessmentQueryService.class, EvaluationQueryService.class}) {
            assertThat(context.getBeansOfType(type)).as(type.getSimpleName()).hasSize(1);
        }
    }

    @Test
    void placeholderMethodsThrowUnsupportedOperation() {
        assertPlaceholderThrows(audit, () -> audit.record("a", null, "t", ID, "x", null, null, null));
        assertPlaceholderThrows(auth, () -> auth.currentProfessorId());
        assertPlaceholderThrows(studio, () -> studio.exists(ID));
        assertPlaceholderThrows(contextCreation, () -> contextCreation.inspectCandidateInput(ID, "text"));
        assertPlaceholderThrows(assessment, () -> assessment.getTranscriptForAttempt(ID));
        assertPlaceholderThrows(evaluation, () -> evaluation.getCandidateProfilesForConfig(ID));
    }

    @Test
    void enumsUseTheirJsonAndDbLiterals() throws Exception {
        assertThat(json.writeValueAsString(ScoreStatus.NOT_ASSESSED)).isEqualTo("\"N/A\"");
        assertThat(json.writeValueAsString(ScoreStatus.SCORED)).isEqualTo("\"SCORED\"");
        assertThat(json.writeValueAsString(EntryOrigin.FOLLOW_UP)).isEqualTo("\"follow_up\"");
        assertThat(json.writeValueAsString(ReleaseStatus.AUTO_RELEASED)).isEqualTo("\"auto_released\"");
        assertThat(json.readValue("\"follow_up\"", EntryOrigin.class)).isEqualTo(EntryOrigin.FOLLOW_UP);
        assertThat(json.readValue("\"N/A\"", ScoreStatus.class)).isEqualTo(ScoreStatus.NOT_ASSESSED);
    }

    private static void assertPlaceholderThrows(Object bean, ThrowingCallable call) {
        // A real bean replaced this placeholder. Return, not assumeTrue: an abort would skip the remaining checks.
        if (!bean.getClass().getSimpleName().endsWith("Placeholder")) {
            return;
        }
        assertThatThrownBy(call).isInstanceOf(UnsupportedOperationException.class);
    }
}
