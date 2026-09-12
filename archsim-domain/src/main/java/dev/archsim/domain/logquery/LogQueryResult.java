package dev.archsim.domain.logquery;

import dev.archsim.domain.observability.LogEntry;

import java.util.List;

/** Either a list of events or a tabular/timechart result, depending on the pipeline. */
public record LogQueryResult(Kind kind, List<LogEntry> events, List<String> columns, List<List<Object>> rows, long totalMatched, boolean truncated) {
    public enum Kind { EVENTS, TABLE, TIMECHART }

    public static LogQueryResult events(List<LogEntry> events, long totalMatched, boolean truncated) {
        return new LogQueryResult(Kind.EVENTS, events, List.of("timestamp", "service", "level", "traceId", "latency", "message"), List.of(), totalMatched, truncated);
    }
    public static LogQueryResult table(List<String> columns, List<List<Object>> rows, long totalMatched) {
        return new LogQueryResult(Kind.TABLE, List.of(), columns, rows, totalMatched, false);
    }
    public static LogQueryResult timechart(List<String> columns, List<List<Object>> rows, long totalMatched) {
        return new LogQueryResult(Kind.TIMECHART, List.of(), columns, rows, totalMatched, false);
    }
}
