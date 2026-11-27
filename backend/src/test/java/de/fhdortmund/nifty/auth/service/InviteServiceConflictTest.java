package de.fhdortmund.nifty.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.repository.InviteEntity;
import de.fhdortmund.nifty.auth.repository.InviteRepository;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/** REQ-AA-11: a unique-key violation on save (a lost race) answers 409, not 500. */
class InviteServiceConflictTest {

    @Test
    void uniqueViolationOnSaveAnswers409() {
        UUID professorId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        InviteRepository repository = mock(InviteRepository.class);
        StudioQueryService studio = mock(StudioQueryService.class);
        AuthContextService auth = mock(AuthContextService.class);
        when(auth.currentProfessorId()).thenReturn(professorId);
        when(studio.ownedBy(configId, professorId)).thenReturn(true);
        when(repository.saveAndFlush(any(InviteEntity.class))).thenThrow(new DataIntegrityViolationException("dup"));
        InviteService service = new InviteService(repository, studio, auth, mock(AuditService.class), "http://x");

        assertThatThrownBy(() -> service.createInvites(configId, List.of("anna.schmidt")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
