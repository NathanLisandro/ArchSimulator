package dev.archsim.domain.observability;

import java.time.Instant;
import java.util.Map;

public record LogEntry(Instant timestamp, String service, String level, String traceId, Long latencyMs, String message, Map<String, String> fields) {}
