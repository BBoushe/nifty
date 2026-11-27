package de.fhdortmund.nifty.auth.service;

import java.util.UUID;

public record CandidateSessionPrincipal(UUID inviteId, UUID configId, String candidateRef, String tokenHash) {
}
