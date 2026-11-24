package de.fhdortmund.archfixture.entityexposure.alpha.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AlphaEntity {
    @Id
    private Long id;
}
