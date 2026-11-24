package de.fhdortmund.archfixture.sharedban.shared.config;

import de.fhdortmund.archfixture.sharedban.alpha.service.AlphaService;

/** Violates REQ-BA-03: shared depends on a feature. */
public class SharedConfig {
    private final AlphaService service = new AlphaService();
}
