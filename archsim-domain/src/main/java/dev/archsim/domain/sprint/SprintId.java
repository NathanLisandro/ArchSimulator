package dev.archsim.domain.sprint;

import java.util.UUID;

public record SprintId(String value) {
    public SprintId { if (value == null || value.isBlank()) throw new IllegalArgumentException("SprintId required"); }
    public static SprintId of(String v) { return new SprintId(v); }
    public static SprintId random() { return new SprintId(UUID.randomUUID().toString()); }
}
