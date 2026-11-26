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

        assertThatThrownBy(() -> inviteService.createInvites(configId, List.of("new_1", "existing_1")))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(ex -> assertThat(((FieldValidationException) ex).getErrors())
                        .anySatisfy(err -> assertThat(err.message()).contains("existing_1")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM invite WHERE config_id = ?", Long.class, configId))
                .isEqualTo(1L);
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
}
