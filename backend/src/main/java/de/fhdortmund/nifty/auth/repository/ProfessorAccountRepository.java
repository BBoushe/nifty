package de.fhdortmund.nifty.auth.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Only the {@code auth} slice uses this repository (ArchUnit). Callers pass a normalized email. */
public interface ProfessorAccountRepository extends Repository<ProfessorAccountEntity, UUID> {

    ProfessorAccountEntity save(ProfessorAccountEntity account);

    Optional<ProfessorAccountEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
