package de.fhdortmund.nifty.auth.service;

import java.time.Instant;
import java.util.UUID;

/** Listed invite without any token or hash (REQ-AA-03). */
public record InviteSummary(UUID inviteId, String candidateRef, boolean revoked, Instant createdAt) {
}
