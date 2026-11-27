package de.fhdortmund.nifty.assessment.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One append-only ledger row (Decision 1.11). A database trigger rejects UPDATE and unflagged DELETE. */
@Entity
@Immutable
@Table(name = "conversation_entry")
public class ConversationEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "author", nullable = false, length = 16)
    private String author;

    @Column(name = "core_question_id")
    private UUID coreQuestionId;

    @Column(name = "origin", nullable = false, length = 16)
    private String origin;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_skills", nullable = false, columnDefinition = "jsonb")
    private List<UUID> targetSkills;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "timed_out", nullable = false)
    private boolean timedOut;

    @Column(name = "paste_suspected", nullable = false)
    private boolean pasteSuspected;

    @Column(name = "injection_suspected", nullable = false)
    private boolean injectionSuspected;

    protected ConversationEntryEntity() {
    }

    public ConversationEntryEntity(UUID attemptId, int seq, String author, UUID coreQuestionId, String origin,
            String content, List<UUID> targetSkills, Instant createdAt, Instant expiresAt, boolean timedOut,
            boolean pasteSuspected, boolean injectionSuspected) {
        this.attemptId = attemptId;
        this.seq = seq;
        this.author = author;
        this.coreQuestionId = coreQuestionId;
        this.origin = origin;
        this.content = content;
        this.targetSkills = List.copyOf(targetSkills);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.timedOut = timedOut;
        this.pasteSuspected = pasteSuspected;
        this.injectionSuspected = injectionSuspected;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public int getSeq() {
        return seq;
    }

    public String getAuthor() {
        return author;
    }

    public UUID getCoreQuestionId() {
        return coreQuestionId;
    }

    public String getOrigin() {
        return origin;
    }

    public String getContent() {
        return content;
    }

    public List<UUID> getTargetSkills() {
        return List.copyOf(targetSkills);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public boolean isPasteSuspected() {
        return pasteSuspected;
    }

    public boolean isInjectionSuspected() {
        return injectionSuspected;
    }
}
