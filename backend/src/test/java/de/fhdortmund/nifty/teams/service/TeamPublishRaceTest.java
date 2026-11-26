package de.fhdortmund.nifty.teams.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.assessment.service.AssessmentQueryService;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.auth.service.InviteService;
import de.fhdortmund.nifty.evaluation.service.CandidateHardSkillProfile;
import de.fhdortmund.nifty.evaluation.service.CandidateProfilesView;
import de.fhdortmund.nifty.evaluation.service.EvaluationQueryService;
import de.fhdortmund.nifty.evaluation.service.ReleaseStatus;
import de.fhdortmund.nifty.evaluation.service.ScoreStatus;
import de.fhdortmund.nifty.evaluation.service.SkillScoreView;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import de.fhdortmund.nifty.studio.service.view.TeamView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * REQ-TB-04: two concurrent publishes of one proposal write exactly one audit row and one gets 409. Real
 * committed transactions on PostgreSQL; the audit mock holds each caller for up to a second so that, without a row
 * lock, both callers would read DRAFT before either commits.
 */
@SpringBootTest
@ActiveProfiles("test")
class TeamPublishRaceTest extends PostgresContainerBase {

    private final UUID professor = UUID.randomUUID();
    private final UUID configId = UUID.randomUUID();

    @Autowired
    private TeamBuilderService service;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private StudioQueryService studio;
    @MockitoBean
    private EvaluationQueryService evaluation;
    @MockitoBean
    private AssessmentQueryService assessment;
    @MockitoBean
    private AuditService audit;
    @MockitoBean
    private AuthContextService authContext;
    @MockitoBean
    private InviteService inviteService;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM team_proposal WHERE config_id = ?", configId);
    }

    @Test
    void concurrentPublishesWriteOneAuditRowAndOneGets409() throws Exception {
        when(authContext.currentProfessorId()).thenReturn(professor);
        when(studio.ownedBy(configId, professor)).thenReturn(true);
        when(studio.teamView(configId)).thenReturn(new TeamView(configId, 2, GroupingMode.HARD_SKILLS,
                SoftPreset.ENGINEERING_DEFAULT, new BigDecimal("0.5"), new BigDecimal("0.5"), Map.of(), false));
        UUID skill = UUID.randomUUID();
        List<CandidateHardSkillProfile> released = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            released.add(new CandidateHardSkillProfile(UUID.randomUUID(), UUID.randomUUID(), ReleaseStatus.APPROVED,
                    new BigDecimal("2.00"), new BigDecimal("1.0000"), List.of(new SkillScoreView(
                    skill, "git", ScoreStatus.SCORED, BigDecimal.valueOf(i)))));
        }
        when(evaluation.getCandidateProfilesForConfig(configId)).thenReturn(new CandidateProfilesView(released, List.of()));
        UUID proposalId = service.generate(configId, new GenerateTeamsRequest(2, null, null, null, 1L, null)).proposalId();

        CountDownLatch bothInAudit = new CountDownLatch(2);
        doAnswer(invocation -> {
            bothInAudit.countDown();
            bothInAudit.await(1, TimeUnit.SECONDS);
            return null;
        }).when(audit).record(any(), any(), any(), any(), any(), any(), any(), any());

        Callable<Object> publish = () -> {
            try {
                return service.publish(proposalId).status();
            } catch (DomainException ex) {
                return ex.getStatus();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(publish);
            Future<Object> second = pool.submit(publish);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("PUBLISHED", HttpStatus.CONFLICT);
        } finally {
            pool.shutdownNow();
        }
        verify(audit, times(1)).record(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
