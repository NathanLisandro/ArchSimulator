package dev.archsim.application.dto;

import dev.archsim.domain.observability.MetricSeries;

import java.time.Instant;
import java.util.List;

public record MetricsView(Instant from, Instant to, List<String> services, List<MetricSeries> series) {}
