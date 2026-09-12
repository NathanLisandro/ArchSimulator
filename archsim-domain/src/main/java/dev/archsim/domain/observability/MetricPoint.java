package dev.archsim.domain.observability;

import java.time.Instant;

public record MetricPoint(Instant at, double value) {}
