package de.fhdortmund.nifty.auth.service;

import de.fhdortmund.nifty.audit.service.AuditActorRole;
import de.fhdortmund.nifty.audit.service.AuditService;
import de.fhdortmund.nifty.auth.repository.InviteEntity;
import de.fhdortmund.nifty.auth.repository.InviteRepository;
import de.fhdortmund.nifty.shared.config.AssessmentDeletedEvent;
import de.fhdortmund.nifty.shared.exception.DomainException;
import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import de.fhdortmund.nifty.studio.service.StudioQueryService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-AA-03, REQ-AA-08, REQ-AA-09: professor-scoped invite creation, listing, and revocation.
 * Zero Java Streams in validation/mapping loops (Coding Standard #3).
 */
@Service
@Transactional(readOnly = true)
public class InviteService {

    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_REF_LENGTH = 256;
    private static final String CONFIG_NOT_FOUND = "Assessment configuration not found.";
    private static final String INVITE_NOT_FOUND = "Invite not found.";
    private static final String ALREADY_INVITED = "This candidate already has an active invite.";

    private final InviteRepository inviteRepository;
    private final StudioQueryService studioQueryService;
    private final AuthContextService authContextService;
    private final AuditService auditService;
    private final String inviteLinkPrefix;

    InviteService(
            InviteRepository inviteRepository,
            StudioQueryService studioQueryService,
            AuthContextService authContextService,
            AuditService auditService,
            @Value("${nifty.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.inviteRepository = inviteRepository;
        this.studioQueryService = studioQueryService;
        this.authContextService = authContextService;
        this.auditService = auditService;
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        this.inviteLinkPrefix = base + "/invite/";
    }

    @Transactional
    public List<CreatedInvite> createInvites(UUID configId, List<String> candidateRefs) {
        UUID professorId = requireOwnedConfig(configId);
        List<String> trimmedRefs = validateCandidateRefs(candidateRefs);
        Map<String, InviteEntity> revoked = revokedInvitesOrConflict(configId, trimmedRefs);

        List<CreatedInvite> created = new ArrayList<>(trimmedRefs.size());
        for (int i = 0; i < trimmedRefs.size(); i++) {
            String ref = trimmedRefs.get(i);
            String plainToken = TokenHasher.generateToken();
            String tokenHash = TokenHasher.sha256Hex(plainToken);
            InviteEntity invite = revoked.get(ref);
            if (invite == null) {
                invite = new InviteEntity(configId, ref, tokenHash, professorId);
            } else {
                // Decision 2.34: same row, new token. Attempts and results stay linked to the invite.
                invite.reissue(tokenHash);
                auditService.record(professorId.toString(), AuditActorRole.PROFESSOR, "INVITE", invite.getId(),
                        "INVITE_REISSUED", null, null, null);
            }
            InviteEntity saved = saveOrConflict(invite);
            created.add(new CreatedInvite(saved.getId(), saved.getCandidateRef(), inviteLinkPrefix + plainToken));
        }
        return List.copyOf(created);
    }

    // REQ-AA-11: the unique keys decide a race. The loser answers 409, and the whole batch rolls back.
    private InviteEntity saveOrConflict(InviteEntity invite) {
        try {
            return inviteRepository.saveAndFlush(invite);
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException(HttpStatus.CONFLICT, ALREADY_INVITED);
        }
    }

    /** The revoked invites of these refs by ref. A ref with an active invite answers 409 (Decision 2.34). */
    private Map<String, InviteEntity> revokedInvitesOrConflict(UUID configId, List<String> refs) {
        List<InviteEntity> rows = inviteRepository.findByConfigIdAndCandidateRefIn(configId, refs);
        Map<String, InviteEntity> revoked = new HashMap<>(rows.size() * 2);
        for (int i = 0; i < rows.size(); i++) {
            InviteEntity row = rows.get(i);
            if (!row.isRevoked()) {
                throw new DomainException(HttpStatus.CONFLICT, ALREADY_INVITED + " " + row.getCandidateRef());
            }
            revoked.put(row.getCandidateRef(), row);
        }
        return revoked;
    }

    public List<InviteSummary> listInvites(UUID configId) {
        requireOwnedConfig(configId);
        List<InviteEntity> rows = inviteRepository.findByConfigIdOrderByCreatedAtDesc(configId);
        List<InviteSummary> summaries = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            InviteEntity row = rows.get(i);
            summaries.add(new InviteSummary(row.getId(), row.getCandidateRef(), row.isRevoked(), row.getCreatedAt()));
        }
        return List.copyOf(summaries);
    }

    /** REQ-SM-12: candidate names by invite ID for the ranking views of one owned config (revoked invites included). */
    public Map<UUID, String> candidateRefs(UUID configId) {
        requireOwnedConfig(configId);
        List<InviteEntity> rows = inviteRepository.findByConfigIdOrderByCreatedAtDesc(configId);
        Map<UUID, String> refs = new HashMap<>(rows.size() * 2);
        for (int i = 0; i < rows.size(); i++) {
            refs.put(rows.get(i).getId(), rows.get(i).getCandidateRef());
        }
        return refs;
    }

    @Transactional
    public void revokeInvite(UUID configId, UUID inviteId) {
        requireOwnedConfig(configId);
        InviteEntity invite = inviteRepository.findByIdAndConfigId(inviteId, configId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, INVITE_NOT_FOUND));
        if (!invite.isRevoked()) {
            invite.revoke();
            inviteRepository.save(invite);
        }
    }

    /** Decision 4.4: synchronous purge inside the assessment deletion transaction. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onAssessmentDeleted(AssessmentDeletedEvent event) {
        inviteRepository.deleteByConfigId(event.configId());
    }

    private UUID requireOwnedConfig(UUID configId) {
        UUID professorId = authContextService.currentProfessorId();
        if (configId == null || !studioQueryService.ownedBy(configId, professorId)) {
            throw new DomainException(HttpStatus.NOT_FOUND, CONFIG_NOT_FOUND);
        }
        return professorId;
    }

    private List<String> validateCandidateRefs(List<String> candidateRefs) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (candidateRefs == null || candidateRefs.isEmpty()) {
            errors.add(new FieldErrorDetail("candidateRefs", "must contain between 1 and 500 candidates", "REQUIRED"));
            throw new FieldValidationException(errors);
        }
        if (candidateRefs.size() > MAX_BATCH_SIZE) {
            errors.add(new FieldErrorDetail("candidateRefs", "must contain at most 500 candidates", "OUT_OF_RANGE"));
            throw new FieldValidationException(errors);
        }

        List<String> trimmed = new ArrayList<>(candidateRefs.size());
        Set<String> seen = new HashSet<>(candidateRefs.size());
        for (int i = 0; i < candidateRefs.size(); i++) {
            String raw = candidateRefs.get(i);
            String field = "candidateRefs[" + i + "]";
            if (raw == null || raw.isBlank()) {
                errors.add(new FieldErrorDetail(field, "must not be blank", "REQUIRED"));
                continue;
            }
            String clean = raw.trim();
            if (clean.length() > MAX_REF_LENGTH) {
                errors.add(new FieldErrorDetail(field, "must be at most 256 characters", "OUT_OF_RANGE"));
                continue;
            }
            if (!seen.add(clean)) {
                errors.add(new FieldErrorDetail(field, "duplicate candidateRef: " + clean, "DUPLICATE_CANDIDATE_REF"));
                continue;
            }
            trimmed.add(clean);
        }

        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return trimmed;
    }
}
