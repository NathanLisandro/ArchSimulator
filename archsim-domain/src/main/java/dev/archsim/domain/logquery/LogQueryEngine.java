package dev.archsim.domain.logquery;

import dev.archsim.domain.observability.LogEntry;

import java.time.Instant;
import java.util.*;

/** Runs a parsed query over an in-memory list of log entries. Pure domain: no indexing, datasets are thousands of lines. */
public final class LogQueryEngine {
    public static final int MAX_EVENTS = 200;
    private static final String KEY_SEPARATOR = "|#|";

    private final LogQueryParser parser = new LogQueryParser();

    public LogQueryResult run(String query, List<LogEntry> logs) {
        return run(parser.parse(query), logs);
    }

    public LogQueryResult run(LogQuery query, List<LogEntry> logs) {
        List<LogEntry> matched = logs.stream().filter(query.filter()).sorted(Comparator.comparing(LogEntry::timestamp).reversed()).toList();
        long total = matched.size();

        List<LogEntry> events = matched;
        Table table = null;
        for (Stage stage : query.pipeline()) {
            switch (stage) {
                case Stage.Stats s -> { table = stats(events, s.aggregations(), s.groupBy()); events = List.of(); }
                case Stage.Timechart t -> { table = timechart(events, t); events = List.of(); }
                case Stage.Where w -> {
                    if (table == null) events = events.stream().filter(w.filter()).toList();
                    else table = table.filterRows(w.filter());
                }
                case Stage.Sort s -> {
                    if (table == null) events = sortEvents(events, s.keys());
                    else table = table.sort(s.keys());
                }
                case Stage.Head h -> {
                    if (table == null) events = events.stream().limit(h.limit()).toList();
                    else table = table.head(h.limit());
                }
                case Stage.Fields f -> table = (table == null ? Table.fromEvents(events, f.fields()) : table.project(f.fields()));
                case Stage.Dedup d -> {
                    if (table == null) events = dedup(events, d.fields());
                }
            }
        }

        if (table != null) {
            return table.timechart
                    ? LogQueryResult.timechart(table.columns, table.rows, total)
                    : LogQueryResult.table(table.columns, table.rows, total);
        }
        List<LogEntry> shown = events.stream().limit(MAX_EVENTS).toList();
        return LogQueryResult.events(shown, total, total > shown.size());
    }

    private static List<LogEntry> sortEvents(List<LogEntry> events, List<Stage.SortKey> keys) {
        Comparator<LogEntry> cmp = null;
        for (Stage.SortKey key : keys) {
            Comparator<LogEntry> c = Comparator.comparing(e -> sortable(FieldAccess.get(e, key.field())), LogQueryEngine::compareValues);
            if (key.descending()) c = c.reversed();
            cmp = cmp == null ? c : cmp.thenComparing(c);
        }
        return cmp == null ? events : events.stream().sorted(cmp).toList();
    }

    private static List<LogEntry> dedup(List<LogEntry> events, List<String> fields) {
        Set<String> seen = new HashSet<>();
        List<LogEntry> out = new ArrayList<>();
        for (LogEntry e : events) {
            StringJoiner key = new StringJoiner(KEY_SEPARATOR);
            fields.forEach(f -> key.add(String.valueOf(FieldAccess.get(e, f))));
            if (seen.add(key.toString())) out.add(e);
        }
        return List.copyOf(out);
    }

    private static Object sortable(Object v) { return v == null ? "" : v; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) return Double.compare(x.doubleValue(), y.doubleValue());
        if (a instanceof Comparable ca && a.getClass().isInstance(b)) return ca.compareTo(b);
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    private Table stats(List<LogEntry> events, List<Aggregation> aggregations, List<String> groupBy) {
        Map<List<String>, List<LogEntry>> groups = new LinkedHashMap<>();
        for (LogEntry e : events) {
            List<String> key = groupBy.stream().map(f -> Objects.toString(FieldAccess.get(e, f), "(null)")).toList();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        if (groups.isEmpty() && groupBy.isEmpty()) groups.put(List.of(), List.of());

        List<String> columns = new ArrayList<>(groupBy);
        aggregations.forEach(a -> columns.add(a.alias()));
        List<List<Object>> rows = new ArrayList<>();
        groups.forEach((key, entries) -> {
            List<Object> row = new ArrayList<>(key);
            aggregations.forEach(a -> row.add(apply(a, entries)));
            rows.add(List.copyOf(row));
        });
        rows.sort((a, b) -> {
            for (int i = 0; i < groupBy.size(); i++) {
                int c = compareValues(a.get(i), b.get(i));
                if (c != 0) return c;
            }
            return 0;
        });
        return new Table(List.copyOf(columns), new ArrayList<>(rows), false);
    }

    private Table timechart(List<LogEntry> events, Stage.Timechart t) {
        if (events.isEmpty()) return new Table(List.of("_time"), new ArrayList<>(), true);
        long spanSeconds = Math.max(1, t.span().getSeconds());
        Instant min = events.stream().map(LogEntry::timestamp).min(Instant::compareTo).orElseThrow();
        Instant max = events.stream().map(LogEntry::timestamp).max(Instant::compareTo).orElseThrow();

        Map<Instant, List<LogEntry>> buckets = new LinkedHashMap<>();
        for (Instant t0 = bucket(min, spanSeconds); !t0.isAfter(max); t0 = t0.plusSeconds(spanSeconds)) buckets.put(t0, new ArrayList<>());
        events.forEach(e -> buckets.get(bucket(e.timestamp(), spanSeconds)).add(e));

        String by = t.groupBy().isEmpty() ? null : t.groupBy().getFirst();
        List<String> series = by == null ? List.of()
                : events.stream().map(e -> Objects.toString(FieldAccess.get(e, by), "(null)")).distinct().sorted().toList();
        Aggregation agg = t.aggregations().getFirst();
        List<String> columns = new ArrayList<>();
        columns.add("_time");
        if (series.isEmpty()) columns.add(agg.alias()); else columns.addAll(series);

        List<List<Object>> rows = new ArrayList<>();
        for (Map.Entry<Instant, List<LogEntry>> b : buckets.entrySet()) {
            List<Object> row = new ArrayList<>();
            row.add(b.getKey().toString());
            if (series.isEmpty()) row.add(apply(agg, b.getValue()));
            else for (String s : series) {
                List<LogEntry> subset = b.getValue().stream().filter(e -> s.equals(Objects.toString(FieldAccess.get(e, by), "(null)"))).toList();
                row.add(apply(agg, subset));
            }
            rows.add(List.copyOf(row));
        }
        return new Table(List.copyOf(columns), rows, true);
    }

    private static Instant bucket(Instant t, long spanSeconds) {
        return Instant.ofEpochSecond(Math.floorDiv(t.getEpochSecond(), spanSeconds) * spanSeconds);
    }

    private static Object apply(Aggregation a, List<LogEntry> entries) {
        if (a.function().equals("count")) return (long) entries.size();
        String field = a.field();
        if (field == null) throw new LogQueryException(a.function() + " precisa de um campo, ex.: " + a.function() + "(latency)");
        if (a.function().equals("dc") || a.function().equals("distinct_count")) {
            return entries.stream().map(e -> FieldAccess.text(e, field)).filter(Objects::nonNull).distinct().count();
        }
        List<Double> values = entries.stream().map(e -> FieldAccess.number(e, field)).filter(Objects::nonNull).sorted().toList();
        if (values.isEmpty()) return 0.0;
        return switch (a.function()) {
            case "sum" -> round(values.stream().mapToDouble(Double::doubleValue).sum());
            case "avg" -> round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            case "min" -> round(values.getFirst());
            case "max" -> round(values.getLast());
            case "p50" -> round(percentile(values, 50));
            case "p90" -> round(percentile(values, 90));
            case "p95" -> round(percentile(values, 95));
            case "p99" -> round(percentile(values, 99));
            default -> throw new LogQueryException("Funcao desconhecida: " + a.function());
        };
    }

    private static double percentile(List<Double> sorted, int p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    /** Tabular intermediate result inside the pipeline. */
    private static final class Table {
        final List<String> columns;
        final List<List<Object>> rows;
        final boolean timechart;

        Table(List<String> columns, List<List<Object>> rows, boolean timechart) {
            this.columns = columns; this.rows = rows; this.timechart = timechart;
        }

        static Table fromEvents(List<LogEntry> events, List<String> fields) {
            List<List<Object>> rows = events.stream().map(e -> fields.stream().map(f -> (Object) FieldAccess.get(e, f)).toList()).toList();
            return new Table(List.copyOf(fields), new ArrayList<>(rows), false);
        }

        Table project(List<String> fields) {
            List<Integer> indexes = fields.stream().map(columns::indexOf).toList();
            if (indexes.contains(-1)) throw new LogQueryException("Campo desconhecido em fields: " + fields.get(indexes.indexOf(-1)));
            List<List<Object>> out = rows.stream().map(r -> indexes.stream().map(r::get).toList()).toList();
            return new Table(List.copyOf(fields), new ArrayList<>(out), timechart);
        }

        Table head(int limit) { return new Table(columns, new ArrayList<>(rows.stream().limit(limit).toList()), timechart); }

        Table sort(List<Stage.SortKey> keys) {
            List<List<Object>> out = new ArrayList<>(rows);
            Comparator<List<Object>> cmp = null;
            for (Stage.SortKey key : keys) {
                int idx = columns.indexOf(key.field());
                if (idx < 0) throw new LogQueryException("Campo desconhecido em sort: " + key.field());
                Comparator<List<Object>> c = (a, b) -> compareValues(a.get(idx), b.get(idx));
                if (key.descending()) c = c.reversed();
                cmp = cmp == null ? c : cmp.thenComparing(c);
            }
            out.sort(cmp);
            return new Table(columns, out, timechart);
        }

        /** Applies a comparison filter to aggregated rows (| where count &gt; 100). */
        Table filterRows(LogFilter filter) {
            if (!(filter instanceof LogFilter.Comparison c))
                throw new LogQueryException("Depois de stats, 'where' aceita apenas comparacoes simples (ex.: count > 100)");
            int idx = columns.indexOf(c.field());
            if (idx < 0) throw new LogQueryException("Campo desconhecido em where: " + c.field());
            List<List<Object>> out = rows.stream().filter(r -> matches(r.get(idx), c)).toList();
            return new Table(columns, new ArrayList<>(out), timechart);
        }

        private static boolean matches(Object actual, LogFilter.Comparison c) {
            if (actual instanceof Number n) {
                double expected;
                try { expected = Double.parseDouble(c.value()); } catch (NumberFormatException e) { return false; }
                return switch (c.op()) {
                    case GT -> n.doubleValue() > expected;
                    case GTE -> n.doubleValue() >= expected;
                    case LT -> n.doubleValue() < expected;
                    case LTE -> n.doubleValue() <= expected;
                    case EQ -> n.doubleValue() == expected;
                    case NE -> n.doubleValue() != expected;
                };
            }
            boolean eq = String.valueOf(actual).equalsIgnoreCase(c.value());
            return c.op() == LogFilter.Comparison.Op.EQ ? eq : !eq;
        }
    }
}
