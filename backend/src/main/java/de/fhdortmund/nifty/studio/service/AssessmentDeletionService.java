package de.fhdortmund.nifty.studio.service;

import de.fhdortmund.nifty.audit.service.AuditActorRole;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.service.AuthContextService;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.studio.repository.AssessmentConfigRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-IS-07 / Decision 4.4: GDPR assessment purge. Verifies professor ownership, sets
 * {@code SET LOCAL nifty.allow_purge = 'on'}, publishes {@link AssessmentDeletedEvent} synchronously
 * in the same transaction, deletes all studio rows, and writes one {@code CONFIG_DELETED} audit row.
 */
@Service
@Transactional(readOnly = true)
public class AssessmentDeletionService {

    private static final String NOT_FOUND = "Assessment configuration not found.";

    private final AssessmentConfigRepository configRepository;
    private final AuthContextService authContext;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbc;

    AssessmentDeletionService(
            AssessmentConfigRepository configRepository,
            AuthContextService authContext,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            JdbcTemplate jdbc) {
        this.configRepository = configRepository;
        this.authContext = authContext;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.jdbc = jdbc;
    }

    @Transactional
    public void deleteForCurrentProfessor(UUID configId) {
        UUID professorId = authContext.currentProfessorId();
        if (!configRepository.existsByIdAndProfessorId(configId, professorId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }

        // Decision 4.4: unlock immutability triggers for this transaction only, then notify every slice.
        jdbc.execute("SET LOCAL nifty.allow_purge = 'on'");
        eventPublisher.publishEvent(new AssessmentDeletedEvent(configId, professorId.toString()));
        configRepository.purgeById(configId);

        // Audit row holds zero candidate PII.
        auditService.record(
                professorId.toString(),
                AuditActorRole.PROFESSOR,
                "ASSESSMENT_CONFIG",
                configId,
                "CONFIG_DELETED",
                null,
                null,
                null);
    }
}
