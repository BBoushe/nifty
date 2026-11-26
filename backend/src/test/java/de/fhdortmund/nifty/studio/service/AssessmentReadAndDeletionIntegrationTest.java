package de.fhdortmund.nifty.studio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.audit.service.AuditActorRole;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-IS-07, REQ-IS-10, Decision 4.4: verifies professor-scoped list/detail reads, 404 isolation,
 * and transactional GDPR purge (SET LOCAL nifty.allow_purge = 'on', AssessmentDeletedEvent, CONFIG_DELETED audit).
 */
@SpringBootTest
@ActiveProfiles("test")
class AssessmentReadAndDeletionIntegrationTest extends PostgresContainerBase {

    private static final List<String> RUBRIC_5 = List.of("0", "1", "2", "3", "4");

    @Autowired
    private ConfigCreationService creationService;
    @Autowired
    private AssessmentReadService readService;
    @Autowired
    private AssessmentDeletionService deletionService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DeletionEventProbe eventProbe;

    @MockitoBean
    private AuditService auditService;
    @MockitoBean
    private AuthContextService authContextService;

    @BeforeEach
    void reset() {
        clearInvocations(auditService, authContextService);
        eventProbe.clear();
    }

    @Test
    void listAndGetAreScopedToOwningProfessorAndNewestFirst() {
        UUID profA = UUID.randomUUID();
        UUID profB = UUID.randomUUID();

        when(authContextService.currentProfessorId()).thenReturn(profA);
        UUID firstA = creationService.create(UUID.randomUUID(), requestTitled("First A")).configId();
        UUID secondA = creationService.create(UUID.randomUUID(), requestTitled("Second A")).configId();

        when(authContextService.currentProfessorId()).thenReturn(profB);
        UUID onlyB = creationService.create(UUID.randomUUID(), requestTitled("Only B")).configId();

        when(authContextService.currentProfessorId()).thenReturn(profA);
        List<AssessmentSummaryResponse> listA = readService.listForCurrentProfessor();
        assertThat(listA).extracting(AssessmentSummaryResponse::configId).containsExactly(secondA, firstA);
        assertThat(listA.getFirst().skillCount()).isEqualTo(1);
        assertThat(listA.getFirst().questionCount()).isEqualTo(1);

        AssessmentDetailResponse detail = readService.getForCurrentProfessor(firstA);
        assertThat(detail.title()).isEqualTo("First A");
        assertThat(detail.skills()).hasSize(1);
        assertThat(detail.questions()).hasSize(1);

        assertThatThrownBy(() -> readService.getForCurrentProfessor(onlyB))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteRejectsNonOwnerWith404AndPurgesEverythingForOwnerWithEventAndAudit() {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();

        when(authContextService.currentProfessorId()).thenReturn(owner);
        UUID configId = creationService.create(UUID.randomUUID(), requestTitled("To Purge")).configId();
        clearInvocations(auditService);

        when(authContextService.currentProfessorId()).thenReturn(intruder);
        assertThatThrownBy(() -> deletionService.deleteForCurrentProfessor(configId))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(eventProbe.events()).isEmpty();
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());

        when(authContextService.currentProfessorId()).thenReturn(owner);
        deletionService.deleteForCurrentProfessor(configId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM assessment_config WHERE id = ?", Long.class, configId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM skill WHERE config_id = ?", Long.class, configId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core_question WHERE config_id = ?", Long.class, configId))
                .isZero();
        assertThat(eventProbe.events()).containsExactly(new AssessmentDeletedEvent(configId, owner.toString()));
        assertThat(eventProbe.allowPurgeObserved()).containsExactly("on");

        verify(auditService).record(
                eq(owner.toString()),
                eq(AuditActorRole.PROFESSOR),
                eq("ASSESSMENT_CONFIG"),
                eq(configId),
                eq("CONFIG_DELETED"),
                eq(null),
                eq(null),
                eq(null));
    }

    private static CreateAssessmentRequest requestTitled(String title) {
        SettingsInput settings = new SettingsInput(
                title,
                "Vision for " + title,
                new BigDecimal("0.5000"),
                1,
                false,
                false,
                4,
                GroupingMode.HARD_SKILLS,
                SoftPreset.ENGINEERING_DEFAULT,
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                Map.of());
        return new CreateAssessmentRequest(
                settings,
                List.of(new SkillInput("Java", RUBRIC_5)),
                List.of(new QuestionInput(1, "Q1", new BigDecimal("1.00"), new BigDecimal("0.7000"),
                        30, 120, 1, List.of("Java"))));
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        DeletionEventProbe deletionEventProbe(JdbcTemplate jdbc) {
            return new DeletionEventProbe(jdbc);
        }
    }

    static class DeletionEventProbe {
        private final JdbcTemplate jdbc;
        private final List<AssessmentDeletedEvent> events = new ArrayList<>();
        private final List<String> allowPurgeObserved = new ArrayList<>();

        DeletionEventProbe(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        // Decision 4.4: synchronous @EventListener + MANDATORY verifies we run inside the purge transaction.
        @EventListener
        @Transactional(propagation = Propagation.MANDATORY)
        void onDeleted(AssessmentDeletedEvent event) {
            events.add(event);
            allowPurgeObserved.add(jdbc.queryForObject("SELECT current_setting('nifty.allow_purge', true)",
                    String.class));
        }

        List<AssessmentDeletedEvent> events() {
            return List.copyOf(events);
        }

        List<String> allowPurgeObserved() {
            return List.copyOf(allowPurgeObserved);
        }

        void clear() {
            events.clear();
            allowPurgeObserved.clear();
        }
    }
}
