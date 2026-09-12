package dev.archsim.domain.observability;

import java.time.Instant;
import java.util.List;

public record Trace(String traceId, Instant startedAt, long durationMs, String rootService, String rootOperation, List<Span> spans) {}
