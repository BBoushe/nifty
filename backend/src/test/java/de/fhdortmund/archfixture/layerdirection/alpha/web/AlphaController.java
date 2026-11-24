package de.fhdortmund.archfixture.layerdirection.alpha.web;

import de.fhdortmund.archfixture.layerdirection.alpha.repository.AlphaRecord;

/** Violates REQ-BA-03: web uses repository. */
public class AlphaController {
    private final AlphaRecord record = new AlphaRecord();
}
