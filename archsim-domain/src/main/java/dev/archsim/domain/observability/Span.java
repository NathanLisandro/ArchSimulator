package dev.archsim.domain.observability;

import java.util.Map;

public record Span(String spanId, String parentSpanId, String service, String operation, long startOffsetMs, long durationMs, Map<String, String> tags) {}
