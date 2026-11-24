package de.fhdortmund.archfixture.layout.alpha.service;

import org.springframework.web.bind.annotation.RestController;

/** Violates REQ-BA-01: a controller outside web. */
@RestController
public class MisplacedController {
}
