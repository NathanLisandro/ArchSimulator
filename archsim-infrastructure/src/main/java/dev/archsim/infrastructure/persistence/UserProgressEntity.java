package dev.archsim.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_progress")
public class UserProgressEntity {
    @Id @Column(name = "user_id") private String userId;
    @Column(name = "rank", nullable = false) private String rank;
    @Column(nullable = false) private long xp;
    @Column(name = "solved_count", nullable = false) private int solvedCount;
    @Column(name = "total_score", nullable = false) private BigDecimal totalScore;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String document;

    protected UserProgressEntity() {}
    public UserProgressEntity(String userId, String rank, long xp, int solvedCount, BigDecimal totalScore, Instant updatedAt, String document) {
        this.userId = userId; this.rank = rank; this.xp = xp; this.solvedCount = solvedCount;
        this.totalScore = totalScore; this.updatedAt = updatedAt; this.document = document;
    }
    public String getUserId() { return userId; }
    public String getRank() { return rank; }
    public long getXp() { return xp; }
    public int getSolvedCount() { return solvedCount; }
    public BigDecimal getTotalScore() { return totalScore; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getDocument() { return document; }
}
