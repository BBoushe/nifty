package de.fhdortmund.nifty.auth.service;

import java.util.UUID;

/** Single created invite returned once on POST; holds the plain link that is never stored in DB (REQ-AA-03). */
public record CreatedInvite(UUID inviteId, String candidateRef, String link) {
}
