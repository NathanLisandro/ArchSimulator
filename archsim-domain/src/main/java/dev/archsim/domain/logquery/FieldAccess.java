package dev.archsim.domain.logquery;

import dev.archsim.domain.observability.LogEntry;

import java.util.Locale;

/** Reads a named field from a log entry: built-ins plus anything in fields{}. */
final class FieldAccess {
    private FieldAccess() {}

    static Object get(LogEntry e, String field) {
        return switch (field.toLowerCase(Locale.ROOT)) {
            case "timestamp", "_time", "@timestamp" -> e.timestamp().toString();
            case "service" -> e.service();
            case "level", "severity" -> e.level();
            case "traceid", "trace_id" -> e.traceId();
            case "latency", "latencyms", "duration" -> e.latencyMs();
            case "message", "msg", "_raw" -> e.message();
            default -> e.fields().get(field);
        };
    }

    static String text(LogEntry e, String field) {
        Object v = get(e, field);
        return v == null ? null : String.valueOf(v);
    }

    static Double number(LogEntry e, String field) {
        Object v = get(e, field);
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return null;
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException ex) { return null; }
    }

    /** Everything searchable by a bare term. */
    static String raw(LogEntry e) {
        StringBuilder sb = new StringBuilder(e.message()).append(' ').append(e.service()).append(' ').append(e.level());
        if (e.traceId() != null) sb.append(' ').append(e.traceId());
        e.fields().forEach((k, v) -> sb.append(' ').append(k).append('=').append(v));
        return sb.toString();
    }
}
