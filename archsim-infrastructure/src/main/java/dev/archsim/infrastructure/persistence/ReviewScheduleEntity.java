package dev.archsim.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "review_schedules")
@IdClass(ReviewScheduleEntity.Key.class)
public class ReviewScheduleEntity {
    @Id @Column(name = "user_id") private String userId;
    @Id @Column(name = "card_id") private String cardId;
    @Column(nullable = false) private int repetitions;
    @Column(name = "interval_days", nullable = false) private int intervalDays;
    @Column(name = "ease_factor", nullable = false) private BigDecimal easeFactor;
    @Column(nullable = false) private int lapses;
    @Column(name = "due_at", nullable = false) private Instant dueAt;
    @Column(name = "last_reviewed_at") private Instant lastReviewedAt;

    protected ReviewScheduleEntity() {}
    public ReviewScheduleEntity(String userId, String cardId, int repetitions, int intervalDays, BigDecimal easeFactor,
                                int lapses, Instant dueAt, Instant lastReviewedAt) {
        this.userId = userId; this.cardId = cardId; this.repetitions = repetitions; this.intervalDays = intervalDays;
        this.easeFactor = easeFactor; this.lapses = lapses; this.dueAt = dueAt; this.lastReviewedAt = lastReviewedAt;
    }
    public String getUserId() { return userId; }
    public String getCardId() { return cardId; }
    public int getRepetitions() { return repetitions; }
    public int getIntervalDays() { return intervalDays; }
    public BigDecimal getEaseFactor() { return easeFactor; }
    public int getLapses() { return lapses; }
    public Instant getDueAt() { return dueAt; }
    public Instant getLastReviewedAt() { return lastReviewedAt; }

    public record Key(String userId, String cardId) implements Serializable {
        public Key() { this(null, null); }
        @Override public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(cardId, k.cardId);
        }
        @Override public int hashCode() { return Objects.hash(userId, cardId); }
    }
}
