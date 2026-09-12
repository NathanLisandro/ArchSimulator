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
@Table(name = "attempts")
public class AttemptEntity {
    @Id private String id;
    @Column(name = "user_id", nullable = false) private String userId;
    @Column(name = "case_id", nullable = false) private String caseId;
    @Column(nullable = false) private String status;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "submitted_at") private Instant submittedAt;
    private BigDecimal score;
    @Column(name = "sprint_id") private String sprintId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String document;

    protected AttemptEntity() {}
    public AttemptEntity(String id, String userId, String caseId, String status, Instant startedAt, Instant submittedAt, BigDecimal score, String sprintId, String document) {
        this.id = id; this.userId = userId; this.caseId = caseId; this.status = status; this.startedAt = startedAt; this.submittedAt = submittedAt;
        this.score = score; this.sprintId = sprintId; this.document = document;
    }
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getCaseId() { return caseId; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getSprintId() { return sprintId; }
    public String getDocument() { return document; }
}
