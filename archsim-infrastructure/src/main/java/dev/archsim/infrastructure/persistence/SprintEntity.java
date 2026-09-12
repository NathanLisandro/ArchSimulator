package dev.archsim.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "sprints")
public class SprintEntity {
    @Id private String id;
    @Column(name = "user_id", nullable = false) private String userId;
    @Column(nullable = false) private String status;
    @Column(name = "total_cases", nullable = false) private int totalCases;
    @Column(name = "time_limit_secs", nullable = false) private long timeLimitSeconds;
    @Column(name = "level", nullable = false) private String level;
    @Column(name = "case_type") private String caseType;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String document;

    protected SprintEntity() {}
    public SprintEntity(String id, String userId, String status, int totalCases, long timeLimitSeconds, String level,
                        String caseType, Instant startedAt, Instant finishedAt, String document) {
        this.id = id; this.userId = userId; this.status = status; this.totalCases = totalCases;
        this.timeLimitSeconds = timeLimitSeconds; this.level = level; this.caseType = caseType;
        this.startedAt = startedAt; this.finishedAt = finishedAt; this.document = document;
    }
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getStatus() { return status; }
    public int getTotalCases() { return totalCases; }
    public long getTimeLimitSeconds() { return timeLimitSeconds; }
    public String getLevel() { return level; }
    public String getCaseType() { return caseType; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getDocument() { return document; }
}
