package de.fhdortmund.nifty.studio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.repository.AssessmentConfigEntity;
import de.fhdortmund.nifty.studio.repository.AssessmentConfigRepository;
import de.fhdortmund.nifty.studio.repository.CoreQuestionRepository;
import de.fhdortmund.nifty.studio.repository.SkillRepository;
import de.fhdortmund.nifty.studio.service.view.GroupingMode;
import de.fhdortmund.nifty.studio.service.view.SoftPreset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQ-IS-01, REQ-IS-12, REQ-IS-14: when the idempotency-key unique constraint rejects the insert (a lost race),
 * the service re-reads the winner. It replays for the same professor and answers 400 for another professor.
 */
class ConfigCreationRaceTest {

    private final UUID key = UUID.randomUUID();
    private final UUID winnerId = UUID.randomUUID();
    private final UUID winnerProfessor = UUID.randomUUID();
    private final AssessmentConfigRepository configRepository = mock(AssessmentConfigRepository.class);
    private final AuthContextService auth = mock(AuthContextService.class);

    @Test
    void lostRaceForTheSameProfessorReplaysTheWinner() {
        raceLostTo(winnerProfessor);
        when(auth.currentProfessorId()).thenReturn(winnerProfessor);

        CreatedAssessment result = service().create(key, request());

        assertThat(result).isEqualTo(new CreatedAssessment(winnerId, false));
    }

    @Test
    void lostRaceForAnotherProfessorAnswers400() {
        raceLostTo(winnerProfessor);
        when(auth.currentProfessorId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service().create(key, request()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // The first look-up misses (the winner has not committed yet). The insert fails. The second look-up hits.
    private void raceLostTo(UUID professorId) {
        AssessmentConfigEntity winner = mock(AssessmentConfigEntity.class);
        when(winner.getId()).thenReturn(winnerId);
        when(winner.getProfessorId()).thenReturn(professorId);
        when(configRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty(), Optional.of(winner));
        when(configRepository.save(any(AssessmentConfigEntity.class)))
                .thenThrow(new DataIntegrityViolationException("idempotency_key"));
    }

    private ConfigCreationService service() {
        return new ConfigCreationService(configRepository, mock(SkillRepository.class),
                mock(CoreQuestionRepository.class), mock(ConfigValidator.class), auth, mock(AuditService.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private static CreateAssessmentRequest request() {
        SettingsInput settings = new SettingsInput("Title", "Vision", new BigDecimal("0.6000"), 2, true, 4,
                GroupingMode.COMBINED, SoftPreset.ENGINEERING_DEFAULT, new BigDecimal("0.6500"),
                new BigDecimal("0.5500"), Map.of());
        return new CreateAssessmentRequest(settings, List.of(), List.of());
    }
}
