package de.fhdortmund.nifty.studio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.audit.service.AuditActorRole;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.view.EvaluatorView;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.InterviewerView;
import de.fhdortmund.nifty.studio.service.view.ScoringView;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import de.fhdortmund.nifty.studio.service.view.TeamView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * REQ-IS-01, REQ-IS-06, REQ-IS-09, REQ-IS-12, Decision 1.19, Decision 2.21:
 * idempotent one-shot creation, cross-professor key collision, rollback on validation failure,
 * AuditService CONFIG_CREATED call, ownedBy, and StudioQueryServiceImpl JOIN FETCH views.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConfigCreationAndQueryIntegrationTest extends PostgresContainerBase {

    private static final List<String> RUBRIC_5 = List.of("None", "Basic", "Developing", "Proficient", "Expert");

    @Autowired
    private ConfigCreationService creationService;
    @Autowired
    private StudioQueryService queryService;
    @Autowired
    private JdbcTemplate jdbc;

    // Known gap (ticket 01 §4): AuditService and AuthContextService are placeholders until Wave 1 merges.
    @MockitoBean
    private AuditService auditService;
    @MockitoBean
    private AuthContextService authContextService;

    @BeforeEach
    void resetMocks() {
        clearInvocations(auditService, authContextService);
    }

    @Test
    void createPersistsOnceAuditsOnceAndReplaysIdempotentlyForSameProfessor() {
        UUID professorId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);

        CreatedAssessment first = creationService.create(idempotencyKey, sampleRequest());
        assertThat(first.created()).isTrue();
        assertThat(first.configId()).isNotNull();

        CreatedAssessment replay = creationService.create(idempotencyKey, sampleRequest());
        assertThat(replay.created()).isFalse();
        assertThat(replay.configId()).isEqualTo(first.configId());

        verify(auditService, times(1)).record(
                eq(professorId.toString()),
                eq(AuditActorRole.PROFESSOR),
                eq("ASSESSMENT_CONFIG"),
                eq(first.configId()),
                eq("CONFIG_CREATED"),
                eq(null),
                eq("v1"),
                eq(null));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM assessment_config WHERE idempotency_key = ?",
                Long.class, idempotencyKey)).isEqualTo(1L);
    }

    // REQ-IS-14: a double submit with one key saves one configuration, and both responses carry it.
    @Test
    void simultaneousCreatesWithOneKeySaveOneConfigurationAndBothReturnIt() throws Exception {
        UUID professorId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<CreatedAssessment> create = () -> {
                start.await();
                return creationService.create(key, sampleRequest());
            };
            var first = pool.submit(create);
            var second = pool.submit(create);
            start.countDown();

            CreatedAssessment a = first.get();
            CreatedAssessment b = second.get();
            assertThat(a.configId()).isEqualTo(b.configId());
            assertThat(a.created() ^ b.created()).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM assessment_config WHERE idempotency_key = ?",
                Long.class, key)).isEqualTo(1L);
    }

    @Test
    void otherProfessorReusingIdempotencyKeyGets400AndSavesNothing() {
        UUID profA = UUID.randomUUID();
        UUID profB = UUID.randomUUID();
        UUID sharedKey = UUID.randomUUID();

        when(authContextService.currentProfessorId()).thenReturn(profA);
        CreatedAssessment createdByA = creationService.create(sharedKey, sampleRequest());
        clearInvocations(auditService);

        when(authContextService.currentProfessorId()).thenReturn(profB);
        assertThatThrownBy(() -> creationService.create(sharedKey, sampleRequest()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(queryService.ownedBy(createdByA.configId(), profA)).isTrue();
        assertThat(queryService.ownedBy(createdByA.configId(), profB)).isFalse();
    }

    @Test
    void invalidRequestThrowsFieldValidationAndPersistsZeroRowsAndZeroAudit() {
        UUID professorId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);

        CreateAssessmentRequest invalid = new CreateAssessmentRequest(
                sampleRequest().settings(),
                List.of(),
                List.of());

        assertThatThrownBy(() -> creationService.create(key, invalid))
                .isInstanceOf(FieldValidationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM assessment_config WHERE idempotency_key = ?",
                Long.class, key)).isZero();
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void queryServiceBuildsAllFourViewsAndOwnedByAnd404ForMissing() {
        UUID professorId = UUID.randomUUID();
        UUID otherProfessor = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);

        UUID configId = creationService.create(UUID.randomUUID(), sampleRequest()).configId();

        assertThat(queryService.exists(configId)).isTrue();
        assertThat(queryService.ownedBy(configId, professorId)).isTrue();
        assertThat(queryService.ownedBy(configId, otherProfessor)).isFalse();
        assertThat(queryService.ownedBy(UUID.randomUUID(), professorId)).isFalse();

        InterviewerView interviewer = queryService.interviewerView(configId);
        assertThat(interviewer.configId()).isEqualTo(configId);
        assertThat(interviewer.configVersion()).isEqualTo("v1");
        assertThat(interviewer.groupingMode()).isEqualTo(GroupingMode.COMBINED);
        assertThat(interviewer.questions()).hasSize(2);
        assertThat(interviewer.questions().get(0).position()).isEqualTo(1);
        assertThat(interviewer.questions().get(0).targetSkillNames()).containsExactly("Git", "Java");
        assertThat(interviewer.questions().get(1).position()).isEqualTo(2);

        EvaluatorView evaluator = queryService.evaluatorView(configId);
        assertThat(evaluator.skills()).hasSize(2);
        assertThat(evaluator.skills().get(0).rubricLevels()).containsExactlyElementsOf(RUBRIC_5);
        assertThat(evaluator.questions()).hasSize(2);

        ScoringView scoring = queryService.scoringView(configId);
        assertThat(scoring.minCoverage()).isEqualByComparingTo("0.6000");
        assertThat(scoring.questions()).hasSize(2);
        assertThat(scoring.questions().get(0).weight()).isEqualByComparingTo("2.00");
        assertThat(scoring.questions().get(0).confidenceThreshold()).isEqualByComparingTo("0.7500");

        TeamView team = queryService.teamView(configId);
        assertThat(team.teamSize()).isEqualTo(4);
        assertThat(team.groupingMode()).isEqualTo(GroupingMode.COMBINED);
        assertThat(team.softPreset()).isEqualTo(SoftPreset.ENGINEERING_DEFAULT);
        assertThat(team.combinedHardWeight()).isEqualByComparingTo("0.6500");
        assertThat(team.alpha()).isEqualByComparingTo("0.5500");
        assertThat(team.bfi10Enabled()).isTrue();

        UUID missing = UUID.randomUUID();
        assertThat(queryService.exists(missing)).isFalse();
        assertThatThrownBy(() -> queryService.interviewerView(missing))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createDeduplicatesCaseInsensitiveTargetSkillsOnSingleQuestion() {
        UUID professorId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);

        CreateAssessmentRequest dupTargets = new CreateAssessmentRequest(
                sampleRequest().settings(),
                List.of(new SkillInput("Java", RUBRIC_5)),
                List.of(new QuestionInput(1, "Explain JVM memory.", new BigDecimal("1.00"),
                        new BigDecimal("0.7000"), 30, 120, 1, List.of("Java", "java", " JAVA "))));

        UUID configId = creationService.create(UUID.randomUUID(), dupTargets).configId();

        InterviewerView interviewer = queryService.interviewerView(configId);
        assertThat(interviewer.questions().getFirst().targetSkillNames()).containsExactly("Java");
    }

    private static CreateAssessmentRequest sampleRequest() {
        SettingsInput settings = new SettingsInput(
                "SE Block Week",
                "Evaluate backend architecture and teamwork",
                new BigDecimal("0.6000"),
                2,
                true,
                4,
                GroupingMode.COMBINED,
                SoftPreset.ENGINEERING_DEFAULT,
                new BigDecimal("0.6500"),
                new BigDecimal("0.5500"),
                Map.of("balance", new BigDecimal("0.8000")));
        List<SkillInput> skills = List.of(
                new SkillInput("Java", RUBRIC_5),
                new SkillInput("Git", RUBRIC_5));
        List<QuestionInput> questions = List.of(
                new QuestionInput(1, "Explain transaction isolation and branching.",
                        new BigDecimal("2.00"), new BigDecimal("0.7500"), 30, 180, 2, List.of("Java", "Git")),
                new QuestionInput(2, "Walk through a merge conflict resolution.",
                        new BigDecimal("1.00"), new BigDecimal("0.7000"), 20, 120, 1, List.of("Git")));
        return new CreateAssessmentRequest(settings, skills, questions);
    }
}
