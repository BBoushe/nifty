package de.fhdortmund.nifty.assessment.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Append-only ledger access (Decision 1.11): save and read, never update or delete. */
public interface ConversationEntryRepository extends Repository<ConversationEntryEntity, UUID> {

    ConversationEntryEntity save(ConversationEntryEntity entry);

    List<ConversationEntryEntity> findByAttemptIdOrderBySeqAsc(UUID attemptId);
}
