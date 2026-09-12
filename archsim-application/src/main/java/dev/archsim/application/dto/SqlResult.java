package dev.archsim.application.dto;

import java.util.List;

public record SqlResult(List<String> columns, List<List<Object>> rows, String planText, String error, long elapsedMs, boolean truncated) {
    public static SqlResult error(String message) { return new SqlResult(List.of(), List.of(), null, message, 0, false); }
    public static SqlResult plan(String text) { return new SqlResult(List.of("QUERY PLAN"), List.of(), text, null, 0, false); }
}
