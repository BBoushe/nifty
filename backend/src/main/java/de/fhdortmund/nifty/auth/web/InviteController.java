package de.fhdortmund.nifty.auth.web;

import de.fhdortmund.nifty.auth.service.CreatedInvite;
import de.fhdortmund.nifty.auth.service.InviteService;
import de.fhdortmund.nifty.auth.service.InviteSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PROFESSOR-only invite link management scoped to owned assessments (REQ-AA-03, REQ-AA-08, REQ-AA-09). */
@RestController
@RequestMapping("/api/v1/ranking/studio/configs/{configId}/invites")
@PreAuthorize("hasRole('PROFESSOR')")
class InviteController {

    private final InviteService inviteService;

    InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping
    ResponseEntity<List<CreatedInvite>> create(
            @PathVariable UUID configId,
            @RequestBody CreateInvitesRequest body) {
        List<CreatedInvite> created = inviteService.createInvites(configId, body == null ? null : body.candidateRefs());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    List<InviteSummary> list(@PathVariable UUID configId) {
        return inviteService.listInvites(configId);
    }

    @DeleteMapping("/{inviteId}")
    ResponseEntity<Void> revoke(@PathVariable UUID configId, @PathVariable UUID inviteId) {
        inviteService.revokeInvite(configId, inviteId);
        return ResponseEntity.noContent().build();
    }

    record CreateInvitesRequest(List<String> candidateRefs) {
    }
}
