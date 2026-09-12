package dev.archsim.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "cases")
public class CaseEntity {
    @Id private String id;
    @Column(name = "company_id", nullable = false) private String companyId;
    @Column(name = "template_id", nullable = false) private String templateId;
    @Column(name = "case_type", nullable = false) private String caseType;
    @Column(nullable = false) private String level;
    @Column(nullable = false) private long seed;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String document;

    protected CaseEntity() {}
    public CaseEntity(String id, String companyId, String templateId, String caseType, String level, long seed, Instant generatedAt, String document) {
        this.id = id; this.companyId = companyId; this.templateId = templateId; this.caseType = caseType; this.level = level;
        this.seed = seed; this.generatedAt = generatedAt; this.document = document;
    }
    public String getId() { return id; }
    public String getDocument() { return document; }
}
