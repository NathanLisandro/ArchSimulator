package dev.archsim.domain.observability;

import java.time.Instant;

public record FeatureFlagChange(Instant at, String flag, String from, String to, String actor, String scope) {}
