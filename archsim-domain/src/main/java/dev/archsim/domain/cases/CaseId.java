package dev.archsim.domain.cases;

import java.util.UUID;

public record CaseId(String value) {
    public CaseId { if (value == null || value.isBlank()) throw new IllegalArgumentException("CaseId required"); }
    public static CaseId of(String v) { return new CaseId(v); }
    public static CaseId random() { return new CaseId(UUID.randomUUID().toString()); }
}
