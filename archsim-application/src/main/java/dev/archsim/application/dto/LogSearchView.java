package dev.archsim.application.dto;

import dev.archsim.domain.logquery.LogQueryResult;
import dev.archsim.domain.observability.LogEntry;

import java.util.List;

public record LogSearchView(String kind, List<LogEntry> events, List<String> columns, List<List<Object>> rows,
                            long totalMatched, boolean truncated, String error) {
    public static LogSearchView from(LogQueryResult r) {
        return new LogSearchView(r.kind().name(), r.events(), r.columns(), r.rows(), r.totalMatched(), r.truncated(), null);
    }
    public static LogSearchView error(String message) {
        return new LogSearchView("ERROR", List.of(), List.of(), List.of(), 0, false, message);
    }
}
