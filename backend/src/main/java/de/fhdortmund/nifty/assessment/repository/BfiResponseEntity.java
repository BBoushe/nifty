package de.fhdortmund.nifty.assessment.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** The 10 raw BFI-10 answers (1..5) and the 5 trait means scored once in Java (Decision 4.3). */
@Entity
@Immutable
@Table(name = "bfi_response")
public class BfiResponseEntity {

    @Id
    @Column(name = "attempt_id")
    private UUID attemptId;

    @Column(name = "item_1", nullable = false)
    private short item1;
    @Column(name = "item_2", nullable = false)
    private short item2;
    @Column(name = "item_3", nullable = false)
    private short item3;
    @Column(name = "item_4", nullable = false)
    private short item4;
    @Column(name = "item_5", nullable = false)
    private short item5;
    @Column(name = "item_6", nullable = false)
    private short item6;
    @Column(name = "item_7", nullable = false)
    private short item7;
    @Column(name = "item_8", nullable = false)
    private short item8;
    @Column(name = "item_9", nullable = false)
    private short item9;
    @Column(name = "item_10", nullable = false)
    private short item10;

    @Column(name = "conscientiousness", nullable = false, precision = 4, scale = 2)
    private BigDecimal conscientiousness;
    @Column(name = "agreeableness", nullable = false, precision = 4, scale = 2)
    private BigDecimal agreeableness;
    @Column(name = "neuroticism", nullable = false, precision = 4, scale = 2)
    private BigDecimal neuroticism;
    @Column(name = "extraversion", nullable = false, precision = 4, scale = 2)
    private BigDecimal extraversion;
    @Column(name = "openness", nullable = false, precision = 4, scale = 2)
    private BigDecimal openness;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected BfiResponseEntity() {
    }

    /** {@code items} holds the 10 answers in item order (index 0 = item 1). */
    public BfiResponseEntity(UUID attemptId, int[] items, BigDecimal conscientiousness, BigDecimal agreeableness,
            BigDecimal neuroticism, BigDecimal extraversion, BigDecimal openness, Instant submittedAt) {
        this.attemptId = attemptId;
        this.item1 = (short) items[0];
        this.item2 = (short) items[1];
        this.item3 = (short) items[2];
        this.item4 = (short) items[3];
        this.item5 = (short) items[4];
        this.item6 = (short) items[5];
        this.item7 = (short) items[6];
        this.item8 = (short) items[7];
        this.item9 = (short) items[8];
        this.item10 = (short) items[9];
        this.conscientiousness = conscientiousness;
        this.agreeableness = agreeableness;
        this.neuroticism = neuroticism;
        this.extraversion = extraversion;
        this.openness = openness;
        this.submittedAt = submittedAt;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public int[] getItems() {
        return new int[] {item1, item2, item3, item4, item5, item6, item7, item8, item9, item10};
    }

    public BigDecimal getConscientiousness() {
        return conscientiousness;
    }

    public BigDecimal getAgreeableness() {
        return agreeableness;
    }

    public BigDecimal getNeuroticism() {
        return neuroticism;
    }

    public BigDecimal getExtraversion() {
        return extraversion;
    }

    public BigDecimal getOpenness() {
        return openness;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
