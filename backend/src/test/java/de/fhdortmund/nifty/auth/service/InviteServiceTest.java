package de.fhdortmund.nifty.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.PostgresContainerBase;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * REQ-AA-03, REQ-AA-09: invite creation generates >=128-bit random tokens, stores only SHA-256 hashes,
 * rejects duplicate candidateRefs with 400, and rejects unowned configs with 404.
 */
@SpringBootTest
@ActiveProfiles("test")
class InviteServiceTest extends PostgresContainerBase {

    @Autowired
    private InviteService inviteService;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private StudioQueryService studioQueryService;
    @MockitoBean
    private AuthContextService authContextService;

    @Test
    void createsThreeDistinctLinksAndStoresOnlyHashesNoPlainTokens() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);

        List<CreatedInvite> created = inviteService.createInvites(configId, List.of("cand_1", "cand_2", "cand_3"));

        assertThat(created).hasSize(3);
        assertThat(created).extracting(CreatedInvite::candidateRef).containsExactly("cand_1", "cand_2", "cand_3");
        assertThat(created).extracting(CreatedInvite::link).doesNotHaveDuplicates();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, candidate_ref, token_hash, revoked FROM invite WHERE config_id = ?", configId);
        assertThat(rows).hasSize(3);

        for (CreatedInvite item : created) {
            String token = item.link().substring(item.link().lastIndexOf('/') + 1);
            // 32 random bytes base64url without padding = 43 chars (256 bits >= 128 bits).
            assertThat(token.length()).isGreaterThanOrEqualTo(43);
            String expectedHash = TokenHasher.sha256Hex(token);
            assertThat(rows)
                    .anySatisfy(row -> {
                        assertThat(row.get("candidate_ref")).isEqualTo(item.candidateRef());
                        assertThat(row.get("token_hash")).isEqualTo(expectedHash);
                        assertThat(row.get("revoked")).isEqualTo(false);
                    });
            // Plain token must never appear in the database.
            assertThat(rows).noneSatisfy(row -> assertThat(row.get("token_hash")).isEqualTo(token));
        }

        List<InviteSummary> listed = inviteService.listInvites(configId);
        assertThat(listed).hasSize(3);
        assertThat(listed).extracting(InviteSummary::candidateRef)
                .containsExactlyInAnyOrder("cand_1", "cand_2", "cand_3");
    }

    @Test
    void rejectsDuplicateCandidateRefInRequestOrDatabaseWith400() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);

        assertThatThrownBy(() -> inviteService.createInvites(configId, List.of("dup_1", " dup_1 ")))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(ex -> assertThat(((FieldValidationException) ex).getErrors())
                        .anySatisfy(err -> assertThat(err.message()).contains("dup_1")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM invite WHERE config_id = ?", Long.class, configId))
                .isZero();

        inviteService.createInvites(configId, List.of("existing_1"));

        // REQ-AA-11 / Decision 2.34: an existing, not revoked invite answers 409, and nothing is created.
        assertThatThrownBy(() -> inviteService.createInvites(configId, List.of("new_1", "existing_1")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM invite WHERE config_id = ?", Long.class, configId))
                .isEqualTo(1L);
    }

    // REQ-AA-10 / Decision 2.34: the same row gets a new token, so attempts stay linked to the invite.
    @Test
    void reinviteOfARevokedCandidateRotatesTheTokenOnTheSameRowAndAudits() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        CreatedInvite first = inviteService.createInvites(configId, List.of("anna.schmidt")).getFirst();
        String oldHash = tokenHashOf(first.inviteId());
        inviteService.revokeInvite(configId, first.inviteId());

        CreatedInvite second = inviteService.createInvites(configId, List.of("anna.schmidt", "ben.meier")).getFirst();

        assertThat(second.inviteId()).isEqualTo(first.inviteId());
        assertThat(second.link()).isNotEqualTo(first.link());
        assertThat(tokenHashOf(first.inviteId())).isNotEqualTo(oldHash)
                .isEqualTo(TokenHasher.sha256Hex(second.link().substring(second.link().lastIndexOf('/') + 1)));
        assertThat(jdbc.queryForObject("SELECT revoked FROM invite WHERE id = ?", Boolean.class, first.inviteId()))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invite WHERE config_id = ?", Long.class, configId))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'INVITE_REISSUED' AND entity_id = ?",
                Long.class, first.inviteId().toString())).isEqualTo(1L);
    }

    // REQ-AA-11: two simultaneous creates of one candidate give one 201 and one 409, never a 500.
    @Test
    void simultaneousCreatesOfOneCandidateGiveOneInviteAndOneConflict() throws Exception {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Object> create = () -> {
                start.await();
                try {
                    return inviteService.createInvites(configId, List.of("double.click"));
                } catch (DomainException ex) {
                    return ex.getStatus();
                }
            };
            var first = pool.submit(create);
            var second = pool.submit(create);
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .filteredOn(HttpStatus.CONFLICT::equals).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invite WHERE config_id = ?", Long.class, configId))
                .isEqualTo(1L);
    }

    private String tokenHashOf(UUID inviteId) {
        return jdbc.queryForObject("SELECT token_hash FROM invite WHERE id = ?", String.class, inviteId);
    }

    // REQ-SM-12: other slices read candidate names through this public method, never the repository.
    @Test
    void candidateRefsMapsInviteIdToRefForAnOwnedConfig() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);
        List<CreatedInvite> created = inviteService.createInvites(configId, List.of("anna.schmidt", "ben.meier"));

        Map<UUID, String> refs = inviteService.candidateRefs(configId);

        assertThat(refs).containsEntry(created.get(0).inviteId(), "anna.schmidt")
                .containsEntry(created.get(1).inviteId(), "ben.meier")
                .hasSize(2);
    }

    @Test
    void rejectsUnownedOrUnknownConfigWith404AndChangesNothing() {
        UUID professorId = UUID.randomUUID();
        UUID foreignConfigId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(foreignConfigId, professorId)).thenReturn(false);

        assertThatThrownBy(() -> inviteService.createInvites(foreignConfigId, List.of("cand_x")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> inviteService.listInvites(foreignConfigId))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> inviteService.revokeInvite(foreignConfigId, UUID.randomUUID()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM invite WHERE config_id = ?", Long.class, foreignConfigId))
                .isZero();
    }

    @Test
    void rejectsEmptyOverMaxBlankAndOverlongCandidateRefsWith400() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configId, professorId)).thenReturn(true);

        assertThatThrownBy(() -> inviteService.createInvites(configId, List.of()))
                .isInstanceOf(FieldValidationException.class);

        java.util.ArrayList<String> tooMany = new java.util.ArrayList<>(501);
        for (int i = 0; i < 501; i++) {
            tooMany.add("cand_" + i);
        }
        assertThatThrownBy(() -> inviteService.createInvites(configId, tooMany))
                .isInstanceOf(FieldValidationException.class);

        assertThatThrownBy(() -> inviteService.createInvites(configId, List.of("   ")))
                .isInstanceOf(FieldValidationException.class);

        assertThatThrownBy(() -> inviteService.createInvites(configId, List.of("a".repeat(257))))
                .isInstanceOf(FieldValidationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM invite WHERE config_id = ?", Long.class, configId))
                .isZero();
    }

    @Test
    void rejectsCrossConfigInviteRevocationWith404() {
        UUID professorId = UUID.randomUUID();
        UUID configA = UUID.randomUUID();
        UUID configB = UUID.randomUUID();
        when(authContextService.currentProfessorId()).thenReturn(professorId);
        when(studioQueryService.ownedBy(configA, professorId)).thenReturn(true);
        when(studioQueryService.ownedBy(configB, professorId)).thenReturn(true);

        CreatedInvite inviteA = inviteService.createInvites(configA, List.of("alice_cross")).get(0);

        assertThatThrownBy(() -> inviteService.revokeInvite(configB, inviteA.inviteId()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(inviteService.listInvites(configA))
                .singleElement()
                .extracting(InviteSummary::revoked)
                .isEqualTo(false);
    }
}
