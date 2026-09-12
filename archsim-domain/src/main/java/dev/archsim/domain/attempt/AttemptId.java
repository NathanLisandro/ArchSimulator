package dev.archsim.domain.attempt;

import java.util.UUID;

public record AttemptId(String value) {
    public AttemptId { if (value == null || value.isBlank()) throw new IllegalArgumentException("AttemptId required"); }
    public static AttemptId of(String v) { return new AttemptId(v); }
    public static AttemptId random() { return new AttemptId(UUID.randomUUID().toString()); }
}
