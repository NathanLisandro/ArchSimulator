package dev.archsim.domain.logquery;

import java.time.Duration;
import java.util.List;

public sealed interface Stage {
    record Stats(List<Aggregation> aggregations, List<String> groupBy, Duration span) implements Stage {}
    record Timechart(List<Aggregation> aggregations, List<String> groupBy, Duration span) implements Stage {}
    record Sort(List<SortKey> keys) implements Stage {}
    record Head(int limit) implements Stage {}
    record Fields(List<String> fields) implements Stage {}
    record Where(LogFilter filter) implements Stage {}
    record Dedup(List<String> fields) implements Stage {}
    record SortKey(String field, boolean descending) {}
}
