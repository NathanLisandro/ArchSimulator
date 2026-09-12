package dev.archsim.domain.observability;

import java.time.Instant;

public record DeployEvent(Instant at, String service, String version, String author, String description, boolean rolledBack) {}
