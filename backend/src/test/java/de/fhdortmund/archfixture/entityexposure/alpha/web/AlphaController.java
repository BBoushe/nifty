package de.fhdortmund.archfixture.entityexposure.alpha.web;

import de.fhdortmund.archfixture.entityexposure.alpha.repository.AlphaEntity;
import org.springframework.web.bind.annotation.RestController;

/** Violates REQ-BA-04: a controller method returns an entity. */
@RestController
public class AlphaController {
    public AlphaEntity find() {
        return new AlphaEntity();
    }
}
