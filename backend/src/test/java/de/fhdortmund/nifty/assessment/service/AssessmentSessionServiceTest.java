package de.fhdortmund.nifty.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.auth.service.CandidateSessionPrincipal;
import de.fhdortmund.nifty.context.service.ContextCreationService;
import de.fhdortmund.nifty.shared.exception.DomainException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/** REQ-AC-11: two parallel starts of one invite collide on UNIQUE (invite_id, attempt_no); the loser gets 409. */
class AssessmentSessionServiceTest {

    @Test
    void parallelStartThatLosesTheAttemptNumberRaceIs409NotA500() {
        AttemptService attempts = mock(AttemptService.class);
        AuthContextService auth = mock(AuthContextService.class);
        when(auth.currentCandidate()).thenReturn(new CandidateSessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), "c"));
        when(attempts.start(any())).thenThrow(new DataIntegrityViolationException("uq_attempt"));
        AssessmentSessionService sessions = new AssessmentSessionService(attempts, auth,
                mock(ContextCreationService.class), ChatClient.builder(mock(ChatModel.class)));

        assertThatThrownBy(sessions::start).isInstanceOfSatisfying(DomainException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }
}
