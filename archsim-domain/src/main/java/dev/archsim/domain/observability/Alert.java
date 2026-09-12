package dev.archsim.domain.observability;

import java.time.Instant;

public record Alert(Instant firedAt, String name, String service, String severity, String description, Instant resolvedAt) {}
