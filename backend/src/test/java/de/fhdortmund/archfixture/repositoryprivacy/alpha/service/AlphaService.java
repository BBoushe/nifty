package de.fhdortmund.archfixture.repositoryprivacy.alpha.service;

import de.fhdortmund.archfixture.repositoryprivacy.beta.repository.BetaRepository;

/** Violates REQ-BA-02: feature alpha uses beta's repository. */
public class AlphaService {
    private final BetaRepository repository = new BetaRepository();
}
